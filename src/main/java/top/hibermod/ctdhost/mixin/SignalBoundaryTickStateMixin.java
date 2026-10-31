package top.hibermod.ctdhost.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import top.hibermod.ctdhost.execute.SignalApplier;
import top.hibermod.ctdhost.execute.SignalOverrides;

/**
 * 让「我方灯色」活过 Create 的每帧重算。
 *
 * <h2>问题</h2>
 * {@code SignalBoundary#tickState} 每个 tick 都重写 {@code cachedStates}：
 * 占用/预定 → RED，否则走链式解析。所以只写 {@code cachedStates}
 * <b>只在当帧有效</b>，下一帧就被抹掉——表现为「内核写了灯色，灯从来不按它变」。
 *
 * <h2>做法</h2>
 * 在 {@code tickState} 的 HEAD 处判断：这条边界<b>两面都有新鲜意图</b>时，
 * 写入我方灯色并取消原版重算；否则<b>完全放行</b>。
 *
 * <h2>为什么要求「两面都有」</h2>
 * 取消原版意味着这一帧两面都不会被 Create 计算。只接管一面、另一面停在上一帧的值，
 * 会产生「一半我方、一半陈旧」的灯——那种灯最难排障。宁可整条边界交给原版。
 *
 * <h2>为什么保留红石强制红</h2>
 * 红石强制红是外部安全输入，优先级高于调度意图：即便我们有灯色，
 * 被强制红的那一面也写 RED。
 */
@Mixin(SignalBoundary.class)
public abstract class SignalBoundaryTickStateMixin {

    @Inject(method = "tickState", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ctd$applyOverride(TrackGraph graph, CallbackInfo ci) {
        SignalBoundary self = (SignalBoundary) (Object) this;
        UUID id = self.getId();

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = server.overworld().getGameTime();
        if (!SignalOverrides.shouldTakeOver(id, now)) return;

        SignalOverrides.Entry entry = SignalOverrides.get(id);
        if (entry == null || !entry.complete()) return;

        SignalState first = self.isForcedRed(true) ? SignalState.RED : entry.first();
        SignalState second = self.isForcedRed(false) ? SignalState.RED : entry.second();

        boolean okFirst = SignalApplier.writeState(self, true, first);
        boolean okSecond = SignalApplier.writeState(self, false, second);

        // 写失败（反射不可用）就不要取消原版——否则灯会僵在旧值
        if (okFirst && okSecond) {
            ci.cancel();
        }
    }

    /** 启动自检用：证明这个注入真的落在 SignalBoundary 上。 */
    @Unique
    private static void ctd$tickStateGuardMarker() {
        throw new AssertionError();
    }
}
