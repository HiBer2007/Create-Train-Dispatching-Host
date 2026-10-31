package top.hibermod.ctdhost.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.pis.Pis;
import top.hibermod.ctdhost.pis.BoardStore;

/**
 * {@code /ctd host departures} —— 发车指示器的数据源。
 *
 * <h2>它算的是什么</h2>
 * 每块 PIS 屏的第一条（最近的）发车信息，换算成**相对图定的偏差**：
 *
 * <pre>
 *   偏差 = eta - scheduled        （正数 = 晚点，负数 = 早点）
 *   早点 → 绿色；晚点 → 红色；正点 → 白色
 * </pre>
 *
 * <p>这就是发车指示器要显示的全部内容。把它单独做成命令有两个原因：
 * <ol>
 *   <li>方块还没画出来之前，数据本身就能核对；</li>
 *   <li>颜色规则是**语义**，不是渲染细节——写在这里才能被测试与复用。</li>
 * </ol>
 *
 * <p>没有 PIS 推送时明确说「没有数据」，而不是显示 0——0 会被误读成「正点」。
 */
public final class DeparturesCommand {

    private DeparturesCommand() {}

    /** 颜色：早点绿、晚点红、正点白。 */
    public enum Lamp { EARLY, ON_TIME, LATE, UNKNOWN }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("departures").executes(ctx -> run(ctx.getSource()));
    }

    /** 纯函数：给定图定与实时，返回颜色。可离线测。 */
    public static Lamp lampFor(int scheduledTicks, int etaTicks) {
        if (scheduledTicks < 0 || etaTicks < 0) return Lamp.UNKNOWN;
        int deviation = etaTicks - scheduledTicks;
        if (deviation < 0) return Lamp.EARLY;
        if (deviation > 0) return Lamp.LATE;
        return Lamp.ON_TIME;
    }

    /** 偏差（正数晚点），不可知时返回 0。 */
    public static int deviationOf(int scheduledTicks, int etaTicks) {
        if (scheduledTicks < 0 || etaTicks < 0) return 0;
        return etaTicks - scheduledTicks;
    }

    /** 把 tick 偏差格式化成 {@code +1:23} / {@code -0:45} / {@code 0:00}。 */
    public static String formatDeviation(int ticks) {
        int totalSeconds = Math.abs(ticks) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String sign = ticks > 0 ? "+" : ticks < 0 ? "-" : "";
        return sign + minutes + ":" + String.format("%02d", seconds);
    }

    private static int run(CommandSourceStack src) {
        var boards = BoardStore.snapshot();

        if (boards.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                "还没有 PIS 屏内容（内核发 out.board(...) 之后这里会有数据）"), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal(
            "发车指示数据：" + boards.size() + " 块屏，t=" + BoardStore.gameTime()), false);

        int shown = 0;
        for (var entry : boards.entrySet()) {
            Pis.BoardModel model = entry.getValue();
            if (model.entries().isEmpty()) {
                final String id = entry.getKey();
                src.sendSuccess(() -> Component.literal("  [" + id + "] 无发车信息"), false);
                continue;
            }

            Pis.BoardEntry first = model.entries().get(0);
            Lamp lamp = lampFor(first.scheduledTicks(), first.etaTicks());
            String deviation = formatDeviation(deviationOf(first.scheduledTicks(), first.etaTicks()));

            final String line = "  [" + entry.getKey() + "] " + first.serviceCode()
                + " → " + first.destination().getString()
                + "  图定=" + first.scheduledTicks() + "t"
                + " 实时=" + first.etaTicks() + "t"
                + " 偏差=" + deviation
                + " 颜色=" + lamp;
            src.sendSuccess(() -> Component.literal(line), false);
            shown++;
        }

        return shown;
    }
}
