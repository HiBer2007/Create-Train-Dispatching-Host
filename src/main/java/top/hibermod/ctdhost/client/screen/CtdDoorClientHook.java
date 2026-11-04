package top.hibermod.ctdhost.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 客户端专用入口。**必须用反射从公共代码调用**：
 * 直接引用这个类会让专用服务器在类加载时就尝试解析
 * {@code net.minecraft.client.gui.screens.Screen}，触发 DistCleaner 报错。
 */
public final class CtdDoorClientHook {

    private CtdDoorClientHook() {}

    public static void open(BlockPos pos, String station, String platform, String doorId,
                            float speed, float height, boolean testMode) {
        Minecraft.getInstance().setScreen(
            new CtdDoorScreen(pos, station, platform, doorId, speed, height, testMode));
    }
}
