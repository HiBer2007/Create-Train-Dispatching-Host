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
            event.accept(CtdBlocks.PSD_DOOR_ITEM.get());
        }
        // 屏蔽门放进 CTD 自己的分页（ctdscript 注册的标签页，按命名空间匹配避免编译期依赖）
        if (event.getTabKey().location().getNamespace().equals("ctdscript")) {
            event.accept(CtdBlocks.PSD_DOOR_ITEM.get());
        }
    }
}