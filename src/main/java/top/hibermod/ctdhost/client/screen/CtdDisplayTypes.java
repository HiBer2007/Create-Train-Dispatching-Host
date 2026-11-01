package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.crn.block.properties.EDisplayType;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayProperties;

/**
 * 把两个新的站台屏类型注册进 CRN。
 *
 * <h2>为什么在客户端 mixin 的静态初始化里注册</h2>
 * {@code AdvancedDisplaysRegistry} 与渲染类都在 {@code de.mrjulsen.crn.client} 包里，
 * 服务端不加载它们。
 *
 * <p>踩过的两个坑：
 * <ol>
 *   <li>{@code @EventBusSubscriber} 在 1.21.1 默认挂 **GAME 总线**，
 *       而 {@code FMLClientSetupEvent} 走 **MOD 总线**——监听器从来没被调用过；</li>
 *   <li>在公共主类里用 {@code Class.forName} + 反射挂载也没有生效。</li>
 * </ol>
 *
 * <p>最终选了一个**不可能漏掉**的挂载点：客户端 mixin（{@code NixieTubeRendererMixin}，
 * 只注册在 {@code ctdhost.mixins.json} 的 {@code client} 段）的静态初始化。
 * 它只在客户端加载，而且比任何 setup 事件都早。
 */
public final class CtdDisplayTypes {

    private CtdDisplayTypes() {}

    private static boolean registered;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ctdhost");

    /** 直接注册（不依赖任何事件总线）。由客户端 mixin 的静态初始化调用。 */
    public static synchronized void registerAll() {
        if (registered) return;
        registered = true;

'        // 灯头的 BER 也在这里注册。
        // 注解那条路（@EventBusSubscriber / @Mod(dist=CLIENT) / 反射）在这个会话里
        // 全都「没被调用过」，而这条路径已经被站台屏证明会执行。
        try {
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                top.hibermod.ctdhost.block.CtdBlocks.SIGNAL_HEAD_BE.get(),
                top.hibermod.ctdhost.client.CtdSignalHeadRenderer::new);
            LOG.info("ctdhost: 灯头渲染器已注册");
        } catch (Throwable t) {
            LOG.error("ctdhost: 灯头渲染器注册失败", t);
        }

        // 顺带把辉光模型登记上'：它们必须在模型烘焙之前进入 Catnip 的静态表。
        top.hibermod.ctdhost.client.CtdPartialModels.bootstrap();

        try {
            AdvancedDisplaysRegistry.register(
                EDisplayType.PLATFORM, "ctd_platform",
                CtdPlatformSettings::new, BERCtdPlatform::new,
                new DisplayProperties(false, be -> 3));

            AdvancedDisplaysRegistry.register(
                EDisplayType.PLATFORM, "ctd_departure",
                CtdPlatformSettings::new, BERCtdDeparture::new,
                new DisplayProperties(true, be -> 1));

            LOG.info("ctdhost: 站台屏类型注册成功 ctd_platform / ctd_departure");
        } catch (Throwable t) {
            LOG.error("ctdhost: 站台屏类型注册失败", t);
            top.hibermod.ctdhost.Failures.caught("站台屏类型注册", t);
        }
    }
}
