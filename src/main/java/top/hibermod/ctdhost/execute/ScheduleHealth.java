package top.hibermod.ctdhost.execute;

/**
 * 时刻表健康判定 —— 一条从事故里换来的规则。
 *
 * <h2>Create 的地雷</h2>
 * {@code ScheduleRuntime.estimateStayDuration}：
 *
 * <pre>
 * if (index &gt;= schedule.entries.size()) {
 *     if (!schedule.cyclic) return INVALID;
 *     index = 0;                       // ← cyclic 且 entries 为空时
 * }
 * ScheduleEntry e = schedule.entries.get(index);   // ← IndexOutOfBounds
 * </pre>
 *
 * <p>也就是说：**{@code cyclic = true} 且 {@code entries} 为空的时刻表，每个 tick 都会抛**。
 * 由于它发生在 {@code GlobalRailwayManager#tick → GlobalTrainDisplayData.refresh} 里，
 * 表现是服务端直接崩，而且时刻表存在存档里——重启也起不来。
 *
 * <p>这不是我们的 bug（Create 自己的边界没处理），但**是我们能提前看见的状态**：
 * 一条明确的状态判定 + 一句「用哪条命令清掉」，比事后翻 crash report 便宜得多。
 *
 * <p>纯函数，不引用任何游戏类型，所以可以离线测。
 */
public final class ScheduleHealth {

    private ScheduleHealth() {}

    /**
     * 这份时刻表会不会让 Create 崩。
     *
     * @param cyclic     {@code Schedule.cyclic}
     * @param entryCount {@code Schedule.entries.size()}
     */
    public static boolean isCorrupt(boolean cyclic, int entryCount) {
        // 判据是 **entries 为空**，与 cyclic 无关。
        //
        // 早先写的是 `cyclic && entryCount == 0`，因为第一份坏数据恰好是那样。
        // 但 `ScheduleRuntime.startCurrentInstruction` 里是无条件的
        // `schedule.entries.get(currentEntry)`，`currentEntry` 初值 -1——
        // 所以**任何空 entries 的时刻表都会崩**，非循环的也一样。
        // 这条错误判据直接导致「用 clear 修好之后又崩」（clear 写的正是空 + 非循环）。
        return entryCount <= 0;
    }

    /** 一句能直接贴进日志的解释。 */
    public static String describe(boolean cyclic, int entryCount) {
        if (!isCorrupt(cyclic, entryCount)) {
            return "ok (cyclic=" + cyclic + ", entries=" + entryCount + ")";
        }
        return "CORRUPT: cyclic=true but entries=" + entryCount
            + " — Create's ScheduleRuntime.estimateStayDuration calls entries.get(0) here"
            + " and throws every tick; run /ctd host schedule clear to recover";
    }
}
