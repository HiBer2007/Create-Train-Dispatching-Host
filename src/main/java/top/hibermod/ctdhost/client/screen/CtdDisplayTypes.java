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
        LOG.info("ctdhost: registerAll start (registered={})", registered);
        if (registered) return;
        registered = true;

        // 注解那条路（@EventBusSubscriber / @Mod(dist=CLIENT) / 反射）在这个会话里
        // 全都「没被调用过」，而这条路径已经被站台屏证明会执行。

        // 顺带把辉光模型登记上：它们必须在模型烘焙之前进入 Catnip 的静态表。
        // 模型登记只在客户端做（服务端没有模型烘焙）。
        if (isClient()) {
            try { top.hibermod.ctdhost.client.CtdPartialModels.bootstrap(); } catch (Throwable ignored) { }
        }

        try {
            AdvancedDisplaysRegistry.register(
                EDisplayType.PLATFORM, "ctd_platform",
                CtdPlatformSettings::new, rendererOf("top.hibermod.ctdhost.client.screen.BERCtdPlatform"),
                new DisplayProperties(false, be -> be.getYSize() * 3 - 1));

            AdvancedDisplaysRegistry.register(
                EDisplayType.PLATFORM, "ctd_departure",
                CtdPlatformSettings::new, rendererOf("top.hibermod.ctdhost.client.screen.BERCtdDeparture"),
                new DisplayProperties(false, be -> be.getYSize() * 3 - 1));

            // 新增：正点计时器（替代辉光管）与扣车指示。
            // 两者都绑定「车站 ID + 站台 ID」，内容来自 getStops()，与 ctd_platform 同源。
            AdvancedDisplaysRegistry.register(
                EDisplayType.PLATFORM, "ctd_timer",
                CtdPlatformSettings::new, rendererOf("top.hibermod.ctdhost.client.screen.BERCtdDeparture"),
                new DisplayProperties(false, be -> be.getYSize() * 3 - 1));

            AdvancedDisplaysRegistry.register(
                EDisplayType.PLATFORM, "ctd_hold",
                CtdPlatformSettings::new, rendererOf("top.hibermod.ctdhost.client.screen.BERCtdPlatform"),
                new DisplayProperties(false, be -> be.getYSize() * 3 - 1));

            LOG.info("ctdhost: display types registered: ctd_platform / ctd_departure / ctd_timer / ctd_hold");
        } catch (Throwable t) {
            LOG.error("ctdhost: display type registration failed: {}", t.toString());
            top.hibermod.ctdhost.Failures.caught("站台屏类型注册", t);
        }
    }
    /**
     * 渲染器只在客户端实例化。
     *
     * <p>关键：**类型注册必须在两端都执行**。服务端要靠它把 NBT 还原成正确的
     * Settings 类（{@code AdvancedDisplaysRegistry.createSettings}），
     * 只在客户端注册会让服务端退回 {@code EmptyDisplaySettings}，
     * 我们写的字段在存档往返时全部丢失——这就是「填了保存再打开还是空」的根因。
     */
    @SuppressWarnings("unchecked")
    private static <R> java.util.function.Supplier<R> rendererOf(String className) {
        return () -> {
            if (!isClient()) return null;
            try {
                return (R) Class.forName(className).getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                LOG.warn("ctdhost: renderer {} unavailable: {}", className, t.toString());
                return null;
            }
        };
    }

    private static boolean isClient() {
        try {
            return net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
        } catch (Throwable t) {
            return false;
        }
    }

}