package top.hibermod.ctdhost.execute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.Topology;

/**
 * 进路申请 → 已锁资源 + 剩余长度。
 *
 * <h2>为什么需要它</h2>
 * 授权只回答「还能走多远」，进路回答「走哪股」。没有进路，分叉口该走哪条
 * 只能靠 Create 自己的寻路；同一个岔口上两列车会各算各的。
 *
 * <h2>锁什么</h2>
 * 进路给出的 {@code path} 是一串边。这些边的**中间节点**就是需要锁的岔口：
 * 一条边连接两节点，连续两条边共用的那个节点就是分歧点。
 * 起终点不算——它们是进路的边界，不是分歧。
 *
 * <h2>区段锁为什么暂时为空</h2>
 * 区段（section）需要「闭塞分区」这一层模型，目前拓扑里只有节点和边。
 * 返回空列表而不是拿边冒充区段：让读的人知道这层还没建，
 * 而不是拿到一份看起来像区段、实际是边的东西。
 */
public final class RouteApplier {

    private final Map<UUID, Telemetry.RouteState> routes = new HashMap<>();
    private final Map<UUID, Command.RouteRequest> requests = new HashMap<>();
    private long nextRouteId = 1;

    /**
     * 应用本帧的进路申请。
     *
     * @return 当前持有进路的列车数
     */
    public int apply(Map<UUID, Command.RouteRequest> incoming, Topology view) {
        if (incoming == null) incoming = Map.of();

        // 撤销：本帧没有再申请的列车，进路作废
        var it = routes.keySet().iterator();
        while (it.hasNext()) {
            UUID trainId = it.next();
            if (!incoming.containsKey(trainId)) {
                requests.remove(trainId);
                it.remove();
            }
        }

        for (var entry : incoming.entrySet()) {
            UUID trainId = entry.getKey();
            Command.RouteRequest request = entry.getValue();

            // 同一份申请不重复分配 id：id 变化会让下游以为换了进路
            Command.RouteRequest previous = requests.get(trainId);
            if (previous != null && previous.equals(request)) continue;

            requests.put(trainId, request);
            routes.put(trainId, build(request, view));
        }

        return routes.size();
    }

    private Telemetry.RouteState build(Command.RouteRequest request, Topology view) {
        List<Rail.EdgeId> path = request.path() == null ? List.of() : request.path();

        double remaining = 0.0;
        if (view != null) {
            Map<Rail.EdgeId, Double> lengths = new HashMap<>();
            for (Topology.Edge edge : view.edges()) {
                lengths.put(edge.id(), edge.length());
            }
            for (Rail.EdgeId edgeId : path) {
                Double len = lengths.get(edgeId);
                if (len != null) remaining += len;
            }
        }

        // 岔口 = 连续两条边共用的节点
        Set<Rail.NodeId> switches = new HashSet<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            Rail.NodeId shared = sharedNode(path.get(i), path.get(i + 1));
            if (shared != null) switches.add(shared);
        }

        return new Telemetry.RouteState(
            new Telemetry.RouteId(nextRouteId++),
            remaining,
            List.of(),                                  // 区段模型未建，见类注释
            new ArrayList<>(switches),
            false);                                     // committed：列车进入首区段后才是 true
    }

    private static Rail.NodeId sharedNode(Rail.EdgeId a, Rail.EdgeId b) {
        if (a.first().equals(b.first()) || a.first().equals(b.second())) return a.first();
        if (a.second().equals(b.first()) || a.second().equals(b.second())) return a.second();
        return null;
    }

    /** 某列车当前的进路；没有返回 {@code null}。 */
    public Telemetry.RouteState stateOf(UUID trainId) {
        return trainId == null ? null : routes.get(trainId);
    }

    public int size() {
        return routes.size();
    }
}
