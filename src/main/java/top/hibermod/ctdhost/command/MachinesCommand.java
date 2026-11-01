package top.hibermod.ctdhost.command;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdhost.execute.FixedRoute;
import top.hibermod.ctdhost.execute.SwitchMachines;

/**
 * {@code /ctd host machines} —— 自动道岔机的观测。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>装了机器的岔口：允许信号系统自动扳动；</li>
 *   <li>没装机器的岔口：**固定方向**——有直行就直行，否则始终左转。</li>
 * </ul>
 *
 * <p>这条命令把两种岔口分别列出来，并给出「固定方向会走哪条」，
 * 这样玩家能一眼看出「为什么我的岔口不按调度走」。
 */
public final class MachinesCommand {

    private MachinesCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("machines")
            .executes(ctx -> list(ctx.getSource()))
            .then(Commands.literal("fixed")
                .then(Commands.argument("node", StringArgumentType.greedyString())
                    .executes(ctx -> fixed(ctx.getSource(),
                        StringArgumentType.getString(ctx, "node")))));
    }

    private static int list(CommandSourceStack src) {
        var host = top.hibermod.ctdhost.HostRuntime.get();
        var topo = host.mirror() == null ? null : host.mirror().current();

        if (topo == null) {
            src.sendFailure(Component.literal("拓扑还没建好"));
            return 0;
        }

        int machines = SwitchMachines.count();
        int junctions = 0;
        int unarmed = 0;

        src.sendSuccess(() -> Component.literal(
            "自动道岔机：" + machines + " 台（只有装了机器的岔口才允许信号系统自动扳动）"), false);

        for (String nodeId : SwitchMachines.snapshot().keySet()) {
            Rail.NodeId armedNode = Rail.NodeId.tryDecode(nodeId);
            final String armedBranches = armedNode == null ? "(解析失败)"
                : branchesOf(topo, armedNode).stream().map(Rail.EdgeId::encode)
                    .limit(3).reduce((a, b) -> a + " | " + b).orElse("(无)");
            final String line = "  " + nodeId + "  <- 机器 @" + SwitchMachines.snapshot().get(nodeId).toShortString()
                + "\n      股道: " + armedBranches;
            src.sendSuccess(() -> Component.literal(line), false);
        }

        for (var node : topo.nodes()) {
            if (!node.isSwitch()) continue;
            junctions++;
            if (SwitchMachines.hasMachine(node.id())) continue;
            unarmed++;

            List<Rail.EdgeId> branches = branchesOf(topo, node.id());
            final String branchesText = branches.stream()
                .map(Rail.EdgeId::encode)
                .limit(3)
                .reduce((a, b) -> a + " | " + b)
                .orElse("(无)");

            final String line = "  " + node.id().encode() + "  无机器 → 固定方向："
                + FixedRoute.describe(node.id(), branches, topo)
                + "\n      股道: " + branchesText;
            src.sendSuccess(() -> Component.literal(line), false);
        }

        final int j = junctions, u = unarmed;
        src.sendSuccess(() -> Component.literal(
            "合计岔口 " + j + " 个：装了机器 " + (j - u) + "，固定方向 " + u), false);

        return junctions;
    }

    private static int fixed(CommandSourceStack src, String nodeEncoded) {
        Rail.NodeId node = Rail.NodeId.tryDecode(nodeEncoded.trim());
        if (node == null) {
            src.sendFailure(Component.literal("岔口 id 解析失败：" + nodeEncoded));
            return 0;
        }

        var topo = top.hibermod.ctdhost.HostRuntime.get().mirror() == null
            ? null : top.hibermod.ctdhost.HostRuntime.get().mirror().current();
        if (topo == null) {
            src.sendFailure(Component.literal("拓扑还没建好"));
            return 0;
        }

        List<Rail.EdgeId> branches = branchesOf(topo, node);
        boolean armed = SwitchMachines.hasMachine(node);

        src.sendSuccess(() -> Component.literal(
            node.encode() + "  机器=" + armed + "  股道=" + branches.size()
                + "  固定方向=" + FixedRoute.describe(node, branches, topo)), false);

        return branches.size();
    }

    /** 从岔口出发的股道。 */
    private static List<Rail.EdgeId> branchesOf(Topology topo, Rail.NodeId node) {
        List<Rail.EdgeId> out = new java.util.ArrayList<>();
        for (var edge : topo.edgesFrom(node)) {
            out.add(edge.id());
        }
        return out;
    }
}
