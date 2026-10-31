package top.hibermod.ctdhost.topology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;

import top.hibermod.ctdapi.Rail;

/**
 * 把 Create 的 {@code TrackGraph} 镜像成 {@code Topology} 快照。
 *
 * <p>当前实现是**全量重建**。做成增量需要挂 {@code TrackGraph#addNode/connectNodes}
 * 的注入（H-4），等宿主能在游戏里跑起来之后再做——过早优化会在还没验证正确性时
 * 引入「镜像与实物不一致」这种最难查的 bug。
 *
 * <p>全量重建的代价必须盯着：一个上万条边的线网，每次重建要遍历所有节点的所有连接。
 * 所以**只在拓扑变化时重建**，不是每帧。
 */
public final class TopologyMirror {

    private volatile TopologyView view;
    private long version;

    /** 上次重建时的图规模，用于判断「有没有变」。 */
    private int lastNodeCount = -1;
    private int lastEdgeCount = -1;
    private long lastSignature = Long.MIN_VALUE;
    private int ticksSinceCheck;

    /** 多少次 tick 检查一次变更（10 刻 = 0.5 秒）。 */
    private static final int CHECK_INTERVAL = 10;

    private int rebuildCount;
    private long lastRebuildMillis;

    public TopologyView current() {
        return view;
    }

    /**
     * 若图规模与上次不同则重建。
     *
     * <p>规模不变但内容变了（比如换了一条轨的材质）不会被发现。这在当前阶段可以接受：
     * 真正影响行车的是连通性与道岔，它们必然改变边数或节点数。
     * 后续接入 H-4 的增量通知后，这里会换成版本号比较。
     */
    public synchronized TopologyView refreshIfChanged(Map<UUID, TrackGraph> graphs) {
        // 每 CHECK_INTERVAL 刻查一次「变没变」，不是每帧。
        //
        // 拓扑变更（铺轨、拆轨、加装信号机）是**分钟级**的事件，而重建是 O(节点数)。
        // 每帧重建在 100 节点时看不出代价，到上千节点就是每帧几毫秒——纯粹浪费。
        if (view != null && ++ticksSinceCheck < CHECK_INTERVAL) {
            return view;
        }
        ticksSinceCheck = 0;

        long sig = signature(graphs);
        if (view != null && sig == lastSignature) {
            return view;                      // 规模与边点都没变：沿用
        }
        lastSignature = sig;
        return rebuild(graphs);
    }

