package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.world.level.Level;

/**
 * 在列车 tick 的最末尾施加内核要求的目标速度。
 *
 * <h2>为什么挂在 {@code Train.tick} 而不是 {@code Navigation.tick}</h2>
 * 实测（{@code /ctd host status} 的 {@code ticked=moving}）表明列车在 Create 的
 * tick 列表里，但 {@code Navigation.tick} 一次都没被执行到——
 * 那个注入点在这种情况下永远不会触发，而且**看起来像注入失效**。
 *
 * <p>{@code Train.tick} 是列车每帧唯一的必经之路：它被调用时，车厢才会推进
 * （{@code carriage.travel}）。挂在这里，无论寻路走不走，内核的目标速度都能落地。
 *
 * <h2>顺序</h2>
 * TAIL = Create 已经算完自己的 {@code targetSpeed} 并推进过车厢。
 * 我们在这之后写目标速度，下一帧立即生效，不会与本帧的物理冲突。
 */
@Mixin(Train.class)
public abstract class TrainTickMixin {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger("ctdhost-train");

    private static long calls;

    /**
     * 标记方法 —— 存在的唯一目的是让启动自检能看见「这个 mixin 真的织进来了」。
     *
     * <p>{@code @Inject} 的处理方法会被内联进目标方法然后丢弃，反射看不到它；
     * 而 {@code @Unique} 方法会被真正加进目标类。所以自检查的是这个标记。
     */
    @org.spongepowered.asm.mixin.Unique
    private static void ctd$trainTickMarker() {}

    @Inject(method = "tick", at = @At("TAIL"), require = 1, remap = false)
    private void ctd$applyKernelSpeed(Level level, CallbackInfo ci) {
        try {
            @SuppressWarnings("unused")
            Train self = (Train) (Object) this;

            if (++calls == 1) {
                LOG.info("[train] Train.tick reached at least once");
            }
            if (calls % 400 == 0) {
                LOG.info("[train] tick calls={} pending={}", calls,
                    top.hibermod.ctdhost.execute.SpeedControl.size());
            }

            top.hibermod.ctdhost.execute.SpeedControl.applyTo(self);
        } catch (Throwable ignored) {
            // 施加失败不能让 Create 的物理结果作废
        }
    }
}
