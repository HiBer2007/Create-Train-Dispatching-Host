package top.hibermod.ctdhost.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import top.hibermod.ctdhost.CtdHost;
import top.hibermod.ctdhost.block.CtdBlocks;

/**
 * 客户端渲染器注册。
 *
 * <p>注意 {@code bus = EventBusSubscriber.Bus.MOD}：{@code EntityRenderersEvent}
 * 走 MOD 总线，而 {@code @EventBusSubscriber} 在 1.21.1 默认挂 GAME 总线——
 * 漏掉这个参数就会「注册代码从来没被调用过」，前面已经踩过一次。
 */
@EventBusSubscriber(modid = CtdHost.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CtdRenderers {

    private CtdRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CtdBlocks.SIGNAL_HEAD_BE.get(), CtdSignalHeadRenderer::new);
    }
}