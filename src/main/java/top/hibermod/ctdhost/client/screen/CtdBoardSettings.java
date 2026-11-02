package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.mcdragonlib.util.DLColor;

/**
 * 发车信息板的设置：在站台屏基础上换成深底琥珀字，像车站的 LED 发车板。
 *
 * <p>字段与 {@link CtdPlatformSettings} 完全一致（车站 / 站台 / 信号系统），
 * 只是默认配色不同，所以直接继承。
 */
public class CtdBoardSettings extends CtdPlatformSettings {

    public CtdBoardSettings() {
        setFontColor(DLColor.fromInt(0xFFFFC107));
    }
}