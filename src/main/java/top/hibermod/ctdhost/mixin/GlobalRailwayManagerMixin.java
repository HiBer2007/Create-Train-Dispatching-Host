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
 * <h2>为什么 require = 1</h2>
 * 这是每帧唯一的调度入口：注入不生效，内核就不会被调用，而列车**看起来仍在正常跑**。
 * 这种失效没有任何外在现象，只有「调度指令全部不生效」这一个后果——
 * 等到有人发现时，已经排查很久了。宁可开机就崩，也不要静默地什么都不做。
 *
 * <p>{@link HostRuntime} 仍会在启动后检查「有没有被调用过」，
 * 作为第二道闸门：注入成功但从未触发，同样是失效。
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
