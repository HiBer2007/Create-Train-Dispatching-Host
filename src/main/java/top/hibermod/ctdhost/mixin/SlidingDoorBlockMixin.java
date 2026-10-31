package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import top.hibermod.ctdhost.door.SignalDoorBehaviour;

/**
 * 信号模式下的门：**红石与手动都不能开它**，只有宿主能。
 *
 * <h2>为什么两条路径都要拦</h2>
 * 「只有信号系统能调度」如果只拦一条，另一条就是后门：
 * <ul>
 *   <li>{@code neighborChanged} —— 红石。旁边放个拉杆就能开门，等于接管失效；</li>
 *   <li>{@code useWithoutItem} —— 右键。玩家一按就开，等于调度失效。</li>
 * </ul>
 *
 * <p>两条都拦掉之后，「门什么时候开」这件事就只剩一个来源：
 * 宿主每 20 刻算出的站台开门许可（{@code DoorService}）。
 *
 * <h2>为什么不干脆取消 {@code neighborChanged}</h2>
 * 只取消红石对 OPEN 的影响，但保留 POWERED 的同步：如果完全取消，
 * 门上的红石状态会和世界不一致，视觉与后续逻辑都会奇怪。
 * 所以这里直接跳过整个方法——Create 自己也是「有电就开」的一体逻辑，
 * 跳过它不会留下半截状态。
 */
@Mixin(SlidingDoorBlock.class)
public abstract class SlidingDoorBlockMixin {

    /** 红石：信号模式下完全不响应。 */
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ctd$ignoreRedstoneWhenSignalControlled(BlockState state, Level level, BlockPos pos,
                                                        Block neighbour, BlockPos fromPos, boolean isMoving,
                                                        CallbackInfo ci) {
        if (level.isClientSide) return;

        SignalDoorBehaviour behaviour = SignalDoorBehaviour.at(level, pos);
        if (behaviour != null && behaviour.isSignalControlled()) {
            ci.cancel();
        }
    }

    /**
     * 手动：信号模式下右键不开门。
     *
     * <p>返回 {@code SUCCESS} 而不是 {@code PASS}：PASS 会让手持物品继续处理，
     * 玩家会以为「门没反应但东西被用了」。SUCCESS 表示「这个交互到此为止」。
     */
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void ctd$denyManualOpenWhenSignalControlled(BlockState state, Level level, BlockPos pos,
                                                        Player player, BlockHitResult hitResult,
                                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide) return;

        SignalDoorBehaviour behaviour = SignalDoorBehaviour.at(level, pos);
        if (behaviour != null && behaviour.isSignalControlled()) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    /** 启动自检用。 */
    @org.spongepowered.asm.mixin.Unique
    private static void ctd$signalDoorBlockMarker() {
        throw new AssertionError();
    }
}
