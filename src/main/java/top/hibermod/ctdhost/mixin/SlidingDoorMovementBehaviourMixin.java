package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.decoration.slidingDoor.DoorControlBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;

import net.minecraft.core.BlockPos;

import top.hibermod.ctdhost.door.DoorPermissions;

/**
 * 车厢门：Create 判「该开」之后，再问一次宿主。
 *
 * <h2>Create 原本怎么判</h2>
 * {@code shouldOpenAt(controller, context)} 只看站台的 {@code DoorControl.mode}
 * （ALL / NORTH / … / NONE）。也就是说：**车停稳 + 站台设了方向 = 门开**，
 * 中间没有任何「信号系统是否允许」的环节。
 *
 * <h2>我们加的一层</h2>
 * 在这个返回值上再叠加一次宿主判定：站台没被允许开门时，即使 Create 认为该开，
 * 也保持关闭。反过来，Create 认为不该开时我们**不强行打开**——
 * 门是安全设备，多开一次比少开一次危险。
 *
 * <p>这条规则写在返回值上（{@code RETURN} + {@code setReturnValue}），
 * 所以不需要知道 Create 内部怎么算，也不会覆盖它。
 */
@Mixin(SlidingDoorMovementBehaviour.class)
public abstract class SlidingDoorMovementBehaviourMixin {

    @Inject(method = "shouldOpenAt", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void ctd$gateBySignalPermission(DoorControlBehaviour controller,
                                            MovementContext context,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;   // Create 说不开，就不开
        if (controller == null || controller.blockEntity == null) return;

        try {
            BlockPos stationPos = controller.blockEntity.getBlockPos();
            boolean allowed = DoorPermissions.allowed(stationPos, true);
            if (!allowed) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            top.hibermod.ctdhost.Failures.caught("车门接管：车厢门判定", t);
            // 判定出错时保持 Create 的结论——不因为我们的异常去改变门的行为
        }
    }

    /** 启动自检用。 */
    @org.spongepowered.asm.mixin.Unique
    private static void ctd$doorGateMarker() {
        throw new AssertionError();
    }
}
