package top.hibermod.ctdhost.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /ctd host kernels} —— 列出**当前实际注册着的内核**。
 *
 * <h2>它补的是什么</h2>
 * {@code /ctd host status} 只有一行 {@code kernels: N}。N 对不上脚本文件数时
 * （这一轮就撞上了：4 个内核文件、N=5），完全没有线索查是哪来的。
 *
 * <p>一条「失败的重载之后留下了一个多余注册」如果没有名字可看，
 * 就只能靠猜——而猜出来的结论通常会被写进文档当成事实。
 *
 * <h2>输出</h2>
 * 每个内核一行：id、kind、本轮是否参与调度。只读。
 */
public final class KernelsCommand {

    private KernelsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("kernels").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        var host = top.hibermod.ctdhost.HostRuntime.get();
        var passes = host.passes();

        if (passes.isEmpty()) {
            src.sendSuccess(() -> Component.literal("没有注册任何内核"), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("已注册内核：" + passes.size() + " 个"), false);

        for (var entry : passes.entrySet()) {
            final String line = "  " + entry.getKey() + "   pass="
                + entry.getValue().getClass().getSimpleName();
            src.sendSuccess(() -> Component.literal(line), false);
        }

        return passes.size();
    }
}
