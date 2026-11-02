package top.hibermod.ctdhost.client;

/**
 * 灯头渲染器的懒注册。
 *
 * <p>灯现在其实由**方块模型**画，这个渲染器只作为兜底。注册时机有个坑：
 * 在 mod 构造期调用时 {@code SIGNAL_HEAD_BE} 这个 DeferredHolder **还没绑定**，
 * 会抛 {@code Trying to access unbound value}。所以先判断 {@code isBound()}，
 * 没绑定就直接返回、不标记完成，等下一次（方块被加载时）再试。
 */
public final class CtdHeadRenderers {

    private CtdHeadRenderers() {}

    private static boolean done;

    public static void ensureRegistered() {
        if (done) return;
        if (!top.hibermod.ctdhost.block.CtdBlocks.SIGNAL_HEAD_BE.isBound()) {
            return;                       // 还没注册完，下次再试
        }
        done = true;
        try {
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                top.hibermod.ctdhost.block.CtdBlocks.SIGNAL_HEAD_BE.get(),
                CtdSignalHeadRenderer::new);
            org.slf4j.LoggerFactory.getLogger("ctdhost").info("ctdhost: head renderer registered");
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("ctdhost").warn("ctdhost: head renderer failed: {}", t.toString());
        }
    }
}