package top.hibermod.ctdhost.command;

import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import top.hibermod.ctdhost.door.DoorPermissions;
import top.hibermod.ctdhost.door.DoorService;
import top.hibermod.ctdhost.door.SignalDoorBehaviour;

/**
 * {@code /ctd host doors …} —— 车门接管的观测与手动干预。
 *
 * <h2>子命令</h2>
 * <ul>
 *   <li>{@code /ctd host doors} —— 每个站台的开门许可、附近门的信号模式；</li>
 *   <li>{@code /ctd host doors mode <x> <y> <z>} —— 切换某扇门的信号模式；</li>
 *   <li>{@code /ctd host doors open|close <x> <y> <z>} —— 显式指定某个站台开门/关门；</li>
 *   <li>{@code /ctd host doors auto <x> <y> <z>} —— 清掉显式指定，交回自动判定。</li>
 * </ul>
 *
 * <p>「显式指定」优先于自动判定，且**不会过期**——这是有意的：
 * 门是安全设备，调度说「这个站台现在别开门」就应该一直别开，
 * 直到有人明确解除。自动过期会让一次误判变成「过一会儿自己就好了」。
 */
public final class DoorsCommand {

    private DoorsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("doors")
            .executes(ctx -> list(ctx.getSource()))
            .then(Commands.literal("mode")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(ctx -> toggleMode(ctx.getSource(),
                                pos(ctx.getSource(), ctx)))))))
            .then(Commands.literal("open")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(ctx -> setExplicit(ctx.getSource(), pos(ctx.getSource(), ctx), true))))))
            .then(Commands.literal("close")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(ctx -> setExplicit(ctx.getSource(), pos(ctx.getSource(), ctx), false))))))
            .then(Commands.literal("auto")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(ctx -> setExplicit(ctx.getSource(), pos(ctx.getSource(), ctx), null))))));
    }

    private static BlockPos pos(CommandSourceStack src, com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        return new BlockPos(
            IntegerArgumentType.getInteger(ctx, "x"),
            IntegerArgumentType.getInteger(ctx, "y"),
            IntegerArgumentType.getInteger(ctx, "z"));
    }

    private static int list(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        int stations = 0;
        int allowedCount = 0;
        int signalDoors = 0;

        src.sendSuccess(() -> Component.literal(
            "车门接管：每 " + DoorService.INTERVAL_TICKS + " 刻判定一次"
                + "（车停稳且距站台 ≤" + (int) DoorService.TRAIN_RADIUS + " 格）"), false);

        for (var graph : Create.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                BlockPos stationPos = station.getBlockEntityPos();
                if (stationPos == null) continue;

                stations++;
                boolean stopped = DoorService.isTrainStoppedAt(level, stationPos);
                boolean allowed = DoorPermissions.allowed(stationPos, stopped);
                boolean explicit = DoorPermissions.hasExplicit(stationPos);
                if (allowed) allowedCount++;

                List<String> doors = DoorService.describe(level, stationPos);
                for (String d : doors) if (d.contains("signalMode=true")) signalDoors++;

                final String line = "  " + stationPos.toShortString()
                    + " name=" + (station.name == null ? "?" : station.name)
                    + " trainStopped=" + stopped
                    + " doorsAllowed=" + allowed
                    + (explicit ? " (显式)" : " (自动)")
                    + " doors=" + doors.size()
                    + " [" + DoorService.counters() + "]";
                src.sendSuccess(() -> Component.literal(line), false);
            }
        }

        if (stations == 0) {
            src.sendSuccess(() -> Component.literal("  世界里没有车站"), false);
        }

        final int s = stations, a = allowedCount, d = signalDoors;
        src.sendSuccess(() -> Component.literal(
            "合计 " + s + " 站，允许开门 " + a + "，信号模式的门 " + d + " 扇"), false);
        return stations;
    }

    private static int toggleMode(CommandSourceStack src, BlockPos pos) {
        var behaviour = SignalDoorBehaviour.at(src.getLevel(), pos);
        if (behaviour == null) {
            src.sendFailure(Component.literal(
                pos.toShortString() + " 不是 Create 的滑动门（或没有挂上信号行为）"));
            return 0;
        }

        boolean now = behaviour.toggle();
        src.sendSuccess(() -> Component.literal(
            pos.toShortString() + " 信号模式 = " + now
                + (now ? "（红石与手动都不再能开它，只有调度能）" : "（恢复 Create 原有行为）")), false);
        return now ? 1 : 0;
    }

    private static int setExplicit(CommandSourceStack src, BlockPos stationPos, Boolean allowed) {
        DoorPermissions.setExplicit(stationPos, allowed);

        if (allowed == null) {
            src.sendSuccess(() -> Component.literal(
                stationPos.toShortString() + " 交回自动判定"), false);
        } else {
            src.sendSuccess(() -> Component.literal(
                stationPos.toShortString() + " 显式 " + (allowed ? "允许开门" : "禁止开门")
                    + "（不会自动过期，用 auto 解除）"), false);
        }
        return 1;
    }

    /** 诊断用：当前所有自动判定。 */
    public static Map<BlockPos, Boolean> autoSnapshot() {
        return DoorPermissions.autoSnapshot();
    }
}
