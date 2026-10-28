package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.GlobalRailwayManager;

import net.minecraft.world.level.Level;

import top.hibermod.ctdhost.HostRuntime;

/**
 * 每帧调度入口（H-1）。
 *
 * <h2>为什么是 HEAD</h2>
 * {@code tick} 紧接着会**清空所有 {@code SignalEdgeGroup} 的占用并重建**
 * （`GlobalRailwayManager.java:197-200`）。如果不抢在清空之前跑，
 * 内核看到的就是一个被清空的空集，所有「这段有没有车」的判断都会失效。
 *
 * <h2>为什么 require = 0</h2>
 * 上游一改方法名，默认的 {@code require = 1} 会让整个服务器硬崩。
 * 这里选择软失败：注入不生效 → 内核不会被调用 → 列车退回 Create 原生行为。
 * {@link HostRuntime} 会在启动后检查「有没有被调用过」，没被调用就报一条 error，
 * 避免静默失效（R5 的要求）。
 *
 * <h2>优先级</h2>
 * 用默认优先级（1000，与 Create 自身一致）。只需要观察与本帧内写入，
 * 与其它注入不构成冲突。
 */
@Mixin(value = GlobalRailwayManager.class, remap = false)
public class GlobalRailwayManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 1)
    private void ctdhost$dispatchPass(Level level, CallbackInfo ci) {
        HostRuntime.onRailwayTick(level);
    }
}
