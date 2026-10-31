package top.hibermod.ctdhost.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /ctd host debugjunction} —— 从一个节点**分叉**出一条支线，造出一个真正的岔口。
 *
 * <h2>为什么需要它</h2>
 * `/ctd host debugtrack` 只铺直线，所以测试世界里**从来没有岔口**。
 * 于是 {@code /ctd host switches} 的「有锁时逐条列出」那一支永远验不到，
 * 道岔逻辑锁也只在离线测试里被覆盖过。
 *
 * <p>没有岔口就没法验证岔口——这条命令补的就是这个缺口。
 *
 * <h2>它改世界</h2>
 * 与 {@code debugtrack} 一样会永久改变轨道图，**没有撤销**。
 * 只在开发世界里用；这一点写在命令输出里。
 *
 * <h2>怎么造</h2>
 * 取离列车最近的叶子，沿链条往回退 {@value #BACK_OFF} 个节点作为岔心，
 * 从那里朝**垂直方向**铺 {@value #BRANCH_NODES} 个节点。
 * 岔心因此获得第三条连接，成为度数 3 的岔口节点。
 */
public final class DebugJunctionCommand {

    private DebugJunctionCommand() {}

    /** 从叶子往回退几个节点当岔心（太靠近端头会让支线贴着主线）。 */
    private static final int BACK_OFF = 6;

    /** 支线长度（节点数）。 */
    private static final int BRANCH_NODES = 10;

    /** 节点间距（与 debugtrack 的 SEGMENT_LENGTH 一致）。 */
    private static final double SEGMENT_LENGTH = 4.0;

    /** 支线第一个节点的坐标；{@code layBranch} 填，用于算 Rail.EdgeId。 */
    private static Vec3 LAST_BRANCH_START;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debugjunction").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        ServerLevel level = src.getLevel();

        TrackGraph graph = largestGraph();
        if (graph == null) {
            src.sendFailure(Component.literal("没有轨道图；先跑 /ctd host debugtrack"));
            return 0;
        }

        TrackNode forkNode = pickForkNode(graph);
        if (forkNode == null) {
            src.sendFailure(Component.literal("这条链太短，找不到合适的岔心"));
            return 0;
        }

        Vec3 forkPos = forkNode.getLocation().getLocation();

        // 支线方向：取主线方向绕 Y 轴转 60°，避免支线贴回主线
        Vec3 main = mainDirection(graph, forkNode);
        if (main == null) {
            src.sendFailure(Component.literal("岔心没有邻居，无法确定主线方向"));
            return 0;
        }

        Vec3 branch = rotateY(main, Math.toRadians(60));
        int added = layBranch(level, graph, forkNode, forkPos, branch);

        final int degree = degreeOf(graph, forkNode);

        // 打印 Rail 编码的 id：内核脚本要锁岔口时用的就是这个字符串。
        // 只给坐标不够——脚本拿不到坐标，只能拿 id。
        String nodeId;
        try {
            nodeId = new top.hibermod.ctdapi.Rail.NodeId(
                level.dimension(),
                (int) Math.round(forkPos.x * 2),
                (int) Math.round(forkPos.y * 2),
                (int) Math.round(forkPos.z * 2),
                0).encode();
        } catch (Throwable t) {
            top.hibermod.ctdhost.Failures.caught("debugjunction 编码 node id", t);
            nodeId = "(编码失败)";
        }

        final String railId = nodeId;

        // 支线第一条边的 EdgeId：脚本 lock_switch(node, branch) 里的 branch 就是这个。
        // 没有它，脚本只能锁一个「没有股道」的岔口——而 FrameOutput 会直接丢掉这种指令。
        String branchEdgeId;
        try {
            Vec3 b = LAST_BRANCH_START;
            if (b == null) {
                branchEdgeId = "(支线没铺成)";
            } else {
                var first = new top.hibermod.ctdapi.Rail.NodeId(
                    level.dimension(),
                    (int) Math.round(forkPos.x * 2),
                    (int) Math.round(forkPos.y * 2),
                    (int) Math.round(forkPos.z * 2),
                    0);
                var second = new top.hibermod.ctdapi.Rail.NodeId(
                    level.dimension(),
                    (int) Math.round(b.x * 2),
                    (int) Math.round(b.y * 2),
                    (int) Math.round(b.z * 2),
                    0);
                branchEdgeId = new top.hibermod.ctdapi.Rail.EdgeId(first, second).encode();
            }
        } catch (Throwable t) {
            top.hibermod.ctdhost.Failures.caught("debugjunction 编码 branch edge", t);
            branchEdgeId = "(编码失败)";
        }

        final String branchId = branchEdgeId;
        final String line = "junction: node=" + forkNode.getLocation()
            + " 支点=" + posOf(forkNode.getLocation())
            + " 支线新增=" + added + " 节点"
            + " 现在度数=" + degree;

        src.sendSuccess(() -> Component.literal(line), false);
        src.sendSuccess(() -> Component.literal("  Rail.NodeId = " + railId), false);
        src.sendSuccess(() -> Component.literal("  Rail.EdgeId(branch) = " + branchId), false);
        src.sendSuccess(() -> Component.literal(
            "注意：这会**永久改变轨道图**，没有撤销。看 /ctd host switches 验证岔口。"), false);

        return added;
    }

    /** 任意一列车的世界坐标，用作「离哪儿近」的参照；没有列车时返回 null。 */
    private static Vec3 nearestTrain() {
        for (var train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty()) continue;
            var entity = train.carriages.get(0).anyAvailableEntity();
            if (entity != null) return entity.position();
        }
        return null;
    }

    private static TrackGraph largestGraph() {
        TrackGraph best = null;
        int bestNodes = 0;
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            int nodes = graph.getNodes().size();
            if (nodes > bestNodes) {
                bestNodes = nodes;
                best = graph;
            }
        }
        return best;
    }

    /** 离列车最近的叶子往回退 BACK_OFF 个节点。 */
    private static TrackNode pickForkNode(TrackGraph graph) {
        TrackNode leaf = null;
        double bestDistance = Double.MAX_VALUE;

        Vec3 focus = nearestTrain();

        for (var loc : graph.getNodes()) {
            TrackNode node = graph.locateNode(loc);
            if (node == null) continue;

            var conns = graph.getConnectionsFrom(node);
            if (conns == null || conns.size() != 1) continue;

            double d = focus == null ? 0.0 : node.getLocation().getLocation().distanceToSqr(focus);
            if (d < bestDistance) {
                bestDistance = d;
                leaf = node;
            }
        }

        if (leaf == null) return null;

        TrackNode current = leaf;
        for (int i = 0; i < BACK_OFF; i++) {
            var conns = graph.getConnectionsFrom(current);
            if (conns == null || conns.isEmpty()) break;

            TrackNode next = conns.keySet().iterator().next();
            var nextConns = graph.getConnectionsFrom(next);
            if (nextConns == null || nextConns.size() != 2) break;   // 只沿直链后退

            current = next;
        }

        return current;
    }

    private static Vec3 mainDirection(TrackGraph graph, TrackNode node) {
        var conns = graph.getConnectionsFrom(node);
        if (conns == null || conns.isEmpty()) return null;

        TrackNode neighbour = conns.keySet().iterator().next();
        Vec3 dir = node.getLocation().getLocation().subtract(neighbour.getLocation().getLocation());
        if (dir.lengthSqr() < 1e-9) return null;
        return dir.normalize();
    }

    private static Vec3 rotateY(Vec3 v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos).normalize();
    }

    private static int layBranch(ServerLevel level, TrackGraph graph, TrackNode fork,
                                 Vec3 forkPos, Vec3 direction) {
        TrackMaterial material = TrackMaterial.ALL.values().stream().findFirst().orElse(null);
        if (material == null) return 0;

        TrackNodeLocation.DiscoveredLocation previous =
            new TrackNodeLocation.DiscoveredLocation(level, forkPos.x, forkPos.y, forkPos.z)
                .materialA(material).materialB(material);

        int added = 0;
        for (int i = 1; i <= BRANCH_NODES; i++) {
            Vec3 p = forkPos.add(direction.scale(i * SEGMENT_LENGTH));
            var next = new TrackNodeLocation.DiscoveredLocation(level, p.x, p.y, p.z)
                .materialA(material).materialB(material);
            next.withNormal(new Vec3(0, 1, 0));

            graph.createNodeIfAbsent(next);
            graph.connectNodes(level, previous, next, null);
            if (LAST_BRANCH_START == null) LAST_BRANCH_START = p;
            previous = next;
            added++;
        }
        return added;
    }

    private static int degreeOf(TrackGraph graph, TrackNode node) {
        var conns = graph.getConnectionsFrom(node);
        return conns == null ? 0 : conns.size();
    }

    private static BlockPos posOf(TrackNodeLocation loc) {
        Vec3 v = loc.getLocation();
        return BlockPos.containing(v.x, v.y, v.z);
    }

    @SuppressWarnings("unused")
    private static List<TrackNode> unused() {
        return new ArrayList<>();
    }
}
