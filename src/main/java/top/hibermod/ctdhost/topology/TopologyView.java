package top.hibermod.ctdhost.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdapi.device.Devices;
import top.hibermod.ctdapi.signal.Aspect;
import top.hibermod.ctdapi.signal.SignalRegistries;
import top.hibermod.ctdapi.signal.SignalSystem;
import top.hibermod.ctdapi.signal.SignalType;
import top.hibermod.ctdapi.timetable.Service;

/**
 * {@link Topology} 的**不可变快照**实现。
 *
 * <p>刻意做成不可变：内核在一帧内可能反复查询拓扑，如果它中途被宿主改写，
 * 内核算出来的东西就开始自相矛盾。改动一律产生新快照（{@link #builder()}）。
 *
 * <p>查询用哈希索引而不是线性扫描——线网规模上万条边时，
 * `edgesFrom` 被调用得比想象中频繁。
 */
public final class TopologyView implements Topology {

    private final long version;
    private final double maxTrainLength;

    private final Map<Rail.NodeId, Node> nodes;
    private final Map<Rail.EdgeId, Edge> edges;
    private final Map<Rail.NodeId, List<Edge>> outgoing;

    private final List<SignalSite> signals;
    private final List<SwitchSite> switches;
    private final List<StationSite> stations;
    private final List<Section> sections;
    private final Map<Rail.EdgeId, List<Section>> sectionsByEdge;

    private final List<Devices.DeviceSite> devices;
    private final Map<ResourceLocation, List<Devices.DeviceSite>> devicesByType;
    private final Map<java.util.UUID, List<Devices.DeviceSite>> devicesByTrain;

