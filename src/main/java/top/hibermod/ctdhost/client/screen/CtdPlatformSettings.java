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
    private static final String NBT_LINE = "CtdLineName";
    private static final String NBT_LINE_COLOR = "CtdLineColor";
    private static final String NBT_ROUTE_MAP = "CtdRouteMap";
    private static final String NBT_DOOR_LIGHT = "CtdDoorLight";
    private static final String NBT_CONTENT = "CtdContent";

    private String signalSystem = "";
    private String station = "";
    private String platform = "";
    /** 线路代号，例如「1号线」「11号线」「广佛城际」；空串表示不显示。 */
    private String lineName = "";
    /** 线路颜色（ARGB）；0 表示按代号自动取色。 */
    private int lineColor = 0;
    /** 1 格高时是否显示线路图（3/4 宽建议开）。 */
    private boolean showRouteMap = true;
    /** 1 格高时是否显示屏蔽门开关指示（2/3 宽建议开）。 */
    private boolean showDoorLight = true;
    private Content content = Content.DEPARTURES;

    public CtdPlatformSettings() {
        // 背景保持默认（透明，露出方块纹理），文字用白色。
        setFontColor(de.mrjulsen.mcdragonlib.util.DLColor.fromInt(0xFFFFFFFF));
    }

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
    public String lineName() {
        return lineName;
    }

    public void setLineName(String value) {
        lineName = value == null ? "" : value;
    }

    public int lineColor() {
        return lineColor;
    }

    public boolean showRouteMap() { return showRouteMap; }
    public void setShowRouteMap(boolean v) { showRouteMap = v; }

    public boolean showDoorLight() { return showDoorLight; }
    public void setShowDoorLight(boolean v) { showDoorLight = v; }

    public void setLineColor(int value) {
        lineColor = value;
    }

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
        lineName = nbt.getString(NBT_LINE);
        lineColor = nbt.getInt(NBT_LINE_COLOR);
        showRouteMap = !nbt.contains(NBT_ROUTE_MAP) || nbt.getBoolean(NBT_ROUTE_MAP);
        showDoorLight = !nbt.contains(NBT_DOOR_LIGHT) || nbt.getBoolean(NBT_DOOR_LIGHT);
        content = Content.byId(nbt.getString(NBT_CONTENT));
        org.slf4j.LoggerFactory.getLogger("ctdhost").debug(
            "ctdhost: settings loaded station=[{}] platform=[{}] sys=[{}]",
            station, platform, signalSystem);
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putString(NBT_SIGNAL_SYSTEM, signalSystem);
        nbt.putString(NBT_STATION, station);
        nbt.putString(NBT_PLATFORM, platform);
        nbt.putString(NBT_LINE, lineName);
        nbt.putInt(NBT_LINE_COLOR, lineColor);
        nbt.putBoolean(NBT_ROUTE_MAP, showRouteMap);
        nbt.putBoolean(NBT_DOOR_LIGHT, showDoorLight);
        nbt.putString(NBT_CONTENT, content.id());
        org.slf4j.LoggerFactory.getLogger("ctdhost").debug(
            "ctdhost: settings saved station=[{}] platform=[{}] sys=[{}]",
            station, platform, signalSystem);
    }

    @Override
    public void buildGui(GuiBuilderContext context) {
        super.buildGui(context);

        // 三个输入项：车站号 / 站台号 / 信号系统。
        // 控件照抄 CRN 的 GuiBuilderWrapper.buildStaticTextGui（addLine(String) + CreateTextBox）。
        buildTextLine(context, "gui.ctdhost.station", station(), this::setStation);
        buildTextLine(context, "gui.ctdhost.platform", platform(), this::setPlatform);
        buildTextLine(context, "gui.ctdhost.line", lineName(), this::setLineName);
        buildColorPickerLine(context, "gui.ctdhost.line_color", lineColor(), this::setLineColor);
        buildCheckLine(context, "gui.ctdhost.route_map", showRouteMap(), this::setShowRouteMap);
        buildCheckLine(context, "gui.ctdhost.door_light", showDoorLight(), this::setShowDoorLight);
        // 信号系统：用下拉选择（数据来自宿主同步过来的列表）。
        buildPickerLine(context, "gui.ctdhost.signal_system", signalSystem(), this::setSignalSystem);
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
            lineName = old.lineName;
            lineColor = old.lineColor;
            showRouteMap = old.showRouteMap;
            showDoorLight = old.showDoorLight;
            content = old.content;
        }
    }
    private void buildTextLine(GuiBuilderContext context, String langKey,
                               String value, java.util.function.Consumer<String> setter) {
        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel line =
            context.container().addLine(langKey);

        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget icon =
            line.addComponent(new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0));
        icon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.TEXT.getAsSprite(16, 16));
        // 悬浮文本：标题 + 灰色说明，照 CRN 的 GuiBuilderWrapper 写法。
        icon.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));

        de.mrjulsen.crn.client.gui.widgets.create.CreateTextBox box =
            new de.mrjulsen.crn.client.gui.widgets.create.CreateTextBox(0, 0, 0);
        box.layoutContraint.set(
            de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.FlowConstraint.FILL);
        box.maxCharacters.set((int) Short.MAX_VALUE);
        box.text.get().set(value == null ? "" : value);
        box.addEventListener(
            de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel.TextChangedEvent.class,
            (s, e) -> {
                org.slf4j.LoggerFactory.getLogger("ctdhost").debug(
                    "ctdhost: text changed key={} value=[{}]", langKey, e.text().getPlainText());
                setter.accept(e.text().getPlainText());
                return false;
            });
        // 输入框本身也挂一份悬浮文本：只挂图标时鼠标停在框里看不到。
        box.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));
        line.addComponent(box);
    }
    private void buildPickerLine(GuiBuilderContext context, String langKey,
                                 String value, java.util.function.Consumer<String> setter) {
        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel line =
            context.container().addLine(langKey);

        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget icon =
            line.addComponent(new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0));
        icon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.PLATFORM_INFORMATION.getAsSprite(16, 16));
        icon.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));

        java.util.List<String> items = new java.util.ArrayList<>(CtdSignalSystemData.all());
        if (items.isEmpty()) items.add(value == null ? "" : value);

        de.mrjulsen.crn.client.gui.widgets.create.CreateItemPicker<String> picker =
            new de.mrjulsen.crn.client.gui.widgets.create.CreateItemPicker<>(0, 0, 0);
        picker.renderArrow.set(true);
        // 下拉里显示翻译名，缺翻译时退回 id 原文。
        picker.formatter.set(item -> item == null
            ? de.mrjulsen.mcdragonlib.util.TextUtils.empty()
            : net.minecraft.network.chat.Component.translatableWithFallback(
                signalSystemKey(item), item));
        picker.layoutContraint.set(
            de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.FlowConstraint.FILL);
        picker.items.addAll(items);
        picker.selectedItem.set(value == null || value.isEmpty()
            ? java.util.Optional.empty()
            : java.util.Optional.of(value));
        picker.addEventListener(
            de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton.SelectedItemChanged.class,
            (s, e) -> {
                e.item().ifPresent(x -> setter.accept(String.valueOf(x)));
                return false;
            });
        picker.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));
        line.addComponent(picker);
    }

    /** 信号系统 id → 翻译键：{@code signal_system.<namespace>.<path>}。 */
    private static String signalSystemKey(String id) {
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.resources.ResourceLocation.tryParse(id == null ? "" : id);
        if (rl == null) return "signal_system.unknown";
        return "signal_system." + rl.getNamespace() + "." + rl.getPath();
    }

    /** 线路颜色选择器：候选来自 ctdapi 的 LineColors 注册表。 */
    private void buildColorPickerLine(GuiBuilderContext context, String langKey, int value,
                                      java.util.function.IntConsumer setter) {
        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel line =
            context.container().addLine(langKey);

        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget icon =
            line.addComponent(new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0));
        icon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.VERY_DETAILED.getAsSprite(16, 16));
        icon.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));

        java.util.List<Integer> colors = new java.util.ArrayList<>(
            top.hibermod.ctdapi.display.LineColors.all().values());
        if (colors.isEmpty()) colors.add(0xFFFFFFFF);

        de.mrjulsen.crn.client.gui.widgets.create.CreateItemPicker<Integer> picker =
            new de.mrjulsen.crn.client.gui.widgets.create.CreateItemPicker<>(0, 0, 0);
        picker.renderArrow.set(true);
        picker.layoutContraint.set(
            de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.FlowConstraint.FILL);
        picker.formatter.set(c -> c == null
            ? de.mrjulsen.mcdragonlib.util.TextUtils.empty()
            : net.minecraft.network.chat.Component.literal(
                    "\u25A0  #" + String.format("%08X", c))
                .withStyle(s -> s.withColor(c & 0xFFFFFF)));
        picker.items.addAll(colors);
        picker.selectedItem.set(value == 0
            ? java.util.Optional.empty()
            : java.util.Optional.of(value));
        picker.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));
        picker.addEventListener(
            de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton.SelectedItemChanged.class,
            (s, e) -> {
                e.item().ifPresent(x -> setter.accept((Integer) x));
                return false;
            });
        line.addComponent(picker);
    }

    /** 布尔项：复选框 + 悬浮说明。 */
    private void buildCheckLine(GuiBuilderContext context, String langKey, boolean value,
                                java.util.function.Consumer<Boolean> setter) {
        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel line =
            context.container().addLine(langKey);

        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget icon =
            line.addComponent(new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0));
        icon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.PLATFORM_INFORMATION.getAsSprite(16, 16));
        icon.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));

        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox box =
            new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox(
                0, 0, 100, de.mrjulsen.crn.client.gui.widgets.create.CreateButton.HEIGHT);
        box.checked.set(value);
        box.text.set(de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey));
        box.layoutContraint.set(
            de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.FlowConstraint.FILL);
        box.addEventListener(
            de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLToggleButton.CheckedChangedEvent.class,
            (s, e) -> { setter.accept(e.checked()); return false; });
        line.addComponent(box);
    }

}