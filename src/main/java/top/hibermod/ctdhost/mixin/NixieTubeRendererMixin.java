package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeRenderer;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.foundation.render.RenderTypes;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 让信号机上那根辉光管显示**我们的语义**，而不是 Create 的红/非红。
 *
 * <h2>为什么这里才是对的注入点</h2>
 * 信号机的显示就是这根管子：管子亮什么颜色，司机就读到什么。
 * 早先的做法是在方块顶上另画两个色块——那不是信号机，那是贴在信号机上的便签。
 *
 * <h2>颜色从哪来</h2>
 * Create 提供的管子模型只有红、黄、白三种。绿与蓝靠给**白管**染色实现：
 * 白管是中性玻璃，染色后读起来仍然是「一根发光的管」，
 * 这与贴着模型另加几何体是两回事。
 *
 * <h2>两个管位</h2>
 * Create 的信号显示本来就是两个管位（第一根红、第二根黄/白），
 * 双灯组合正好落在两个管位上，不需要扩孔。
 */
@Mixin(NixieTubeRenderer.class)
public abstract class NixieTubeRendererMixin {

    private static final float GLOW_VIEW_DISTANCE = 24.0f;

    @Inject(method = "renderSafe", at = @At("HEAD"), cancellable = true)
    private void ctd$driveTubes(NixieTubeBlockEntity be, float partialTicks, PoseStack ms,
                                MultiBufferSource buffer, int light, int overlay,
                                CallbackInfo ci) {
        try {
            if (be.signalState == null) return;               // 不是信号显示用的管子

            SignalBlockEntity signal = ctd$signalOf(be);
            if (signal == null || signal.edgePoint == null
                || signal.edgePoint.getEdgePoint() == null) {
                return;
            }

            java.util.UUID id = signal.edgePoint.getEdgePoint().id;
            String aspect = top.hibermod.ctdapi.status.SignalStates.get(id);
            if (aspect.isEmpty()) return;                     // 我们没管这架：交回 Create

            int[] colors = ctd$colorsOf(aspect);
            if (colors == null) {
                ci.cancel();                                  // 灭灯：连 Create 的也不画
                return;
            }

            ctd$render(be, ms, buffer, colors);
            ci.cancel();
        } catch (Throwable ignored) {
            // 渲染异常不能拖垮客户端：出错就交回原版
        }
    }

    private static void ctd$render(NixieTubeBlockEntity be, PoseStack ms,
                                   MultiBufferSource buffer, int[] colors) {
        BlockState blockState = ((net.minecraft.world.level.block.entity.BlockEntity) be)
            .getBlockState();

        ms.pushPose();
        ms.translate(1 / 2f, 7.5f / 16f, 1 / 2f);

        for (int slot = 0; slot < 2; slot++) {
            int argb = colors[slot];
            if (argb == 0) continue;                          // 这个管位不亮

            ms.pushPose();
            ms.translate(slot == 0 ? -4 / 16f : 4 / 16f, 0, 0);

            boolean warm = ctd$isWarm(argb);
            int tint = ctd$tint(argb);

            // 辉光层：先用对应颜色的 glow，再给管身上色
            dev.engine_room.flywheel.lib.model.baked.PartialModel glow =
                warm ? AllPartialModels.SIGNAL_YELLOW_GLOW : AllPartialModels.SIGNAL_WHITE_GLOW;
            dev.engine_room.flywheel.lib.model.baked.PartialModel tube =
                warm ? AllPartialModels.SIGNAL_YELLOW : AllPartialModels.SIGNAL_WHITE;

            CachedBuffers
                .partial(glow, blockState)
                .light(LightTexture.FULL_BLOCK)
                .disableDiffuse()
                .color(tint)
                .scale(2, 5.125f, 2)
                .renderInto(ms, buffer.getBuffer(RenderTypes.additive()));

            CachedBuffers
                .partial(tube, blockState)
                .light(LightTexture.FULL_BLOCK)
                .disableDiffuse()
                .color(tint)
                .scale(1 + 1 / 16f)
                .renderInto(ms, buffer.getBuffer(RenderTypes.additive()));

            ms.popPose();
        }

        ms.popPose();
    }

    /** 管子选型：红只用于红，其余用黄或白再染色。 */
    private static boolean ctd$isWarm(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return r > 180 && g > 140 && b < 120;                // 偏黄的用黄管
    }

    /** 染色值：红管/黄管本身就是那个颜色，白管按语义上色。 */
    private static int ctd$tint(int argb) {
        return 0xFFFFFFFF | (argb & 0x00FFFFFF);
    }

    /** 语义 id → 两个管位的 ARGB（0 = 该管位不亮）。 */
    private static int[] ctd$colorsOf(String aspect) {
        String path = aspect.contains(":") ? aspect.substring(aspect.indexOf(':') + 1) : aspect;
        return switch (path) {
            case "clear" -> new int[] { 0xFFFFFFFF, 0xFF30D158 };
            case "approach_limited" -> new int[] { 0xFFFFFFFF, 0xFFFFD60A };
            case "approach" -> new int[] { 0xFFFFFFFF, 0xFFFF453A };
            case "caution_diverging" -> new int[] { 0xFFFFFFFF, 0xFFFFD60A };
            case "stop" -> new int[] { 0xFFFF453A, 0xFFFF453A };
            case "call_on" -> new int[] { 0xFFFF453A, 0xFFFFD60A };
            case "cbtc_mode" -> new int[] { 0xFF0A84FF, 0 };
            case "shunt" -> new int[] { 0xFFFFFFFF, 0 };
            case "dark" -> null;
            default -> null;
        };
    }

    /**
     * 从管子反查它所显示的那架信号机。
     *
     * <p>{@code cachedSignalTE} 是 Create 内部的弱引用，这里用反射读：
     * 为一个字段开 accessor mixin 不划算，而反射失败只会退回原版显示。
     */
    private static SignalBlockEntity ctd$signalOf(NixieTubeBlockEntity be) {
        try {
            Object ref = NixieTubeBlockEntity.class
                .getDeclaredField("cachedSignalTE").get(be);
            if (ref instanceof java.lang.ref.WeakReference<?> weak) {
                Object value = weak.get();
                return value instanceof SignalBlockEntity signal ? signal : null;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
