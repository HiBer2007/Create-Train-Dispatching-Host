package top.hibermod.ctdhost.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import top.hibermod.ctdhost.block.CtdBlocks;
import top.hibermod.ctdhost.block.CtdPsdDoorBlock;
import top.hibermod.ctdhost.block.CtdPsdDoorBlockEntity;

/**
 * 滑动门渲染，做法照机械动力的 {@code SlidingDoorRenderer}：
 * 用 {@link CachedBuffers#block(BlockState)} 取到已经烘焙好的门扇模型，
 * 加上平移偏移后 {@code renderInto}，而不是自己去 ModelManager 里翻模型。
 *
 * <p>方块本体在 blockstate 里始终是空模型，所以这里画的就是唯一的门扇副本：
 * <ul>
 *   <li>左扇：沿门宽方向 −leaf×progress，厚度方向 +0.25</li>
 *   <li>右扇：沿门宽方向 +leaf+leaf×progress，厚度方向 +0.5</li>
 * </ul>
 */
public class CtdPsdDoorRenderer extends SafeBlockEntityRenderer<CtdPsdDoorBlockEntity> {

    public CtdPsdDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    protected void renderSafe(CtdPsdDoorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CtdPsdDoorBlock block) || !block.isSliding()) return;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction widthAxis = facing.getClockWise();
        // 双开门：两扇对称地向两侧滑开，不需要镜像

        float leaf = block.width() / 2f;
        float slide = leaf * be.progress();

        VertexConsumer vb = buffer.getBuffer(RenderType.translucent());
        draw(be, facing, widthAxis, ms, vb, light, -slide, 0.25f, leaf);
        draw(be, facing, widthAxis, ms, vb, light, leaf + slide, 0.5f, leaf);
    }

    private void draw(CtdPsdDoorBlockEntity be, Direction facing, Direction widthAxis,
                      PoseStack ms, VertexConsumer vb, int light,
                      float along, float across, float leaf) {

        SuperByteBuffer buf = CachedBuffers.block(staticStateFor(be, facing));
        if (leaf != 1f) {
            buf.scale(leaf, 1f, 1f);
        }
        Vec3 offset = Vec3.atLowerCornerOf(widthAxis.getNormal()).scale(along)
            .add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(across));

        buf.translate(offset)
            .light(light)
            .renderInto(ms, vb);
    }

    /** 用同高度的固定门方块取静态模型（两者模型完全一致）。 */
    private static BlockState staticStateFor(CtdPsdDoorBlockEntity be, Direction facing) {
        float h = be.height();
        BlockState base = h <= 1.6f ? CtdBlocks.PSD_FIXED_1_5.get().defaultBlockState()
            : h >= 2.5f ? CtdBlocks.PSD_FIXED_3.get().defaultBlockState()
            : CtdBlocks.PSD_FIXED_2.get().defaultBlockState();
        return base.setValue(HorizontalDirectionalBlock.FACING, facing);
    }
}
