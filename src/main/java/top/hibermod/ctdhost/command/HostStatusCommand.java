package top.hibermod.ctdhost.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdapi.operation.Assignments;
import top.hibermod.ctdhost.HostRuntime;

/**
 * {@code /ctd host ...}：宿主的自检命令。
 *
 * <h2>为什么需要它</h2>
 * 进游戏验证时，第一件事总是「到底有没有在跑」。看日志要翻文件、要会 grep，
 * 还容易被别的输出盖掉。一条命令把这些数字摊开，验证就从「读日志」变成「看屏幕」。
 *
 * <p>它**只读**，不改任何状态——允许在正式档里跑。
 */
public final class HostStatusCommand {

    private HostStatusCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("host")
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("switches").executes(ctx -> switches(ctx.getSource())))
            .then(DebugTrackCommand.build());
    }

    private static int status(CommandSourceStack src) {
        HostRuntime host = HostRuntime.get();

        final int ticks = (int) HostRuntime.tickCount();
        final int kernels = host.passes().size();
        final int units = host.units().size();
        final int assignments = Assignments.size();

        Topology view = host.mirror().current();
        final int nodes = view == null ? 0 : view.nodes().size();
        final int edges = view == null ? 0 : view.edges().size();
        final boolean someDisabled = view != null
            && view.edges().stream().anyMatch(e -> !e.enabled());
        final String switchStatus = host.switches() == null ? "n/a" : host.switches().status();

        src.sendSuccess(() -> Component.literal("CTD host status"), false);
        src.sendSuccess(() -> Component.literal("  ticks       : " + ticks), false);
        src.sendSuccess(() -> Component.literal("  kernels     : " + kernels), false);
        src.sendSuccess(() -> Component.literal("  topology    : nodes=" + nodes + " edges=" + edges
            + (someDisabled ? "  (some edges disabled by switches)" : "")), false);
        src.sendSuccess(() -> Component.literal("  units       : " + units), false);
        src.sendSuccess(() -> Component.literal("  assignments : " + assignments), false);
        src.sendSuccess(() -> Component.literal("  resolved    : " + host.assignments().summary()), false);
        src.sendSuccess(() -> Component.literal("  switches    : " + switchStatus), false);

        if (ticks == 0) {
            src.sendFailure(Component.literal(
                "  ! no tick dispatched — the host is not hooked up"));
        }
        if (nodes == 0) {
            src.sendFailure(Component.literal(
                "  ! topology empty — lay some track to exercise the mirror"));
        }
        return ticks;
    }

    private static int switches(CommandSourceStack src) {
        var applier = HostRuntime.get().switches();
        src.sendSuccess(() -> Component.literal("switch applier: "
            + (applier == null ? "not initialised" : applier.status())), false);
        if (applier == null) return 0;

        applier.locks().forEach((node, branch) -> src.sendSuccess(
            () -> Component.literal("  " + node.encode() + " -> " + branch.encode()), false));
        return applier.locks().size();
    }
}
