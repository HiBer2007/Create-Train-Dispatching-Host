package top.hibermod.ctdhost.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.diagnostics.Failures;

/**
 * 「循环但无条目」的时刻表会在 Create 里至少四处越界：{@code startCurrentInstruction}、
 * {@code tickConditions}、{@code estimateStayDuration}、{@code predictForEntry}。
 *
 * <h2>为什么不在每一处各堵一次</h2>
 * 因为堵不完。我这轮已经连续踩了三次：
 * 先按崩溃日志补了 {@code estimateStayDuration}，下一次崩在 {@code startCurrentInstruction}，
 * 再下一次崩在 {@code predictForEntry}。**崩溃日志只告诉你这一次从哪儿炸**，
 * 逐点补漏就是打地鼠——而每一次漏网都是服务端崩溃。
 *
 * <h2>做法：在唯一的入口临时摘掉坏时刻表</h2>
 * {@code GlobalRailwayManager#tick} 是整个列车系统的唯一每帧入口
 * （见 {@code docs/01} 的时序图）。在它 HEAD 把所有损坏的 runtime 的
 * {@code schedule} 临时置空、RETURN 时原样放回：
 *
 * <ul>
 *   <li>Create 的各条路径遇到 {@code schedule == null} 都会提前返回，因此不会越界；</li>
 *   <li>**玩家数据不动**：置空只持续 Create 这一帧，然后原样恢复。
 *       这一点和「直接清掉坏时刻表」有本质区别——后者会静默改存档。</li>
 * </ul>
 *
 * <p>若 Create 的 tick 抛异常导致 RETURN 没跑到，下一次 HEAD 会先把上一帧的残留恢复，
 * 所以不会出现「时刻表永久消失」。
 */
@Mixin(GlobalRailwayManager.class)
public abstract class CorruptScheduleTickMixin {

    @Unique
    private static final List<ScheduleRuntime> CTD_STASHED_RUNTIMES = new ArrayList<>();

    @Unique
    private static final List<Schedule> CTD_STASHED_SCHEDULES = new ArrayList<>();

    /** 已报过警的列车名：同一列车只报一次，避免每帧刷屏。 */
    @Unique
    private static final java.util.Set<String> CTD_REPORTED = new java.util.HashSet<>();

    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 1)
    private void ctd$stashCorruptSchedules(Level level, CallbackInfo ci) {
        // 上一帧如果因为异常没恢复，这里补上
        ctd$restore();

        if (level == null || !level.dimension().equals(Level.OVERWORLD)) return;

        try {
            java.util.Set<String> corruptNow = new java.util.HashSet<>();

            for (var train : Create.RAILWAYS.trains.values()) {
                if (train.runtime == null) continue;

                Schedule schedule = train.runtime.schedule;
                if (schedule == null) continue;
                if (!schedule.entries.isEmpty()) continue;   // 空条目即损坏，与 cyclic 无关

                CTD_STASHED_RUNTIMES.add(train.runtime);
                CTD_STASHED_SCHEDULES.add(schedule);
                train.runtime.schedule = null;

                String key = train.name.getString();
                corruptNow.add(key);

                // 只报一次：这段每帧都跑，不节流的话一次事故能刷出几百条，
                // 把 swallowed 变成噪音——而它的用途正是「有没有新东西坏了」。
                if (CTD_REPORTED.add(key)) {
                    Failures.caught("时刻表兜底：'" + key
                        + "' 持有的时刻表没有条目（任何空时刻表都会让 Create 越界），已在源头临时摘除（用 /ctd host schedule clear 永久修复）",
                        null);
                }
            }

            // 只保留「现在仍然坏着」的列车名：
            // 修好之后再坏，才会重新报一次。否则同一列车第二次故障是静默的。
            CTD_REPORTED.retainAll(corruptNow);
        } catch (Throwable t) {
            Failures.caught("时刻表兜底：摘除阶段", t);
            ctd$restore();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false, require = 1)
    private void ctd$putBackCorruptSchedules(Level level, CallbackInfo ci) {
        ctd$restore();
    }

    @Unique
    private static void ctd$restore() {
        for (int i = 0; i < CTD_STASHED_RUNTIMES.size(); i++) {
            try {
                CTD_STASHED_RUNTIMES.get(i).schedule = CTD_STASHED_SCHEDULES.get(i);
            } catch (Throwable t) {
                Failures.caught("时刻表兜底：恢复阶段", t);
            }
        }
        CTD_STASHED_RUNTIMES.clear();
        CTD_STASHED_SCHEDULES.clear();
    }

    /** 启动自检用。 */
    @Unique
    private static void ctd$corruptScheduleStashMarker() {
        throw new AssertionError();
    }
}
