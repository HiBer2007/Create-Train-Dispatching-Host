package top.hibermod.ctdhost.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import top.hibermod.ctdhost.block.CtdPsdDoorBlock;
import top.hibermod.ctdhost.block.CtdPsdDoorBlockEntity;

/**
 * 屏蔽门渲染。方块本体是空模型，门扇全部由这里画：
 *
 * <ul>
 *   <li><b>固定门</b>：一片门扇贴在方块边缘（z 0–0.25），不参与动画</li>
 *   <li><b>滑动门</b>：左右两片贴在方块中间（z 0.25–0.5 / 0.5–0.75），
 *       开门时各向外平移「自身宽度」，滑进相邻固定门那格</li>
 * </ul>
 *
 * <p>门扇模型只有一套（1 格宽），宽度用 X 缩放、高度用 Y 缩放适配
 * 1x1.5 / 1x2 / 1x3 与 2x1.5 / 2x2 / 2x3 / 3x3 七种规格。
 */
public class CtdPsdDoorRenderer implements BlockEntityRenderer<CtdPsdDoorBlockEntity> {

    private static final ResourceLocation PANEL_H3 =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "psd_panel_h3");
    private static final ResourceLocation PANEL_H4 =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "psd_panel_h4");
    private static final ResourceLocation PANEL_H6 =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "psd_panel_h6");

    public CtdPsdDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CtdPsdDoorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();
        BakedModel panel = panelFor(be.height());
        if (panel == null) return;

        boolean fixed = state.getValue(CtdPsdDoorBlock.TYPE) == CtdPsdDoorBlock.Type.FIXED;
        float progress = fixed ? 0f : be.progress();

        if (fixed) {
            // 固定门：一片，贴方块边缘那一层（z 0–0.25），宽度 1 格
            draw(panel, state, pose, buffer, light, overlay, 0f, -0.25f, 1f, 1f);
            return;
        }

        float scaleX = be.width() / 2f;       // 2 宽 = 1，3 宽 = 1.5
        float scaleY = be.height() / 2f;
        float slide = scaleX * progress;      // 滑开距离 = 自身宽度

        // 左扇：z 0.25–0.5；右扇：z 0.5–0.75
        draw(panel, state, pose, buffer, light, overlay, -slide, 0f, scaleX, scaleY);
        draw(panel, state, pose, buffer, light, overlay, scaleX + slide, 0.25f, scaleX, scaleY);
    }

    private static BakedModel panelFor(float height) {
        ResourceLocation id = height <= 1.6f ? PANEL_H3
            : height >= 2.5f ? PANEL_H6 : PANEL_H4;
        BakedModel model = Minecraft.getInstance().getModelManager()
            .getModel(new ModelResourceLocation(id, "standalone"));
        return model;
    }

    private void draw(BakedModel model, BlockState state, PoseStack pose,
                      MultiBufferSource buffer, int light, int overlay,
                      float dx, float dz, float scaleX, float scaleY) {
        pose.pushPose();
        pose.translate(dx, 0f, dz);
        pose.scale(scaleX, scaleY, 1f);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            pose.last(), buffer.getBuffer(RenderType.translucent()), state, model,
            1f, 1f, 1f, light, overlay,
            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
            RenderType.translucent());
        pose.popPose();
    }
}
