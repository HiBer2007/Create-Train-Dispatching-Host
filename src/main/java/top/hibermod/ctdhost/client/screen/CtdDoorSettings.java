package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.mcdragonlib.util.DLColor;

/** 车门外侧终到牌：深蓝底白字。 */
public class CtdDoorSettings extends CtdPlatformSettings {
    public CtdDoorSettings() {
        setBackColor(DLColor.fromInt(0xFF0D2137));
        setFontColor(DLColor.fromInt(0xFFFFFFFF));
    }
}