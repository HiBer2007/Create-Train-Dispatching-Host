package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.diagnostics.Failures;

/**
 * 拦住**损坏时刻表**在 {@code ScheduleRuntime.tick} 里的第一条越界。
 *
 * <h2>为什么需要这一层</h2>
 * 「{@code cyclic=true} 且 {@code entries} 为空」是一份合法却能崩服的状态。
 * 它会在至少三处炸开：
 *
 * <ol>
 *   <li>{@code ScheduleRuntime.startCurrentInstruction} — {@code entries.get(currentEntry)}，
 *       而 {@code currentEntry} 是 -1（本轮实测的崩溃点）；</li>
 *   <li>{@code ScheduleRuntime.tickConditions} — 同样的下标；</li>
 *   <li>{@code estimateStayDuration} — 经 {@code GlobalTrainDisplayData.refresh}
 *       每帧被调用（上一轮加的那道守卫只管这一条）。</li>
 * </ol>
 *
 * <p>早先我只看见第 3 条（当时崩溃报告里恰好是它），就以为兜底做完了。
 * 实际上 {@code runtime.tick} 跑在它前面，先炸在第 1 条上。
 * ——「按崩溃日志修 bug」的典型盲区：日志只告诉你**这一次**从哪儿炸。
 *
 * <h2>做法</h2>
 * 在 {@code tick} 的最前面判一次：损坏就**整帧跳过**并记一次失败。
 * 这是唯一能在源头挡住全部三处的位置。
 *
 * <p>为什么不顺手把坏时刻表改成空时刻表：那会**静默改掉玩家的数据**。
 * 损坏就该看得出来——所以这里只停摆、只记录，修复留给
 * {@code /ctd host schedule clear}（有人按、有人看见）。
 */
@Mixin(ScheduleRuntime.class)
public abstract class ScheduleRuntimeTickGuardMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ctd$guardCorruptSchedule(Level level, CallbackInfo ci) {
        ScheduleRuntime self = (ScheduleRuntime) (Object) this;

        if (self.schedule == null) return;

        boolean corrupt = self.schedule.entries.isEmpty();   // 空条目即损坏，与 cyclic 无关
        if (!corrupt) return;

        ctd$reportOnce(self);
        ci.cancel();
    }

    /** 只在状态变化时报一次，避免每帧刷屏把日志淹掉。 */
    @Unique
    private static void ctd$reportOnce(ScheduleRuntime runtime) {
        if (CTD_LAST_REPORTED != runtime) {
            CTD_LAST_REPORTED = runtime;
            Failures.caught("时刻表兜底：循环但无条目的时刻表，已停摆该 runtime（用 /ctd host schedule clear 修复）",
                null);
        }
    }

    @Unique
    private static ScheduleRuntime CTD_LAST_REPORTED;

    /** 启动自检用：证明注入落在真实类上。 */
    @Unique
    private static void ctd$scheduleRuntimeTickGuardMarker() {
        throw new AssertionError();
    }
}
