package top.hibermod.ctdhost.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.OverlayState;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour.RenderedTrackOverlayType;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自建信号机方块的渲染器。
 *
 * <p>照抄 Create 的 {@code SignalRenderer.renderSafe}，但**去掉**
 * {@code VisualizationManager.supportsVisualization} 的提前返回：
 * 那一行是因为 Create 用 Flywheel 的 {@code SignalVisual} 接管了渲染。
 * 我们的方块实体类型没有注册 Flywheel 可视化，所以一旦走进那个分支，
 * 屏幕上就什么都不会画——这正是「轨道上没有那块板子」的原因。
 *
 * <p>它画两样东西：
 * <ol>
 *   <li>方块本体上的信号灯（{@code AllPartialModels.SIGNAL_ON/OFF}）</li>
 *   <li>**轨道上的那块板子**：{@code TrackTargetingBehaviour.render(...)}，
 *       类型按双面开关取 {@code DUAL_SIGNAL} 或 {@code SIGNAL}</li>
 * </ol>
 */
public class CtdSignalRenderer extends SafeBlockEntityRenderer<SignalBlockEntity> {

    public CtdSignalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SignalBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        SignalState signalState = be.getState();
        OverlayState overlayState = be.getOverlay();

        float renderTime = AnimationTickHolder.getRenderTime(be.getLevel());
        if (signalState.isRedLight(renderTime)) {
            CachedBuffers.partial(AllPartialModels.SIGNAL_ON, blockState)
                .light(LightTexture.FULL_BLOCK)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        } else {
            CachedBuffers.partial(AllPartialModels.SIGNAL_OFF, blockState)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }

        if (be.edgePoint == null) return;

        BlockPos pos = be.getBlockPos();
        TrackTargetingBehaviour<SignalBoundary> target = be.edgePoint;
        BlockPos targetPosition = target.getGlobalPosition();
        Level level = be.getLevel();
        BlockState trackState = level.getBlockState(targetPosition);
        Block block = trackState.getBlock();

        if (!(block instanceof ITrackBlock)) return;
        if (overlayState == OverlayState.SKIP) return;

        ms.pushPose();
        dev.engine_room.flywheel.lib.transform.TransformStack.of(ms)
            .translate(targetPosition.subtract(pos));
        RenderedTrackOverlayType type = overlayState == OverlayState.DUAL
            ? RenderedTrackOverlayType.DUAL_SIGNAL
            : RenderedTrackOverlayType.SIGNAL;
        TrackTargetingBehaviour.render(level, targetPosition, target.getTargetDirection(),
            target.getTargetBezier(), ms, buffer, light, overlay, type, 1);
        ms.popPose();
    }
}
