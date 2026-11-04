package top.hibermod.ctdhost.block;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 把 CTD 的方块放进创造模式物品栏。
 *
 * <p>不挂事件的话，方块只有在 `/give` 或物品注册表里能找到，
 * 创造模式物品栏里是看不到的——这是 NeoForge 的默认行为。
 */
public final class CtdCreativeTab {

    private CtdCreativeTab() {}

    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(CtdBlocks.SWITCH_MACHINE_ITEM.get());
            event.accept(CtdBlocks.CTD_SIGNAL_ITEM.get());
            event.accept(CtdBlocks.SIGNAL_HEAD_ITEM.get());
        }
        // 屏蔽门属于建筑方块
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(CtdBlocks.PSD_DOOR_ITEM.get());
        }
    }
}