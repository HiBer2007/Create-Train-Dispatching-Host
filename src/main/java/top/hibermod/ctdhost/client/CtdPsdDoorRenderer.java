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
 * 滑动门渲染。固定门由 blockstate 模型直接画，这里只负责滑动门：
 *
 * <pre>
 *   z: 0 ─ 1/4 ─ 1/2 ─ 3/4 ─ 1
 *      [ 1 ][ 2 ][ 3 ][ 4 ]
 *       固定  滑动  滑动  固定
 * </pre>
 *
 * 门扇模型只有一套（16px 宽、z 0–4、1 格高按 height 缩放），
 * 左扇放 z 0.25、右扇放 z 0.5；开门时各向外平移「自身宽度」一格。
 */
public class CtdPsdDoorRenderer implements BlockEntityRenderer<CtdPsdDoorBlockEntity> {

    private static final ResourceLocation PANEL_H3 =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "block/psd_panel_h3");
    private static final ResourceLocation PANEL_H4 =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "block/psd_panel_h4");
    private static final ResourceLocation PANEL_H6 =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "block/psd_panel_h6");

    public CtdPsdDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CtdPsdDoorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CtdPsdDoorBlock block) || !block.isSliding()) return;

        BakedModel panel = panelFor(block.height());
        if (panel == null) return;

        float leaf = block.width() / 2f;          // 单扇宽（格）
        float slide = leaf * be.progress();       // 开门滑出距离 = 自身宽度
        float scaleY = block.height() / 2f;       // 模型按 2 格高写

        // 左扇 z 0.25–0.5，右扇 z 0.5–0.75
        draw(panel, state, pose, buffer, light, overlay, -slide, 0.25f, leaf, scaleY);
        draw(panel, state, pose, buffer, light, overlay, leaf + slide, 0.5f, leaf, scaleY);
    }

    private static BakedModel panelFor(float height) {
        ResourceLocation id = height <= 1.6f ? PANEL_H3
            : height >= 2.5f ? PANEL_H6 : PANEL_H4;
        return Minecraft.getInstance().getModelManager()
            .getModel(new ModelResourceLocation(id, "standalone"));
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
