package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.mcdragonlib.util.DLColor;

/** 走字屏：黑底橙红字。 */
public class CtdScrollSettings extends CtdPlatformSettings {
    public CtdScrollSettings() {
        setBackColor(DLColor.fromInt(0xFF101010));
        setFontColor(DLColor.fromInt(0xFFFF7043));
    }
}