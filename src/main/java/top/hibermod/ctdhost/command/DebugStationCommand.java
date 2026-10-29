package top.hibermod.ctdhost.command;

import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.operation.OperationFeed;
import top.hibermod.ctdapi.operation.TrainAtStation;

/**
 * {@code /ctd debugstation <phase>} —— 手工发一条车站相位事件。
 *
 * <h2>为什么需要</h2>
 * 「宿主判定相位 → 契约层 → 脚本出文案 → 语音源」这条链有四段，
 * 一段断了都表现为「没声音」。等一列真车开到站台再排查太慢，
 * 这个命令让你直接往第二段注入一条事件，把后三段单独验证。
 *
 * <p>它发的是**合成事件**，日志里会标明，不会被误当成真实运营事件。
 */
public final class DebugStationCommand {

    private DebugStationCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debugstation")
            .then(Commands.argument("phase", StringArgumentType.word())
                .executes(DebugStationCommand::fire));
    }

    private static int fire(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "phase").toUpperCase();

        TrainAtStation.Phase phase;
        try {
            phase = TrainAtStation.Phase.valueOf(raw);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(
                "unknown phase: " + raw + " (APPROACHING / STOPPED / DEPARTING)"));
            return 0;
        }

        TrainAtStation event = new TrainAtStation(
            UUID.nameUUIDFromBytes("ctd:debug".getBytes()),
            "DEBUG-TRAIN",
            UUID.nameUUIDFromBytes("ctd:debug-station".getBytes()),
            "调试站",
            phase,
            ctx.getSource().getLevel().getGameTime(),
            0);

        OperationFeed.trainAtStation(event);

        ctx.getSource().sendSuccess(() -> Component.literal(
            "synthetic station event sent: " + event.summary()), false);
        return 1;
    }
}
