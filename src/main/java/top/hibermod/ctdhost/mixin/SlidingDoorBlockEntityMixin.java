package top.hibermod.ctdhost.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import top.hibermod.ctdhost.door.SignalDoorBehaviour;

/**
 * 把「信号模式」标记挂到 Create 的滑动门上。
 *
 * <h2>为什么挂在门上而不是另建一张表</h2>
 * 门自己就有 {@code SmartBlockEntity}，而 {@code addBehaviours} 是空的——
 * 正好用来挂行为。这样：
 * <ul>
 *   <li>存档跟着门走（行为参与 BE 的 NBT 读写）；</li>
 *   <li>门被拆掉，标记自然消失，不会留下孤儿记录；</li>
 *   <li>不需要额外的坐标→状态映射表去同步与清理。</li>
 * </ul>
 */
@Mixin(SlidingDoorBlockEntity.class)
public abstract class SlidingDoorBlockEntityMixin {

    @Inject(method = "addBehaviours", at = @At("TAIL"), remap = false, require = 1)
    private void ctd$addSignalDoorBehaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        behaviours.add(new SignalDoorBehaviour((SmartBlockEntity) (Object) this));
    }

    /** 启动自检用。 */
    @org.spongepowered.asm.mixin.Unique
    private static void ctd$signalDoorBehaviourMarker() {
        throw new AssertionError();
    }
}
