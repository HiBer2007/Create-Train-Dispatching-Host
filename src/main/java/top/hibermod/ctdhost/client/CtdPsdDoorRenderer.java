package top.hibermod.ctdhost.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import top.hibermod.ctdhost.block.CtdBlocks;
import top.hibermod.ctdhost.block.CtdPsdDoorBlock;
import top.hibermod.ctdhost.block.CtdPsdDoorBlockEntity;

/**
 * 滑动门渲染：**每一格只画自己那一扇**。
 *
 * <p>门是多格结构，主格（{@code MAIN=true}）负责 tick 与进度同步，
 * 其余格在渲染时向主格要 {@code progress}。这样每格的渲染范围与它的碰撞箱
 * 落在同一格内，不会出现「半扇门画在空气方块里」。
 *
 * <h2>3 格宽（奇数）</h2>
 * 左扇 = 第 0 格整格；右扇 = 第 2 格整格；**中间格切成两片半宽**，
 * 左半并入左扇、右半并入右扇，接缝在中线（方案 A）。
 */
public class CtdPsdDoorRenderer extends SafeBlockEntityRenderer<CtdPsdDoorBlockEntity> {

    public CtdPsdDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    protected void renderSafe(CtdPsdDoorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CtdPsdDoorBlock block) || !block.isSliding()) return;

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction side = facing.getClockWise();

        // 找到主格：主格自己 tick，其余格读它的进度
        BlockPos mainPos = be.mainPos();
        if (mainPos == null) return;
        if (!(be.getLevel().getBlockEntity(mainPos) instanceof CtdPsdDoorBlockEntity main)) return;

        int row = state.getValue(CtdPsdDoorBlock.ROW);
        int cells = block.cells();
        float progress = net.minecraft.util.Mth.lerp(partialTicks, main.prevProgress(), main.progress());

        BlockPos pos = be.getBlockPos();
        int index = 0;
        for (int i = 0; i < cells; i++) {
            if (mainPos.relative(side, i).equals(pos)) {
                index = i;
                break;
            }
        }

        VertexConsumer vb = buffer.getBuffer(RenderType.translucent());

        // 每一格只画自己那一份：
        //   最左格 / 最右格 = 整格门扇
        //   中间格（奇数宽度才有）= 对开两片半宽，接缝在中线
        if (index == 0) {
            draw(block, facing, side, ms, vb, light, -progress, 0.25f, 1f, row);
        } else if (index == cells - 1) {
            draw(block, facing, side, ms, vb, light, progress, 0.25f, 1f, row);
        } else {
            // 中间格：两片半宽，厚度分层错开，避免共面 z-fighting（表现为闪烁/抽搐）
            draw(block, facing, side, ms, vb, light, -progress, 0.25f, 0.5f, row);
            draw(block, facing, side, ms, vb, light, 0.5f + progress, 0.5f, 0.5f, row);
        }
    }

    private static BlockPos findMain(CtdPsdDoorBlockEntity be, BlockState state, Direction side) {
        if (state.getValue(CtdPsdDoorBlock.MAIN)) return be.getBlockPos();
        for (int i = 1; i <= 3; i++) {
            BlockPos at = be.getBlockPos().relative(side, -i);
            BlockState s = be.getLevel().getBlockState(at);
            if (s.getBlock() == state.getBlock() && s.getValue(CtdPsdDoorBlock.MAIN)) {
                return at;
            }
        }
        return null;
    }

    private void draw(CtdPsdDoorBlock block, Direction facing, Direction side, PoseStack ms,
                      VertexConsumer vb, int light,
                      float along, float across, float widthScale, int row) {

        SuperByteBuffer buf = CachedBuffers.block(staticStateFor(block, facing, row));
        if (widthScale != 1f) buf.scale(widthScale, 1f, 1f);

        Vec3 offset = Vec3.atLowerCornerOf(side.getNormal()).scale(along)
            .add(Vec3.atLowerCornerOf(facing.getOpposite().getNormal()).scale(across));

        buf.translate(offset).light(light).renderInto(ms, vb);
    }

    private static BlockState staticStateFor(CtdPsdDoorBlock block, Direction facing, int row) {
        float h = block.height();
        BlockState base = h <= 1.6f ? CtdBlocks.PSD_FIXED_1_5.get().defaultBlockState()
            : h >= 2.5f ? CtdBlocks.PSD_FIXED_3.get().defaultBlockState()
            : CtdBlocks.PSD_FIXED_2.get().defaultBlockState();
        return base.setValue(HorizontalDirectionalBlock.FACING, facing)
            .setValue(CtdPsdDoorBlock.ROW, Math.max(0, Math.min(2, row)));
    }
}
