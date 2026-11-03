package top.hibermod.ctdhost.block;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.display.LineColors;

/**
 * 内置线路色号。用 {@code ctd:lineN} 命名，内核可以用 {@code out.lineColor(...)} 覆盖或新增。
 */
public final class CtdLineColorDefaults {

    private CtdLineColorDefaults() {}

    public static void register() {
        put("line1", 0xFFF3D03E);
        put("line2", 0xFF00629B);
        put("line3", 0xFFECA154);
        put("line4", 0xFF00843D);
        put("line5", 0xFFD6001C);
        put("line6", 0xFF8E3A80);
        put("line7", 0xFF97D700);
        put("line8", 0xFF00AEC7);
        put("line9", 0xFF6BBBAE);
        put("line11", 0xFFC8D400);
        put("line14", 0xFF81312F);
        put("line18", 0xFF5B7DB1);
        put("line22", 0xFF7B5AA6);
        put("intercity", 0xFF9E9E9E);
    }

    private static void put(String name, int argb) {
        LineColors.register(ResourceLocation.fromNamespaceAndPath("ctd", name), argb);
    }
}