package top.hibermod.ctdhost.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

import top.hibermod.ctdhost.block.CtdBlocks;

/**
 * 客户端专用注册。
 *
 * <p>**必须在客户端初始化事件里做**：{@code ItemBlockRenderTypes.setRenderLayer}
 * 在渲染线程才调用会抛
 * {@code IllegalStateException: Render layers can only be set during client setup}，
 * 之前把「设渲染层」和「注册 BER」写在同一个 try 里，前者一抛，
 * 后面的 BER 注册就永远没执行——滑动门因此什么都不画。
 */
public final class CtdClientEvents {

    private CtdClientEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CtdClientEvents::onClientSetup);
        modBus.addListener(CtdClientEvents::onRegisterRenderers);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var item : CtdBlocks.psdItems()) {
                ItemBlockRenderTypes.setRenderLayer(item.get().getBlock(), RenderType.translucent());
            }
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CtdBlocks.PSD_DOOR_BE.get(), CtdPsdDoorRenderer::new);
    }
}
