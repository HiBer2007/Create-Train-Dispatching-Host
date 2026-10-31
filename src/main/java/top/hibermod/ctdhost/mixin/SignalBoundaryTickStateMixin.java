package top.hibermod.ctdhost.mixin;

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
 * 让「我方灯色」活过 Create 的每帧重算，**且永远不能放宽一个红灯**。
 *
 * <h2>问题</h2>
 * {@code SignalBoundary#tickState} 每个 tick 都重写 {@code cachedStates}：
 * 占用/预定 → RED，否则走链式解析。只写 {@code cachedStates} 因此只活一帧。
 *
 * <h2>为什么是 TAIL 而不是 HEAD + cancel</h2>
 * 最初写的是「HEAD 取消原版、自己算两面的灯」。那需要宿主自己判断占用，
 * 而判断占用要读 Catnip 的 {@code Couple}——它不在编译路径上，只能反射，
 * 而反射一旦出错就可能把「有车的区段」画成绿灯。
 *
 * <p>改成 TAIL：**让 Create 先按原版把灯算完**（占用、强制红、链式解析全都在），
 * 然后我们只做一件事——{{@link SignalOverrides#resolve}}：
 * **当前是 RED 就保持 RED，绝不放宽**；否则写入我方意图。
 * 这样「占用 → 红灯」这条安全不变式由 Create 自己保证，我们只能让它更严。
 *
 * <h2>过期</h2>
 * 意图带 TTL：内核停写（崩溃/被禁用/改了逻辑）之后最多 TTL tick，
 * 灯自动回到 Create 的判定。否则绿灯会永久亮着。
 */
@Mixin(SignalBoundary.class)
public abstract class SignalBoundaryTickStateMixin {

    @Inject(method = "tickState", at = @At("TAIL"), remap = false, require = 1)
    private void ctd$applyOverride(TrackGraph graph, CallbackInfo ci) {
        SignalBoundary self = (SignalBoundary) (Object) this;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        long now = server.overworld().getGameTime();
        if (!SignalOverrides.shouldTakeOver(self.getId(), now)) return;

        SignalOverrides.Entry entry = SignalOverrides.get(self.getId());
        if (entry == null || !entry.complete()) return;

        ctd$write(self, true, entry.first());
        ctd$write(self, false, entry.second());
    }

    /** 读-判-写：红灯不放宽；写失败也不抛（下一帧还会再来）。 */
    @Unique
    private static void ctd$write(SignalBoundary boundary, boolean side, SignalState wanted) {
        SignalState current = SignalApplier.readState(boundary, side);
        SignalState resolved = SignalOverrides.resolve(current, wanted);

        if (resolved == current) return;
        SignalApplier.writeState(boundary, side, resolved);
    }

    /** 启动自检用：证明这个注入真的落在 SignalBoundary 上。 */
    @Unique
    private static void ctd$tickStateGuardMarker() {
        throw new AssertionError();
    }
}
