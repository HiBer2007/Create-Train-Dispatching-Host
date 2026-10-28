package top.hibermod.ctdhost.command;

import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/**
 * {@code /ctd host debugtrack}：**直接在轨道图里造一条边**，用来验证拓扑镜像。
 *
 * <h2>它解决什么问题</h2>
 * 无头环境（`runServer` + RCON）没法铺真实的铁轨：轨道方块需要贝塞尔数据，
 * `/setblock` 拼不出来，Create 也没有造轨命令。但镜像读的是 {@code TrackGraph}，
 * 不是方块——所以可以直接往图里插两个节点和一条边。
 *
 * <p>这条路径验证的是**镜像本身**（遍历、id 转换、度数计算），
 * 不验证「轨道方块长什么样」。后者需要图形客户端，与镜像的正确性无关。
 *
 * <h2>它是调试命令</h2>
 * 只在服务器上生效、需要权限、每次生成随机位置。不要在生产档里用——
 * 它造出来的图没有对应的方块，列车会在一个「看不见的轨道」上跑。
 */
public final class DebugTrackCommand {

    private DebugTrackCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debugtrack").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        try {
            ServerLevel level = src.getLevel();
            Vec3 base = src.getPosition();

            TrackMaterial material = TrackMaterial.ALL.values().stream().findFirst().orElse(null);
            if (material == null) {
                src.sendFailure(Component.literal("no track material registered yet"));
                return 0;
            }

            // 两个节点相距 4 格，水平放置
            TrackNodeLocation.DiscoveredLocation a =
                new TrackNodeLocation.DiscoveredLocation(level, base.x, base.y, base.z)
                    .materialA(material).materialB(material);
            TrackNodeLocation.DiscoveredLocation b =
                new TrackNodeLocation.DiscoveredLocation(level, base.x + 4, base.y, base.z)
                    .materialA(material).materialB(material);
            a.withNormal(new Vec3(0, 1, 0));
            b.withNormal(new Vec3(0, 1, 0));

            TrackGraph graph = new TrackGraph();
            graph.id = UUID.randomUUID();

            graph.createNodeIfAbsent(a);
            graph.createNodeIfAbsent(b);
            graph.connectNodes(level, a, b, null);

            Create.RAILWAYS.trackNetworks.put(graph.id, graph);

            int nodes = graph.getNodes().size();
            src.sendSuccess(() -> Component.literal(
                "debug track created: graph=" + graph.id + " nodes=" + nodes), false);
            src.sendSuccess(() -> Component.literal(
                "  run /ctd host status — topology should now be non-empty"), false);
            return nodes;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("debug track failed: " + t));
            return 0;
        }
    }
}
