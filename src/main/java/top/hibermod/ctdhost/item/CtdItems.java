package top.hibermod.ctdhost.item;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.world.item.Item;

import top.hibermod.ctdhost.CtdHost;

/** 物品注册。宿主允许注册物品（与 API 层相反，API 层连 assets 都不许有）。 */
public final class CtdItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CtdHost.MOD_ID);

    /** 脚本时刻表。 */
    public static final DeferredItem<ScriptTimetableItem> SCRIPT_TIMETABLE =
        ITEMS.registerItem("script_timetable", ScriptTimetableItem::new, new Item.Properties());

    private CtdItems() {}
}
