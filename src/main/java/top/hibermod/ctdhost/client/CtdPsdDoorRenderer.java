package top.hibermod.ctdhost.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import top.hibermod.ctdhost.block.CtdPsdDoorBlock;
import top.hibermod.ctdhost.block.CtdPsdDoorBlockEntity;

/**
 * 滑动门动画：整樘门在 blockstate 里是空模型，真正的门扇由这里画。
 *
 * <p>左右两扇各是一个独立的 baked model（16px 宽、1/4 厚），
 * 开门时分别沿门宽方向平移 `progress × 16px`，滑进相邻固定门那格。
 */
public class CtdPsdDoorRenderer implements BlockEntityRenderer<CtdPsdDoorBlockEntity> {

    private static final ResourceLocation LEFT =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "psd_panel_left");
    private static final ResourceLocation RIGHT =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "psd_panel_right");

    public CtdPsdDoorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CtdPsdDoorBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (state.getValue(CtdPsdDoorBlock.TYPE) != CtdPsdDoorBlock.Type.SLIDING) return;

        var models = Minecraft.getInstance().getModelManager();
        BakedModel left = models.getModel(new net.minecraft.client.resources.model.ModelResourceLocation(LEFT, "standalone"));
        BakedModel right = models.getModel(new net.minecraft.client.resources.model.ModelResourceLocation(RIGHT, "standalone"));
        if (left == null || right == null) return;

        float progress = be.progress();

        // 关：左扇 x 0–1 格、右扇 x 1–2 格；开：各再向外平移一格，滑进两侧固定门那格
        renderPanel(left, state, pose, buffer, light, overlay, -progress);
        renderPanel(right, state, pose, buffer, light, overlay, 1f + progress);
    }

    private void renderPanel(BakedModel model, BlockState state, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay, float shiftX) {
        pose.pushPose();
        pose.translate(shiftX, 0f, 0f);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            pose.last(), buffer.getBuffer(RenderType.translucent()), state, model,
            1f, 1f, 1f, light, overlay,
            net.neoforged.neoforge.client.model.data.ModelData.EMPTY,
            RenderType.translucent());
        pose.popPose();
    }
}
