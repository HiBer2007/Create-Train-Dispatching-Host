package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.entity.Train;

/**
 * 内核接管时，跳过 Create 的被动减速。
 *
 * <h2>为什么必须跳过</h2>
 * {@code tickPassiveSlowdown()} 在没有目的地时每帧把 {@code speed} 减掉一个加速度步长。
 * 而我们的施加也是每帧加一个步长——两者正好抵消，车速恒定在
 * <b>一个步长</b>（实测 0.007）上，永远到不了目标速度：看起来「车动了」，
 * 其实是原地抖。
 *
 * <p>只在**本帧有内核速度请求**时跳过，其余情况完全交给 Create：
 * 没有指令的列车照常自然减速。
 */
@Mixin(Train.class)
public abstract class PassiveSlowdownMixin {

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger("ctdhost-passive");

    private static long cancels;

    /** 标记方法：供启动自检确认本 mixin 已织入（理由见 TrainTickMixin 的同类注释）。 */
    @org.spongepowered.asm.mixin.Unique
    private static void ctd$passiveMarker() {}

    @Inject(method = "tickPassiveSlowdown", at = @At("HEAD"),
        cancellable = true, require = 1, remap = false)
    private void ctd$skipWhenControlled(CallbackInfo ci) {
        try {
            Train self = (Train) (Object) this;
            if (top.hibermod.ctdhost.execute.SpeedControl.hasRequest(self.id)) {
                ci.cancel();
                if (++cancels == 1) {
                    LOG.info("[passive] first passive-slowdown cancel for {}", self.id);
                }
                if (cancels % 400 == 0) {
                    LOG.info("[passive] cancelled={} speed={} target={}",
                        cancels, self.speed, self.targetSpeed);
                }
            }
        } catch (Throwable ignored) {
            // 判断失败就按原版走：被动减速本来就安全
        }
    }
}
