package top.hibermod.ctdhost.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /ctd host hold &lt;车站&gt; &lt;站台&gt; &lt;true|false&gt; —— 查看与设置扣车。
 *
 * <p>扣车由调度内核通过 {@code out.hold(...)} 下发；这个命令用来核对
 * 「内核写了没有」以及手工覆盖，避免只能靠显示器猜。
 */
public final class HoldCommand {

    private HoldCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("hold")
            .executes(ctx -> {
                var src = ctx.getSource();
                int n = top.hibermod.ctdapi.status.HoldStates.size();
                src.sendSuccess(() -> Component.literal("扣车状态：已记录 " + n + " 个站台"), false);
                return 1;
            })
            .then(Commands.argument("station", StringArgumentType.string())
                .then(Commands.argument("platform", StringArgumentType.string())
                    .then(Commands.argument("held", BoolArgumentType.bool())
                        .executes(ctx -> {
                            String station = StringArgumentType.getString(ctx, "station");
                            String platform = StringArgumentType.getString(ctx, "platform");
                            boolean held = BoolArgumentType.getBool(ctx, "held");
                            top.hibermod.ctdapi.status.HoldStates.put(station, platform, held);
                            var src = ctx.getSource();
                            src.sendSuccess(() -> Component.literal(
                                (held ? "扣车：" : "解除扣车：") + station + " 站台 " + platform), false);
                            return 1;
                        })))
                .then(Commands.argument("platform", StringArgumentType.string())
                    .executes(ctx -> {
                        String station = StringArgumentType.getString(ctx, "station");
                        String platform = StringArgumentType.getString(ctx, "platform");
                        boolean held = top.hibermod.ctdapi.status.HoldStates.isHeld(station, platform);
                        var src = ctx.getSource();
                        src.sendSuccess(() -> Component.literal(
                            station + " 站台 " + platform + " → " + (held ? "扣车中" : "正常")), false);
                        return 1;
                    })));
    }
}