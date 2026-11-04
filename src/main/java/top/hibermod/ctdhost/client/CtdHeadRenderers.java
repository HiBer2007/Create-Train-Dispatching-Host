package top.hibermod.ctdhost.client;



import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * 方块实体渲染器的懒注册。
 *
 * <p>两个坑：
 * <ol>
 *   <li>mod 构造期 {@code DeferredHolder} 还没绑定，{@code get()} 会抛
 *       {@code Trying to access unbound value}，所以先 {@code isBound()}。</li>
 *   <li>我们自己的 {@code ctd_signal} 用的是自己的 BE 类型，Create 的
 *       {@code SignalRenderer} 只挂在它自己的 {@code TRACK_SIGNAL} 上，
 *       于是「轨道上那块信号板」不会画出来。这里把 Create 的渲染器
 *       复用到我们的 BE 类型上。</li>
 * </ol>
 */
public final class CtdHeadRenderers {

    private CtdHeadRenderers() {}

    private static boolean done;

    public static void ensureRegistered() {
        if (done) return;
        if (!top.hibermod.ctdhost.block.CtdBlocks.SIGNAL_HEAD_BE.isBound()) return;
        if (!top.hibermod.ctdhost.block.CtdBlocks.CTD_SIGNAL_BE.isBound()) return;
        done = true;

        var log = org.slf4j.LoggerFactory.getLogger("ctdhost");
        try {
            BlockEntityRenderers.register(
                top.hibermod.ctdhost.block.CtdBlocks.SIGNAL_HEAD_BE.get(),
                CtdSignalHeadRenderer::new);
            log.info("ctdhost: head renderer registered");
        } catch (Throwable t) {
            log.warn("ctdhost: head renderer failed: {}", t.toString());
        }

        try {
            // 复用 Create 的信号机渲染器，让 ctd_signal 在轨道上也显示信号板。
            BlockEntityRenderers.register(
                top.hibermod.ctdhost.block.CtdBlocks.CTD_SIGNAL_BE.get(),
                CtdSignalRenderer::new);
            log.info("ctdhost: signal block renderer registered");
        } catch (Throwable t) {
            log.warn("ctdhost: signal block renderer failed: {}", t.toString());
        }

        try {
            // 屏蔽门玻璃必须走半透明层，否则透明像素会被画成黑色
            if (top.hibermod.ctdhost.block.CtdBlocks.PSD_FIXED.isBound()) {
                var translucent = net.minecraft.client.renderer.RenderType.translucent();
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    top.hibermod.ctdhost.block.CtdBlocks.PSD_FIXED.get(), translucent);
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    top.hibermod.ctdhost.block.CtdBlocks.PSD_SLIDING.get(), translucent);
                log.info("ctdhost: psd door render layer registered");
            }
            if (top.hibermod.ctdhost.block.CtdBlocks.PSD_DOOR_BE.isBound()) {
                BlockEntityRenderers.register(
                    top.hibermod.ctdhost.block.CtdBlocks.PSD_DOOR_BE.get(),
                    CtdPsdDoorRenderer::new);
                log.info("ctdhost: psd door renderer registered");
            }
        } catch (Throwable t) {
            log.warn("ctdhost: psd door render layer failed: {}", t.toString());
        }
    }
}