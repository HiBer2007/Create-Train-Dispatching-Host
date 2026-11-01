package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.crn.block.properties.EDisplayType;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 把两个新的站台屏类型注册进 CRN。
 *
 * <h2>为什么必须在客户端</h2>
 * {@code AdvancedDisplaysRegistry} 在 {@code de.mrjulsen.crn.client} 包里，
 * 渲染类也一样。服务端不加载这些类，所以注册只能发生在客户端初始化。
 *
 * <h2>两个类型</h2>
 * <ul>
 *   <li>{@code ctd_platform} —— 站台屏：车站 + 站台 + 显示内容；</li>
 *   <li>{@code ctd_departure} —— 发车指示器：带颜色的距正点计时。</li>
 * </ul>
 */
@EventBusSubscriber(modid = top.hibermod.ctdhost.CtdHost.MOD_ID, value = Dist.CLIENT)
public final class CtdDisplayTypes {

    private CtdDisplayTypes() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // CRN 的注册表是静态的；重复注册同名类型会抛异常，所以只在客户端初始化时注册一次。
        event.enqueueWork(() -> {
            try {
                AdvancedDisplaysRegistry.register(
                    EDisplayType.PLATFORM, "ctd_platform",
                    CtdPlatformSettings::new, BERCtdPlatform::new,
                    new DisplayProperties(false, be -> 3));

                AdvancedDisplaysRegistry.register(
                    EDisplayType.PLATFORM, "ctd_departure",
                    CtdPlatformSettings::new, BERCtdDeparture::new,
                    new DisplayProperties(true, be -> 1));
            } catch (Throwable t) {
                // 注册失败不该让客户端起不来；但必须可见。
                System.out.println("[ctdhost] 站台屏类型注册失败: " + t);
                top.hibermod.ctdhost.Failures.caught("站台屏类型注册", t);
            }
        });
    }
}
