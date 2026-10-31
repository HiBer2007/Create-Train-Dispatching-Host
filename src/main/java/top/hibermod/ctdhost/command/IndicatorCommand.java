package top.hibermod.ctdhost.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdhost.pis.DepartureIndicatorService;

/**
 * {@code /ctd host indicator <x> <y> <z>} —— 看发车指示器会写什么、实际写成了什么。
 *
 * <p>两段输出缺一不可：
 * <ol>
 *   <li><b>会写什么</b>：由 PIS 数据算出的字符串与颜色（纯逻辑，可核对）；</li>
 *   <li><b>实际写成了什么</b>：把每根辉光管的显示内容读回来（证明真的落到了方块上）。</li>
 * </ol>
 *
 * 只有第一段，说明不了「写进去了」；只有第二段，说明不了「该写的对不对」。
 */
public final class IndicatorCommand {

    private IndicatorCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("indicator")
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("y", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(ctx -> run(ctx.getSource(), new BlockPos(
                            IntegerArgumentType.getInteger(ctx, "x"),
                            IntegerArgumentType.getInteger(ctx, "y"),
                            IntegerArgumentType.getInteger(ctx, "z")))))));
    }

    private static int run(CommandSourceStack src, BlockPos stationPos) {
        var level = src.getLevel();

        String text = DepartureIndicatorService.describe(stationPos);
        var color = DepartureIndicatorService.colorFor();
        var tubes = DepartureIndicatorService.findTubes(level, stationPos);

        src.sendSuccess(() -> Component.literal(
            "发车指示器 @" + stationPos.toShortString()
                + " 应显示='" + text + "'"
                + " 颜色=(" + color.r() + "," + color.g() + "," + color.b() + ")"
                + " 附近辉光管=" + tubes.size() + " 根"), false);

        if (tubes.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                "  （站台 " + DepartureIndicatorService.RADIUS + " 格内没有辉光管；"
                    + "放一排辉光管，宿主每 20 刻会把文字写进去）"), false);
            return 0;
        }

        for (String line : DepartureIndicatorService.readback(level, stationPos)) {
            src.sendSuccess(() -> Component.literal("  " + line), false);
        }

        return tubes.size();
    }
}
