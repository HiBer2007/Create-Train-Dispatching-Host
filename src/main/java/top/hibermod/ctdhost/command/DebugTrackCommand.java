package top.hibermod.ctdhost.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

/**
 * 造/接一段足够长的直轨。
 *
 * <h2>为什么不是「造一张新图」</h2>
 * 世界里的列车挂在**它自己那张图**上。新造一张图对已有列车毫无帮助：
 * 车还是在原来的 4 格残段上，两端都 {@code blocked}，Create 每帧把速度清零。
 *
 * <p>所以现在的行为是：**在多出来的那一端接长**。已有图就找它的端点，
 * 沿着这条边本来的方向继续铺 96 格；没有图才新建一条。
 *
 * <h2>它是调试命令</h2>
 * 只造图数据、不铺方块，所以列车跑在「看不见的轨道」上。
 * 用途只有一个：让「脚本能不能驱动列车」这个问题变得可观测。
 */
public final class DebugTrackCommand {

    /** 每次接长多少段，每段多长。96 格刚好够一列 4 节车厢起步与刹车。 */
    private static final int SEGMENTS = 24;
    private static final double SEGMENT_LENGTH = 4.0;

    private DebugTrackCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debugtrack").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        try {
            ServerLevel level = src.getLevel();

            TrackMaterial material = TrackMaterial.ALL.values().stream().findFirst().orElse(null);
            if (material == null) {
                src.sendFailure(Component.literal("no track material registered yet"));
                return 0;
            }

            TrackGraph graph = largestGraph();
            Vec3 base = src.getPosition();
            int added;

            if (graph == null) {
                graph = new TrackGraph();
                graph.id = UUID.randomUUID();
                Create.RAILWAYS.trackNetworks.put(graph.id, graph);
                added = buildFresh(level, graph, base, material);
            } else {
                added = extend(level, graph, material);
                if (added == 0) {
                    // 找不到端点（成环）——那就另起一条
                    graph = new TrackGraph();
                    graph.id = UUID.randomUUID();
                    Create.RAILWAYS.trackNetworks.put(graph.id, graph);
                    added = buildFresh(level, graph, base, material);
                }
            }

            final int nodes = graph.getNodes().size();
            final UUID gid = graph.id;
            final int generated = added;
            src.sendSuccess(() -> Component.literal(
                "debug track: graph=" + gid + " nodes=" + nodes
                    + " +" + generated + " node(s) (" + (int) (SEGMENTS * SEGMENT_LENGTH) + " blocks)"
            ), false);
            src.sendSuccess(() -> Component.literal(
                "  next: /ctd host status — a train on this graph should stop reporting blocked"), false);
            return added;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("debug track failed: " + t));
            return 0;
        }
    }

    /** 节点最多的那张图，通常就是真实线路。 */
    private static TrackGraph largestGraph() {
        TrackGraph best = null;
        int bestSize = -1;
        for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
            int size = g.getNodes().size();
            if (size > bestSize) {
                bestSize = size;
                best = g;
            }
        }
        return best;
    }

    private static int buildFresh(ServerLevel level, TrackGraph graph, Vec3 base, TrackMaterial material) {
        TrackNodeLocation.DiscoveredLocation previous = null;
        int added = 0;

        for (int i = 0; i <= SEGMENTS; i++) {
            var node = new TrackNodeLocation.DiscoveredLocation(
                level, base.x + i * SEGMENT_LENGTH, base.y, base.z)
                .materialA(material).materialB(material);
            node.withNormal(new Vec3(0, 1, 0));

            graph.createNodeIfAbsent(node);
            if (previous != null) graph.connectNodes(level, previous, node, null);
            previous = node;
            added++;
        }
        return added;
    }

    /**
     * 找图上的一个**端点**（只连了一条边），沿着那条边原本的方向继续铺。
     *
     * <p>从端点出发而不是从中间，是因为列车停在端点附近时最缺的就是「前面还有路」。
     */
    private static int extend(ServerLevel level, TrackGraph graph, TrackMaterial material) {
        // **两端都接长**。
        //
        // 只接一端时，如果接在了列车背后，车头照样顶着尽头——实测过一次，
        // 接完 nodes 从 52 涨到 76，`blocked` 仍然是 true，看起来像「接长没用」。
        // 车朝哪边是它自己的状态，命令不该去猜。
        int total = 0;
        for (TrackNodeLocation leaf : leaves(graph, 2)) {
            total += extendFrom(level, graph, material, leaf);
        }
        return total;
    }

    /** 图上度数 1 的节点，最多取 {@code limit} 个。 */
    private static List<TrackNodeLocation> leaves(TrackGraph graph, int limit) {
        List<TrackNodeLocation> found = new ArrayList<>();
        for (var loc : graph.getNodes()) {
            if (found.size() >= limit) break;
            TrackNode node = graph.locateNode(loc);
            if (node == null) continue;
            var conns = graph.getConnectionsFrom(node);
            if (conns != null && conns.size() == 1) found.add(node.getLocation());
        }
        return found;
    }

    private static int extendFrom(ServerLevel level, TrackGraph graph, TrackMaterial material,
                                  TrackNodeLocation leaf) {
        TrackNode node = graph.locateNode(leaf);
        if (node == null) return 0;
        var conns = graph.getConnectionsFrom(node);
        if (conns == null || conns.isEmpty()) return 0;

        TrackNode neighbour = conns.keySet().iterator().next();
        Vec3 here = node.getLocation().getLocation();
        Vec3 there = neighbour.getLocation().getLocation();
        Vec3 dir = here.subtract(there);
        if (dir.lengthSqr() < 1e-9) return 0;

        Vec3 direction = dir.normalize();

        Vec3 from = leaf.getLocation();
        TrackNodeLocation.DiscoveredLocation previous =
            new TrackNodeLocation.DiscoveredLocation(level, from.x, from.y, from.z)
                .materialA(material).materialB(material);
        previous.withNormal(new Vec3(0, 1, 0));

        int added = 0;
        for (int i = 1; i <= SEGMENTS; i++) {
            Vec3 p = from.add(direction.scale(i * SEGMENT_LENGTH));
            var next = new TrackNodeLocation.DiscoveredLocation(level, p.x, p.y, p.z)
                .materialA(material).materialB(material);
            next.withNormal(new Vec3(0, 1, 0));

            graph.createNodeIfAbsent(next);
            graph.connectNodes(level, previous, next, null);
            previous = next;
            added++;
        }
        return added;
    }

    /** 保留给未来的「在哪里接长」提示用；目前无调用。 */
    @SuppressWarnings("unused")
    private static BlockPos posOf(TrackNodeLocation loc) {
        Vec3 v = loc.getLocation();
        return BlockPos.containing(v.x, v.y, v.z);
    }

    @SuppressWarnings("unused")
    private static List<UUID> ids(TrackGraph graph) {
        return new ArrayList<>(List.of(graph.id));
    }
}
