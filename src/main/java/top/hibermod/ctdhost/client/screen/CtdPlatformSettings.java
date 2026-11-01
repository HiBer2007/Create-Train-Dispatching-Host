package top.hibermod.ctdhost.client.screen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;

/**
 * 站台屏的设置：信号系统 + 站台号 + 车站号 + 显示内容。
 *
 * <h2>字段与用户需求对应</h2>
 * <table>
 *   <tr><td>signalSystem</td><td>「设置包含信号系统」</td></tr>
 *   <tr><td>platform</td><td>「站台号（如果是站房类型则不需要）」——允许空</td></tr>
 *   <tr><td>station</td><td>「车站号」——车站 UUID</td></tr>
 *   <tr><td>content</td><td>「设置显示包含的内容」</td></tr>
 * </table>
 *
 * <h2>为什么继承 BasicDisplaySettings</h2>
 * 它已经把「基类 NBT + 基础 GUI」做好了。我们只加四个字段，
 * 不重复实现 CRN 的通用设置框架——**能复用就不重写**，
 * 否则 CRN 升级时我们要跟着改两份。
 */
public class CtdPlatformSettings extends BasicDisplaySettings {

    /** 显示内容。 */
    public enum Content {
        DEPARTURES("departures"),
        ARRIVALS("arrivals"),
        BOTH("both");

        private final String id;

        Content(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Content byId(String id) {
            for (Content c : values()) {
                if (c.id.equals(id)) return c;
            }
            return DEPARTURES;
        }
    }

    private static final String NBT_SIGNAL_SYSTEM = "CtdSignalSystem";
    private static final String NBT_STATION = "CtdStation";
    private static final String NBT_PLATFORM = "CtdPlatform";
    private static final String NBT_CONTENT = "CtdContent";

    private String signalSystem = "";
    private String station = "";
    private String platform = "";
    private Content content = Content.DEPARTURES;

    public String signalSystem() {
        return signalSystem;
    }

    public void setSignalSystem(String value) {
        signalSystem = value == null ? "" : value;
    }

    public String station() {
        return station;
    }

    public void setStation(String value) {
        station = value == null ? "" : value;
    }

    /** 站台号；站房类型留空。 */
    public String platform() {
        return platform;
    }

    public void setPlatform(String value) {
        platform = value == null ? "" : value;
    }

    public Content content() {
        return content;
    }

    public void setContent(Content value) {
        content = value == null ? Content.DEPARTURES : value;
    }

    /** 信号系统的 ResourceLocation；没设时为 null。 */
    public ResourceLocation signalSystemId() {
        return signalSystem.isEmpty() ? null : ResourceLocation.tryParse(signalSystem);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        signalSystem = nbt.getString(NBT_SIGNAL_SYSTEM);
        station = nbt.getString(NBT_STATION);
        platform = nbt.getString(NBT_PLATFORM);
        content = Content.byId(nbt.getString(NBT_CONTENT));
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putString(NBT_SIGNAL_SYSTEM, signalSystem);
        nbt.putString(NBT_STATION, station);
        nbt.putString(NBT_PLATFORM, platform);
        nbt.putString(NBT_CONTENT, content.id());
    }

    @Override
    public void buildGui(GuiBuilderContext context) {
        super.buildGui(context);
        // 第一版不做自定义控件：先让类型能被选中、能渲染。
        // 设置项已经在 NBT 里，命令或后续 GUI 都能改。
    }

    @Override
    public void onChangeSettings(de.mrjulsen.crn.block.display.properties.IDisplaySettings oldSettings) {
        super.onChangeSettings(oldSettings);
        if (oldSettings instanceof CtdPlatformSettings old) {
            signalSystem = old.signalSystem;
            station = old.station;
            platform = old.platform;
            content = old.content;
        }
    }
}