    private TopologyView(Builder b) {
        this.version = b.version;
        this.maxTrainLength = b.maxTrainLength;
        this.nodes = Map.copyOf(b.nodes);
        this.edges = Map.copyOf(b.edges);
        this.signals = List.copyOf(b.signals);
        this.switches = List.copyOf(b.switches);
        this.stations = List.copyOf(b.stations);
        this.sections = List.copyOf(b.sections);
        this.devices = List.copyOf(b.devices);

        Map<Rail.NodeId, List<Edge>> out = new HashMap<>();
        for (Edge e : b.edges.values()) {
            out.computeIfAbsent(e.id().first(), k -> new ArrayList<>()).add(e);
            out.computeIfAbsent(e.id().second(), k -> new ArrayList<>()).add(e);
        }
        out.replaceAll((k, v) -> List.copyOf(v));
        this.outgoing = Map.copyOf(out);

        Map<Rail.EdgeId, List<Section>> byEdge = new HashMap<>();
        for (Section s : b.sections) {
            // Section 持有的是 spans，不是边列表；从 span 反查所属边
            for (Rail.Span span : s.spans()) {
                byEdge.computeIfAbsent(span.edge(), k -> new ArrayList<>()).add(s);
            }
        }
        byEdge.replaceAll((k, v) -> List.copyOf(v));
        this.sectionsByEdge = Map.copyOf(byEdge);

        Map<ResourceLocation, List<Devices.DeviceSite>> byType = new HashMap<>();
        Map<java.util.UUID, List<Devices.DeviceSite>> byTrain = new HashMap<>();
        for (Devices.DeviceSite d : b.devices) {
            byType.computeIfAbsent(d.typeId(), k -> new ArrayList<>()).add(d);
            if (d.trainId() != null) {
                byTrain.computeIfAbsent(d.trainId(), k -> new ArrayList<>()).add(d);
            }
        }
        byType.replaceAll((k, v) -> List.copyOf(v));
        byTrain.replaceAll((k, v) -> List.copyOf(v));
        this.devicesByType = Map.copyOf(byType);
        this.devicesByTrain = Map.copyOf(byTrain);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── 图 ────────────────────────────────────────────────────────────────

    @Override public long version() { return version; }
    @Override public Collection<Node> nodes() { return nodes.values(); }
    @Override public Node node(Rail.NodeId id) { return nodes.get(id); }
    @Override public Collection<Edge> edges() { return edges.values(); }
    @Override public Edge edge(Rail.EdgeId id) { return edges.get(id); }

    @Override public Collection<Edge> edgesFrom(Rail.NodeId id) {
        List<Edge> list = outgoing.get(id);
        return list == null ? List.of() : list;
    }

    // ── 设施 ──────────────────────────────────────────────────────────────

    @Override public Collection<SignalSite> signals() { return signals; }
    @Override public Collection<SwitchSite> switches() { return switches; }
    @Override public Collection<StationSite> stations() { return stations; }
    @Override public Collection<Section> sections() { return sections; }

    @Override public Collection<Section> sectionsOf(Rail.EdgeId edge) {
        List<Section> list = sectionsByEdge.get(edge);
        return list == null ? List.of() : list;
    }

    @Override public double maxTrainLength() { return maxTrainLength; }

    // ── 信号注册表（委托给全局注册表，不进快照）──────────────────────────

    @Override public Collection<SignalSystem> signalSystems() { return SignalRegistries.systems(); }
    @Override public SignalSystem signalSystem(ResourceLocation id) { return SignalRegistries.system(id).orElse(null); }
    @Override public Collection<SignalType> signalTypes() { return SignalRegistries.types(); }
    @Override public SignalType signalType(ResourceLocation id) { return SignalRegistries.type(id).orElse(null); }
    @Override public Collection<Aspect> aspects() { return SignalRegistries.aspects(); }
    @Override public Aspect aspect(ResourceLocation id) { return SignalRegistries.aspect(id).orElse(null); }

    // ── 设备与交路 ────────────────────────────────────────────────────────

    @Override public Collection<Devices.DeviceSite> devices() { return devices; }

    @Override public Collection<Devices.DeviceSite> devicesOf(ResourceLocation typeId) {
        List<Devices.DeviceSite> list = devicesByType.get(typeId);
        return list == null ? List.of() : list;
    }

    @Override public Collection<Devices.DeviceSite> devicesOfTrain(java.util.UUID trainId) {
        List<Devices.DeviceSite> list = devicesByTrain.get(trainId);
        return list == null ? List.of() : list;
    }

    @Override public Collection<Service> services() { return Service.services(); }
    @Override public Service service(ResourceLocation id) { return Service.service(id).orElse(null); }

    // ── 构造器 ────────────────────────────────────────────────────────────

    /** 逐项填充，末尾 {@link #build()} 冻结成快照。 */
    public static final class Builder {
        private long version;
        private double maxTrainLength = 64.0;
        private final Map<Rail.NodeId, Node> nodes = new HashMap<>();
        private final Map<Rail.EdgeId, Edge> edges = new HashMap<>();
        private final List<SignalSite> signals = new ArrayList<>();
        private final List<SwitchSite> switches = new ArrayList<>();
        private final List<StationSite> stations = new ArrayList<>();
        private final List<Section> sections = new ArrayList<>();
        private final List<Devices.DeviceSite> devices = new ArrayList<>();

        public Builder version(long v) { this.version = v; return this; }
        public Builder maxTrainLength(double v) { this.maxTrainLength = v; return this; }
        public Builder node(Node n) { nodes.put(n.id(), n); return this; }
        public Builder edge(Edge e) { edges.put(e.id(), e); return this; }
        public Builder signal(SignalSite s) { signals.add(s); return this; }
        public Builder switchSite(SwitchSite s) { switches.add(s); return this; }
        public Builder station(StationSite s) { stations.add(s); return this; }
        public Builder section(Section s) { sections.add(s); return this; }
        public Builder device(Devices.DeviceSite d) { devices.add(d); return this; }

        public int nodeCount() { return nodes.size(); }
        public int edgeCount() { return edges.size(); }

        public TopologyView build() {
            return new TopologyView(this);
        }
    }
}
