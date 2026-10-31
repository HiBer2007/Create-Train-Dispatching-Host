package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

/**
 * 让一份损坏的时刻表**不能**把服务端弄垮。
 *
 * <h2>它挡的是什么</h2>
 * Create 的 {@code ScheduleRuntime.estimateStayDuration}：
 *
 * <pre>
 * if (index &gt;= schedule.entries.size()) {
 *     if (!schedule.cyclic) return INVALID;
 *     index = 0;                                    // cyclic 且 entries 为空
 * }
 * ScheduleEntry e = schedule.entries.get(index);    // → IndexOutOfBounds
 * </pre>
 *
 * <p>调用链是 {@code GlobalRailwayManager#tick → GlobalTrainDisplayData.refresh →
 * ScheduleRuntime.submitPredictions}，也就是**每个 tick 都在服务端主线程抛**。
 * 由于时刻表存在存档里，世界从此起不来。
 *
 * <h2>为什么这是宿主该管的事</h2>
 * 这不是 Create 的 bug 传染给我们，而是**我们的调度系统会写出时刻表**
 * （脚本内核、`/ctd host schedule` 都能写）。写坏了却让玩家丢掉整个世界，
 * 这个后果和「换一次数据」差得太远。
 *
 * <h2>它不是静默修复</h2>
 * 每次拦下来的都记 ERROR（通过 {@code Failures}，首次必报），
 * 并且 `{@code /ctd selftest}` 的「时刻表健康」项会持续是红的，
 * 直到有人跑 `/ctd host schedule clear` 真正修好数据。
 *
 * <p>换句话说：**它保证世界能起来，但不假装问题不存在。**
 */
@Mixin(ScheduleRuntime.class)
public abstract class ScheduleRuntimeGuardMixin {

    /** Create 的私有常量 {@code ScheduleRuntime.INVALID}。 */
    @Unique
    private static final int CTD_INVALID = -2;

    @Inject(method = "estimateStayDuration", at = @At("HEAD"),
        cancellable = true, remap = false, require = 1)
    private void ctd$guardEmptyCyclicSchedule(int index,
                                              CallbackInfoReturnable<Integer> cir) {
        ScheduleRuntime self = (ScheduleRuntime) (Object) this;
        Schedule schedule = self.schedule;

        if (schedule == null || !schedule.entries.isEmpty()) {   // 空条目即损坏，与 cyclic 无关
            return;
        }

        // 空循环时刻表：返回 INVALID，等价于「这条算不出停留时间」，
        // 调用方本来就要处理 INVALID。
        top.hibermod.ctdhost.Failures.caught(
            "ScheduleRuntime#estimateStayDuration（cyclic 但无条目）", null);

        cir.setReturnValue(CTD_INVALID);
    }

    /** 启动自检用：证明这个兜底真的注入了。 */
    @Unique
    private static void ctd$scheduleGuardMarker() {
        throw new AssertionError();
    }
}
