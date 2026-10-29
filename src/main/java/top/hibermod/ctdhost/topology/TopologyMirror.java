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
        int nodes = 0;
        int edges = 0;
        for (TrackGraph g : graphs.values()) {
            nodes += g.getNodes().size();
            // 连接数不好直接取，用节点集合算个上界估计即可——只用于「变没变」的判断
            edges += g.getNodes().size();
        }
        if (nodes == lastNodeCount && edges == lastEdgeCount && view != null) {
            return view;
        }
        lastNodeCount = nodes;
        lastEdgeCount = edges;
        return rebuild(graphs);
    }

    /** 无条件重建。 */
    public synchronized TopologyView rebuild(Map<UUID, TrackGraph> graphs) {
        TopologyView.Builder b = TopologyView.builder().version(++version);

        Set<Rail.EdgeId> seenEdges = new HashSet<>();
        int nodeCount = 0;
        int edgeCount = 0;

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
            } catch (Throwable ignored) {
                // 某个图读不出来不该让整张拓扑失败
            }
        }
        lastNodeCount = nodeCount;
        lastEdgeCount = edgeCount;
        this.view = b.build();
        return this.view;
    }

    /** 上一次构建的节点/边数量，用于日志与校验。 */
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