package top.hibermod.ctdhost.command;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /ctd host schedule} —— 把每列车的时刻表逐条打出来。
 *
 * <h2>为什么需要它</h2>
 * {@code /ctd host status} 里只有一个 {@code schedule=N}。N 对了不等于内容对了：
 * 目的地、条件都可能与我们以为的不一样。而这个数字恰好是
 * <b>CRN 也在读的同一份数据</b>（{@code Train.runtime.schedule}），
 * 所以它是「我们的视图」与「CRN 的显示」之间唯一能在无 GUI 时对齐的东西。
 *
 * <h2>{@code fake} 分支：为什么只打印、不排点</h2>
 * 早先这一版真的调了 {@code runtime.setSchedule(...)}，目的地是个不存在的车站名。
 * 结果 Create 的寻路在找不到该车站时把服务端线程卡死，日志里是：
 *
 * <pre>
 * java.lang.Error: ServerHangWatchdog detected that a single server tick took
 * 180000000.00 seconds (should be max 0.05)
 * </pre>
 *
 * <p>整个服务端直接倒下。<b>用假数据喂真运行时，就是拿服务端当赌注。</b>
 * 现在 {@code fake} 只在内存里造一份 {@code Schedule} 交给同一个打印函数，
 * 既不写列车、也不进寻路——打印路径照样被验证，服务端不会有风险。
 */
public final class ScheduleCommand {

    private ScheduleCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("schedule")
            .executes(ctx -> run(ctx.getSource()))
            // 清空所有列车的时刻表。
            //
            // 这条命令的存在是被逼出来的：`fake` 早先真的把一份**条件组为空**的时刻表
            // 写进了列车，随后 Create 在 GlobalTrainDisplayData.refresh →
            // ScheduleRuntime.estimateStayDuration 上抛
            // `IndexOutOfBoundsException: Index 0 out of bounds for length 0`，
            // 每次都崩在 tick 循环里——世界就此起不来。
            //
            // 教训：**写世界的命令，必须同时提供撤销它的命令，并且撤销要先于写被验证。**
            .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
            .then(Commands.literal("fake").executes(ctx -> fake(ctx.getSource())));
    }

    /** 把所有列车的时刻表清空——从「已损坏的存档」里恢复用。 */
    private static int clear(CommandSourceStack src) {
        int cleared = 0;

        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.runtime == null) continue;

            Schedule current = train.runtime.schedule;
            if (current == null || current.entries.isEmpty()) continue;

            train.runtime.setSchedule(new Schedule(), false);
            cleared++;
        }

        final int count = cleared;
        src.sendSuccess(() -> Component.literal("cleared schedule on " + count + " train(s)"), false);
        return cleared;
    }

    private static int run(CommandSourceStack src) {
        int shown = 0;

        for (Train train : Create.RAILWAYS.trains.values()) {
            final String name = train.name == null ? "(unnamed)" : train.name.getString();
            src.sendSuccess(() -> Component.literal("train " + name), false);

            if (train.runtime == null) {
                src.sendSuccess(() -> Component.literal("  runtime  : (null)"), false);
                shown++;
                continue;
            }

            dump(src, train.runtime.schedule);
            shown++;
        }

        if (shown == 0) {
            src.sendSuccess(() -> Component.literal("no trains"), false);
        }

        final int count = shown;
        src.sendSuccess(() -> Component.literal(
            count + " train(s); same Train.runtime.schedule CRN reads"), false);

        return shown;
    }

    /** 只打印一份合成时刻表，不写列车、不触发寻路。 */
    private static int fake(CommandSourceStack src) {
        Schedule schedule = new Schedule();
        schedule.cyclic = true;

        for (String destination : List.of("CTD-TEST-A", "CTD-TEST-B")) {
            DestinationInstruction instr = new DestinationInstruction();
            instr.getData().putString("Text", destination);
            // 条件用**空列表**（没有条件组），不要写成 List.of(List.of())：
            // 那是一个「含一个空组」的列表，Create 取第 0 个条件时会越界。
            schedule.entries.add(new ScheduleEntry(instr, List.of()));
        }

        src.sendSuccess(() -> Component.literal(
            "synthetic schedule (in memory only, never assigned to a train)"), false);

        dump(src, schedule);
        return schedule.entries.size();
    }

    /** 唯一的打印实现：{@code run} 与 {@code fake} 共用，避免两条路径漂移。 */
    private static void dump(CommandSourceStack src, Schedule schedule) {
        if (schedule == null || schedule.entries.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  schedule : 0 entries"), false);
            return;
        }

        final int entryCount = schedule.entries.size();
        final boolean cyclic = schedule.cyclic;
        src.sendSuccess(() -> Component.literal(
            "  schedule : " + entryCount + " entries, cyclic=" + cyclic), false);

        for (int i = 0; i < entryCount; i++) {
            final int index = i;
            ScheduleEntry entry = schedule.entries.get(i);

            // ScheduleInstruction 继承 ScheduleDataEntry，后者的 getData() 返回 CompoundTag
            // （不是指令本身）。早先写成 getData().getClass().getSimpleName()，
            // 于是每一条都打成 "CompoundTag"——有输出、没信息。
            String label = "?";
            if (entry.instruction != null) {
                String text = entry.instruction.getData() == null
                    ? ""
                    : entry.instruction.getData().getString("Text");

                label = entry.instruction.getClass().getSimpleName()
                    + (text == null || text.isEmpty() ? "" : " -> " + text);
            }
            final String kind = label;

            final List<?> groups = entry.conditions == null ? List.of() : List.copyOf(entry.conditions);
            final int conditionCount = entry.conditions == null ? 0
                : entry.conditions.stream().mapToInt(List::size).sum();

            src.sendSuccess(() -> Component.literal(
                "  [" + index + "] " + kind
                    + " groups=" + groups.size()
                    + " conditions=" + conditionCount), false);
        }
    }
}