    /**
     * 「变没变」的廉价签名。
     *
     * <p>旧实现把 `edges` 直接写成节点数——那等于只比较节点数，
     * 于是**只加一条边**（比如把两段轨接上）或**只加一架信号机**都不会触发重建，
     * 内核看到的是过期拓扑，而且没有任何提示。
     *
     * <p>现在把连接数与信号机数一起算进去：同样是 O(节点数)，但不会漏。
     */
    private static long signature(Map<UUID, TrackGraph> graphs) {
        long h = 1125899906842597L;
        for (TrackGraph g : graphs.values()) {
            h = h * 31 + g.id.hashCode();
            h = h * 31 + g.getNodes().size();
            int connections = 0;
            for (TrackNodeLocation loc : g.getNodes()) {
                TrackNode node = g.locateNode(loc);
                if (node == null) continue;
                try {
                    var c = g.getConnectionsFrom(node);
                    if (c != null) connections += c.size();
                } catch (RuntimeException t) {
                top.hibermod.ctdhost.Failures.caught("拓扑签名（单节点）", t);
                    // 单个节点读不出来不影响「整体有没有变」的判断
                }
            }
            h = h * 31 + connections;
            try {
                h = h * 31 + g.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.SIGNAL).size();
            } catch (Throwable t) {
            top.hibermod.ctdhost.Failures.caught("拓扑重建（单点）", t);
                // 老版本没有 getPoints：退化到「节点+连接」
            }
        }
        return h;
    }

    /** 无条件重建。 */
    public synchronized TopologyView rebuild(Map<UUID, TrackGraph> graphs) {
        long started = System.nanoTime();
        TopologyView.Builder b = TopologyView.builder().version(++version);

        Set<Rail.EdgeId> seenEdges = new HashSet<>();
        int nodeCount = 0;
        int edgeCount = 0;

        // 岔口候选：Create 没有道岔实体，岔口就是「连接数 ≥ 3 的节点」。
        // 之前 Topology.switches() 恒为空——契约在、没人填，内核看不到任何岔口。
        java.util.List<top.hibermod.ctdapi.Topology.SwitchSite> junctions = new java.util.ArrayList<>();

        for (TrackGraph graph : graphs.values()) {
            for (TrackNodeLocation loc : graph.getNodes()) {
                TrackNode node = graph.locateNode(loc);
                if (node == null) continue;

                Rail.NodeId id = StableIds.node(node);
                Map<TrackNode, TrackEdge> connections;
                try {
                    connections = graph.getConnectionsFrom(node);
                } catch (RuntimeException e) {
                    continue;                       // 单点异常不该拖垮整次重建
                }
                if (connections == null) connections = Map.of();

                b.node(new top.hibermod.ctdapi.Topology.Node(
                    id, connections.size(), connections.size() > 2));
                nodeCount++;

                // 度数 ≥3 的节点就是岔口。股道 = 从它出发的各条边。
                if (connections.size() >= 3) {
                    try {
                        java.util.List<Rail.EdgeId> branches = new java.util.ArrayList<>();
                        for (TrackNode other : connections.keySet()) {
                            branches.add(StableIds.edge(node, other));
                        }
                        junctions.add(new top.hibermod.ctdapi.Topology.SwitchSite(
                            id,
                            top.hibermod.ctdapi.Topology.SwitchPosition.UNKNOWN,
                            java.util.List.copyOf(branches),
                            -1,
                            null,
                            true,
                            false));
                    } catch (Throwable t) {
                        top.hibermod.ctdhost.Failures.caught("拓扑重建（岔口）", t);
                    }
                }

                for (Map.Entry<TrackNode, TrackEdge> e : connections.entrySet()) {
                    Rail.EdgeId edgeId = StableIds.edge(node, e.getKey());
                    if (!seenEdges.add(edgeId)) continue;       // 每条边会从两端各看到一次

                    TrackEdge edge = e.getValue();
                    b.edge(new top.hibermod.ctdapi.Topology.Edge(
                        edgeId,
                        edge.getLength(),
                        edge.isInterDimensional(),
                        edge.isTurn(),
                        edge.getTrackMaterial() == null ? "create:standard"
                            : edge.getTrackMaterial().id.toString(),
                        // 汽鸣铁道把「股道被扳开」记在 EdgeData 上（mixin 实现
                        // ISwitchDisabledEdge#isEnabled）。EdgeData 没有字段可读，所以按接口反射。
                        // 拿不到就当作可通行——「不知道」不能等于「禁用」。
                        top.hibermod.ctdhost.execute.SwitchApplier.isEdgeEnabled(edge)));
                    edgeCount++;
                }
            }
        }

        // 信号机：从图的边点里取 SignalBoundary。
        // 不读它的话 ctx.signals() 永远是空的——内核想控灯也无从控起。
        for (var graph : graphs.values()) {
            try {
                for (com.simibubi.create.content.trains.signal.SignalBoundary boundary
                        : graph.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.SIGNAL)) {
                    Rail.EdgeId edgeId = edgeIdOf(boundary);
                    if (edgeId == null) continue;

                    b.signal(new top.hibermod.ctdapi.Topology.SignalSite(
                        boundary.id,
                        edgeId,
                        boundary.position,
                        true,
                        true,
                        false,
                        null,
                        ""));
                }
            } catch (Throwable t) {
            top.hibermod.ctdhost.Failures.caught("拓扑重建（单点）", t);
                // 某个图读不出来不该让整张拓扑失败
            }
        }
        // 车站：GlobalStation 是 Create 的边点（SingleBlockEntityEdgePoint），
        // 自带 edgeLocation 与 position，能直接转成 StationSite。
        //
        // 之前 ctx.topology().stations() 恒为空——契约在、数据没接，
        // 内核想按站排进路只能自己再扫一遍 Create 的表。
        int stationCount = 0;
        int stationSeen = 0;

        for (var graph : graphs.values()) {
            try {
                for (com.simibubi.create.content.trains.station.GlobalStation station
                        : graph.getPoints(com.simibubi.create.content.trains.graph.EdgePointType.STATION)) {
                    stationSeen++;

                    Rail.EdgeId edgeId = edgeIdOf(station);
                    if (edgeId == null) continue;

                    b.station(new top.hibermod.ctdapi.Topology.StationSite(
                        station.id,
                        net.minecraft.network.chat.Component.literal(
                            station.name == null ? "" : station.name),
                        edgeId,
                        station.position,
                        station.assembling,
                        java.util.List.of()));

                    // 车站同时也是「地面设备」：站台门 / 发车指示器将来都挂在它上面。
                    // 先给一个通用类型，K4 的细分以后再加。
                    b.device(new top.hibermod.ctdapi.device.Devices.DeviceSite(
                        station.id,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ctd", "station_platform"),
                        null,
                        edgeId,
                        station.position,
                        true,
                        new net.minecraft.nbt.CompoundTag()));

                    stationCount++;
                }
            } catch (Throwable t) {
                top.hibermod.ctdhost.Failures.caught("拓扑重建（车站）", t);
            }
        }

        for (var sw : junctions) {
            b.switchSite(sw);
        }
        lastSwitchCount = junctions.size();

        lastStationCount = stationCount;
        lastStationSeen = stationSeen;

        lastNodeCount = nodeCount;
        lastEdgeCount = edgeCount;
        this.view = b.build();
        rebuildCount++;
        lastRebuildMillis = (System.nanoTime() - started) / 1_000_000L;
        return this.view;
    }

    /** 上一次构建的节点/边数量，用于日志与校验。 */
    /** 自启动以来的重建次数。稳态下应当很少——每帧都涨说明签名没起作用。 */
    public int rebuildCount() { return rebuildCount; }

    /** 上一次重建收录的车站数。 */
    public int lastStationCount() { return lastStationCount; }

    private int lastStationCount;

    /** 上一次重建看到的车站总数（含未能映射到边的）。 */
    public int lastStationSeen() { return lastStationSeen; }

    private int lastStationSeen;

    /** 上一次重建收录的岔口数（度数 ≥3 的节点）。 */
    public int lastSwitchCount() { return lastSwitchCount; }

    private int lastSwitchCount;

    /** 上一次重建耗时（毫秒）。 */
    public long lastRebuildMillis() { return lastRebuildMillis; }

    public int lastNodeCount() { return lastNodeCount; }

    public int lastEdgeCount() { return lastEdgeCount; }

    /** 供测试：直接设置一个快照。 */
    public void install(TopologyView v) {
        this.view = v;
        this.version = v.version();
    }

    public long version() {
        return version;
    }

    static Map<String, Object> debugStats(TopologyMirror m) {
        Map<String, Object> out = new HashMap<>();
        out.put("version", m.version);
        out.put("nodes", m.lastNodeCount);
        out.put("edges", m.lastEdgeCount);
        return out;
    }
    /**
     * 取信号机所在边。
     *
     * <p>用反射读 {@code edgeLocation}：它的类型是 catnip 的 {@code Couple}，
     * 在只有 Create jar 的编译环境里读它会报「无法解引用」。
     * 为一个字段把 catnip 拖进编译依赖不值得，反射只在重建拓扑时跑一次。
     */
    private static Rail.EdgeId edgeIdOf(
            com.simibubi.create.content.trains.signal.TrackEdgePoint point) {
        try {
            Object couple = com.simibubi.create.content.trains.signal.TrackEdgePoint.class
                .getField("edgeLocation").get(point);
            if (couple == null) return null;

            Object first = couple.getClass().getMethod("getFirst").invoke(couple);
            Object second = couple.getClass().getMethod("getSecond").invoke(couple);
            if (first == null || second == null) return null;

            return Rail.EdgeId.of(
                StableIds.node((com.simibubi.create.content.trains.graph.TrackNodeLocation) first),
                StableIds.node((com.simibubi.create.content.trains.graph.TrackNodeLocation) second));
        } catch (Throwable t) {
            return null;
        }
    }
}
