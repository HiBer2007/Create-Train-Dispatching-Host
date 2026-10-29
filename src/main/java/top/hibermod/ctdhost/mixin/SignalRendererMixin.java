package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalRenderer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * 在信号机顶部画我们自己算出来的灯。
 *
 * <h2>为什么要画在顶部</h2>
 * Create 的信号机本体只有一个红/灭的灯，位置固定在机身里。
 * 那套语义（闭塞是否被占用）表达不了双灯组合，而机身模型我们改不了——
 * 所以把组合灯画在**顶端**：既不动它的模型，也不和它的灯抢位置。
 *
 * <h2>颜色从哪来</h2>
 * 内核写入语义 id（{@code ctd:approach} 等），服务端同步到客户端的
 * {@code SignalStates}。这里只做「语义 → 两个灯位的颜色」的翻译，
 * 不参与任何判定。
 */
@Mixin(SignalRenderer.class)
public abstract class SignalRendererMixin {

    /** 灯位相对方块顶面的偏移：两个灯位左右并排。 */
    private static final float LAMP_Y = 1.02f;
    private static final float LAMP_SIZE = 0.16f;
    private static final float LAMP_GAP = 0.02f;

    @Inject(method = "renderSafe", at = @At("TAIL"))
    private void ctd$drawComboLamps(SignalBlockEntity be, float partialTicks, PoseStack ms,
                                    MultiBufferSource buffer, int light, int overlay,
                                    CallbackInfo ci) {
        try {
            if (!(be.edgePoint != null) || be.edgePoint.getEdgePoint() == null) return;

            java.util.UUID id = be.edgePoint.getEdgePoint().id;
            String aspect = top.hibermod.ctdapi.status.SignalStates.get(id);
            if (aspect.isEmpty()) return;

            int[] colors = ctd$colorsOf(aspect);
            if (colors == null) return;

            VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
            PoseStack.Pose pose = ms.last();

            float cx = 0.5f;
            float half = LAMP_SIZE / 2f;
            float leftX = cx - half - LAMP_GAP;
            float rightX = cx + LAMP_GAP;

            // 上灯位 = colors[0]，下灯位 = colors[1]；0 表示这个灯位不亮
            if (colors[0] != 0) ctd$quad(vc, pose, leftX, LAMP_Y, cx, half, colors[0]);
            if (colors[1] != 0) ctd$quad(vc, pose, rightX, LAMP_Y, cx, half, colors[1]);
        } catch (Throwable ignored) {
            // 渲染异常绝不能把客户端拖垮：宁可少画两个灯
        }
    }

    /** 语义 id → 两个灯位的 ARGB（0 = 不亮）。 */
    private static int[] ctd$colorsOf(String aspect) {
        String path = aspect.contains(":") ? aspect.substring(aspect.indexOf(':') + 1) : aspect;
        return switch (path) {
            case "clear" -> new int[] { 0xFF30D158, 0xFF30D158 };            // 绿-绿
            case "approach_limited" -> new int[] { 0xFF30D158, 0xFFFFD60A };  // 绿-黄
            case "approach" -> new int[] { 0xFF30D158, 0xFFFF453A };          // 绿-红
            case "caution_diverging" -> new int[] { 0xFFFFD60A, 0xFFFFD60A }; // 黄-黄
            case "stop" -> new int[] { 0xFFFF453A, 0xFFFF453A };              // 红-红
            case "call_on" -> new int[] { 0xFFFF453A, 0xFFFFD60A };           // 红-黄
            case "cbtc_mode" -> new int[] { 0xFF0A84FF, 0 };                  // 蓝：模式标识
            case "shunt" -> new int[] { 0xFFFFFFFF, 0 };                      // 白：调车
            case "dark" -> null;                                              // 灭灯
            default -> null;
        };
    }

    /** 画一块朝上的小方片。 */
    private static void ctd$quad(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                                 float half, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        float x0 = x - half;
        float x1 = x + half;
        float z0 = z - half;
        float z1 = z + half;

        vc.addVertex(pose, new org.joml.Vector3f(x0, y, z0)).setColor(r, g, b, a);
        vc.addVertex(pose, new org.joml.Vector3f(x1, y, z0)).setColor(r, g, b, a);
        vc.addVertex(pose, new org.joml.Vector3f(x1, y, z1)).setColor(r, g, b, a);
        vc.addVertex(pose, new org.joml.Vector3f(x0, y, z1)).setColor(r, g, b, a);
    }
}
