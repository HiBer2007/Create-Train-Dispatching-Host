package top.hibermod.ctdhost.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdhost.Failures;

/**
 * {@code /ctd host failures} / {@code /ctd host failures clear}
 *
 * <h2>为什么需要「清零」</h2>
 * {@code swallowed:} 那一行的用途是「有没有东西悄悄坏了」。它是**累积**的，
 * 于是只要有一条告警（比如我故意触发的兜底实验），接下来每一次验收都会红——
 * 而红色一旦和"很久以前那次实验"绑定，人就会开始忽略它。
 *
 * <p>所以需要一个**显式的确认动作**：把当前列表打出来，然后由人决定清零。
 * 不是自动过期、不是阈值衰减，而是「我看见了，我知道它是什么」。
 *
 * <p>清零会先打印被清掉的内容——不能悄悄抹掉证据。
 */
public final class FailuresCommand {

    private FailuresCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("failures")
            .executes(ctx -> list(ctx.getSource()))
            .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())));
    }

    private static int list(CommandSourceStack src) {
        var snapshot = Failures.snapshot();

        if (snapshot.isEmpty()) {
            src.sendSuccess(() -> Component.literal("swallowed: none（没有东西被吞）"), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("swallowed: " + snapshot.size() + " 类"), false);

        for (var entry : snapshot.entrySet()) {
            final String line = "  " + entry.getValue() + "× " + entry.getKey();
            src.sendSuccess(() -> Component.literal(line), false);
        }

        src.sendSuccess(() -> Component.literal(
            "确认这些都能解释之后，用 /ctd host failures clear 清零"), false);

        return snapshot.size();
    }

    private static int clear(CommandSourceStack src) {
        var snapshot = Failures.snapshot();

        if (snapshot.isEmpty()) {
            src.sendSuccess(() -> Component.literal("本来就是空的"), false);
            return 0;
        }

        // 先留证据：清掉的东西要打出来，否则等于悄悄抹掉
        src.sendSuccess(() -> Component.literal("清空前有 " + snapshot.size() + " 类告警："), false);
        for (var entry : snapshot.entrySet()) {
            final String line = "  " + entry.getValue() + "× " + entry.getKey();
            src.sendSuccess(() -> Component.literal(line), false);
        }

        Failures.resetForTests();
        src.sendSuccess(() -> Component.literal("已清零；后续新出现的告警会重新记录"), false);
        return snapshot.size();
    }
}
