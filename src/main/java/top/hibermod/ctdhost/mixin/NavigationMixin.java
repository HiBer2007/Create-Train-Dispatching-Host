package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.world.level.Level;

/**
 * 在 Create 寻路**之后**再施加一次内核的目标速度。
 *
 * <h2>为什么必须在这里</h2>
 * {@code Navigation.tick()} 末尾会写 {@code train.targetSpeed = targetSpeed}
 * 然后立刻 {@code approachTargetSpeed(1)}。我们的调度 pass 跑在
 * {@code GlobalRailwayManager.tick} 的 HEAD——比它更早——于是
 * 内核刚设好的目标速度会被 Create 当场覆盖掉。
 *
 * <p>现象极具欺骗性：内核日志显示「已下发 0.35」，遥测里 {@code target=0.35}
 * （我们写的那一刻），但车速永远是 0，因为下一次 tick 又被盖回去了。
 *
 * <h2>只收紧</h2>
 * 这里写入的是 {@link SpeedControl#applyTo(Train)} 决定的值：
 * 它已经与内核之间的最小值、授权换算上限、车辆最高速比较过。
 * 本注入不再做判断，只负责「在正确的时刻写进去」。
 */
@Mixin(Navigation.class)
public abstract class NavigationMixin {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger("ctdhost-nav");

    private static long calls;

    @Shadow
    public Train train;

    @Inject(method = "tick", at = @At("TAIL"), require = 1, remap = false)
    private void ctd$applyKernelSpeed(Level level, CallbackInfo ci) {
        try {
            if (train == null) return;

            if (++calls == 1) {
                LOG.info("[nav] first Navigation.tick observed for {}", train.id);
            }
            if ((calls % 200) == 0) {
                System.out.println("[nav] tail calls=" + calls + " train=" + train.id
                    + " pending=" + top.hibermod.ctdhost.execute.SpeedControl.size());
            }
            top.hibermod.ctdhost.execute.SpeedControl.applyTo(train);
        } catch (Throwable ignored) {
            // 施加失败不能让 Create 的寻路结果作废：本帧退回原生
        }
    }
}
