package top.hibermod.ctdhost.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalRenderer;
import com.simibubi.create.foundation.render.RenderTypes;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 信号机**自己那颗灯**按我们的语义上色。
 *
 * <h2>为什么两个渲染注入都要有</h2>
 * 游戏里「信号机的显示」可能来自两处：信号机自带的指示灯，以及贴在上面的辉光管。
 * 只改一处，另一处仍是原生颜色，看上去就还是「没反应」——
 * 两边都接管，才能保证切换归属后看到的一定是新语义。
 *
 * <h2>为什么染色而不是重画几何</h2>
 * 灯的位置、形状、亮度都在 Create 的模型里。只换颜色，几何不动，
 * 观感不会跑偏，也不会和原版方块叠出重影。
 */
@Mixin(SignalRenderer.class)
public abstract class SignalRendererMixin {

    @Inject(method = "renderSafe", at = @At("HEAD"), cancellable = true, require = 1,
            remap = false)
    private void ctd$tintSignalLamp(SignalBlockEntity be, float partialTicks, PoseStack ms,
                                    MultiBufferSource buffer, int light, int overlay,
                                    CallbackInfo ci) {
        try {
            if (be.edgePoint == null || be.edgePoint.getEdgePoint() == null) return;

            String aspect = top.hibermod.ctdapi.status.SignalStates
                .get(be.edgePoint.getEdgePoint().id);
            if (aspect.isEmpty()) return;                  // 不归我们管：原样

            int argb = ctd$primaryColor(aspect);
            if (argb == 0) {
                ci.cancel();                               // 灭灯：连原生的一起不画
                return;
            }

            BlockState blockState = ((net.minecraft.world.level.block.entity.BlockEntity) be)
                .getBlockState();
            CachedBuffers.partial(AllPartialModels.SIGNAL_ON, blockState)
                .light(LightTexture.FULL_BLOCK)
                .disableDiffuse()
                .color(0xFF000000 | (argb & 0x00FFFFFF))
                .renderInto(ms, buffer.getBuffer(RenderTypes.additive()));

            ci.cancel();
        } catch (Throwable ignored) {
            // 出错就交回原版：客户端不能因为一盏灯崩掉
        }
    }

    /** 语义 → 这架信号机主灯位的颜色（0 = 不亮）。 */
    private static int ctd$primaryColor(String aspect) {
        String path = aspect.contains(":") ? aspect.substring(aspect.indexOf(':') + 1) : aspect;
        return switch (path) {
            case "clear" -> 0xFF30D158;
            case "approach_limited" -> 0xFFFFD60A;
            case "approach" -> 0xFFFF9F0A;
            case "caution_diverging" -> 0xFFFFD60A;
            case "stop" -> 0xFFFF453A;
            case "call_on" -> 0xFFB06010;
            case "cbtc_mode" -> 0xFF0A84FF;
            case "shunt" -> 0xFFFFFFFF;
            case "dark" -> 0;
            default -> 0;
        };
    }
}
