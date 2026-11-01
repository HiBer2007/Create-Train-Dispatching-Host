package top.hibermod.ctdhost.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

import top.hibermod.ctdhost.block.CtdSignalHeadBlockEntity;
import top.hibermod.ctdhost.execute.SignalHeadLamps;

/**
 * 灯头的渲染：两个自发光小方块，颜色由语义决定。
 *
 * <h2>为什么不用 Create 的辉光模型</h2>
 * 那条路已经证明不可靠（模型没被烘焙就洋红、状态还被 Create 抢）。
 * 这里只画**两个纯色发光面**，不依赖任何外部模型与贴图，
 * 因此不存在「缺失材质」这一类问题。
 *
 * <h2>颜色从哪来</h2>
 * {@link CtdSignalHeadBlockEntity#aspect()} 每 20 刻刷新一次，
 * 颜色表与原生管子共用 {@link SignalHeadLamps#colorsOf(String)}。
 */
public class CtdSignalHeadRenderer implements BlockEntityRenderer<CtdSignalHeadBlockEntity> {

    public CtdSignalHeadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CtdSignalHeadBlockEntity be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        int[] colors = SignalHeadLamps.colorsOf(be.aspect());
        if (colors == null) return;                     // 无信号 / 灭灯

        BlockState state = be.getBlockState();
        net.minecraft.core.Direction facing = state.getValue(
            top.hibermod.ctdhost.block.CtdSignalHeadBlock.FACING);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);

        // 把灯泡推到灯头**外表面之外**。
        // 第一版画在 ±0.12 的偏移上、缩放 0.22，结果整颗埋在灯头盒子里，
        // 屏幕上只看到一个黑方块——「画了但看不见」比「没画」更难查。
        ms.translate(facing.getOpposite().getStepX() * 0.34, 0, facing.getOpposite().getStepZ() * 0.34);

        for (int slot = 0; slot < 2; slot++) {
            int argb = colors[slot];
            if (argb == 0) continue;

            ms.pushPose();
            // 两个灯位上下叠放，对应色灯信号机的两个灯位
            ms.translate(0, slot == 0 ? 0.12 : -0.12, 0);
            ms.scale(0.26f, 0.20f, 0.26f);

            var consumer = buffer.getBuffer(net.minecraft.client.renderer.RenderType.translucent());
            drawCube(ms, consumer, argb);

            ms.popPose();
        }

        ms.popPose();
    }

    /** 画一个居中的单位立方体，六面同色。 */
    private static void drawCube(PoseStack ms, com.mojang.blaze3d.vertex.VertexConsumer consumer, int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        var pose = ms.last();
        float[][] corners = {
            { -0.5f, -0.5f, -0.5f }, { 0.5f, -0.5f, -0.5f }, { 0.5f, 0.5f, -0.5f }, { -0.5f, 0.5f, -0.5f },
            { -0.5f, -0.5f, 0.5f }, { 0.5f, -0.5f, 0.5f }, { 0.5f, 0.5f, 0.5f }, { -0.5f, 0.5f, 0.5f }
        };
        int[][] faces = {
            { 0, 1, 2, 3 }, { 5, 4, 7, 6 }, { 4, 0, 3, 7 },
            { 1, 5, 6, 2 }, { 3, 2, 6, 7 }, { 4, 5, 1, 0 }
        };

        for (int[] face : faces) {
            for (int idx : face) {
                float[] c = corners[idx];
                consumer.addVertex(pose, c[0], c[1], c[2]).setColor(r, g, b, a);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(CtdSignalHeadBlockEntity be) {
        return true;                                    // 高杆信号机很远也要画
    }
}
