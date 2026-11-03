package top.hibermod.ctdhost.client.screen;

import net.minecraft.nbt.CompoundTag;

import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.util.DLColor;

/**
 * CTD 屏蔽门挡板（站台门上方的长条屏）设置。
 *
 * <p>版式跟随尺寸（见 {@link BERCtdPsd}）：
 * <table>
 *   <tr><td>4x1</td><td>只有线路图，无指示灯</td></tr>
 *   <tr><td>3x1</td><td>线路图 或 指示灯（二选一）</td></tr>
 *   <tr><td>2x1</td><td>只有指示灯，无线路图</td></tr>
 * </table>
 *
 * <p>背景固定白色，主色（色带、站台号圆章）= 线路颜色。
 */
public class CtdPsdSettings extends BasicDisplaySettings {

    private static final String NBT_STATION = "CtdPsdStation";
    private static final String NBT_PLATFORM = "CtdPsdPlatform";
    private static final String NBT_NAME = "CtdPsdName";
    private static final String NBT_NEXT = "CtdPsdNext";
    private static final String NBT_STATION_ID = "CtdPsdStationId";
    private static final String NBT_DOOR = "CtdPsdDoor";
    private static final String NBT_MAP = "CtdPsdMap";
    private static final String NBT_ARROW_RIGHT = "CtdPsdArrowRight";
    private static final String NBT_LINE = "CtdPsdLine";
    private static final String NBT_LINE_COLOR = "CtdPsdLineColor";
    private static final String NBT_STATIONS = "CtdPsdStations";

    private String station = "";
    private String platform = "";
    private String stationName = "";
    private String nextStation = "";
    private String stationId = "";
    private String doorNumber = "1";
    private boolean showMap = false;
    /** 箭头方向 = 列车运行方向；true 表示向右（→），false 表示向左（←）。 */
    private boolean arrowRight = false;
    private String lineName = "";
    private int lineColor = 0;
    /** 线路图车站，按顺序用逗号分隔；实际由运行图系统下发。 */
    private String mapStations = "";

    public CtdPsdSettings() {
        // 背景必须是白色
        setBackColor(DLColor.fromInt(0xFFFFFFFF));
        setFontColor(DLColor.fromInt(0xFF000000));
    }

    public String station() { return station == null ? "" : station; }
    public void setStation(String v) { station = v == null ? "" : v; }

    public String platform() { return platform == null ? "" : platform; }
    public void setPlatform(String v) { platform = v == null ? "" : v; }

    public String stationName() { return stationName == null ? "" : stationName; }
    public void setStationName(String v) { stationName = v == null ? "" : v; }

    public String nextStation() { return nextStation == null ? "" : nextStation; }
    public void setNextStation(String v) { nextStation = v == null ? "" : v; }

    public String stationId() { return stationId == null ? "" : stationId; }
    public void setStationId(String v) { stationId = v == null ? "" : v; }

    public String doorNumber() { return doorNumber == null ? "" : doorNumber; }
    public void setDoorNumber(String v) { doorNumber = v == null ? "" : v; }

    public boolean showMap() { return showMap; }
    public void setShowMap(boolean v) { showMap = v; }

    public boolean arrowRight() { return arrowRight; }
    public void setArrowRight(boolean v) { arrowRight = v; }

    public String lineName() { return lineName == null ? "" : lineName; }
    public void setLineName(String v) { lineName = v == null ? "" : v; }

    public int lineColor() { return lineColor; }
    public void setLineColor(int v) { lineColor = v; }

    public String mapStations() { return mapStations == null ? "" : mapStations; }
    public void setMapStations(String v) { mapStations = v == null ? "" : v; }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        station = nbt.getString(NBT_STATION);
        platform = nbt.getString(NBT_PLATFORM);
        stationName = nbt.getString(NBT_NAME);
        nextStation = nbt.getString(NBT_NEXT);
        stationId = nbt.getString(NBT_STATION_ID);
        doorNumber = nbt.getString(NBT_DOOR);
        if (doorNumber == null || doorNumber.isEmpty()) doorNumber = "1";
        if (doorNumber.isEmpty()) doorNumber = "1";
        showMap = nbt.getBoolean(NBT_MAP);
        arrowRight = nbt.getBoolean(NBT_ARROW_RIGHT);
        lineName = nbt.getString(NBT_LINE);
        lineColor = nbt.getInt(NBT_LINE_COLOR);
        mapStations = nbt.getString(NBT_STATIONS);
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putString(NBT_STATION, station());
        nbt.putString(NBT_PLATFORM, platform());
        nbt.putString(NBT_NAME, stationName());
        nbt.putString(NBT_NEXT, nextStation());
        nbt.putString(NBT_STATION_ID, stationId());
        nbt.putString(NBT_DOOR, doorNumber());
        nbt.putBoolean(NBT_MAP, showMap);
        nbt.putBoolean(NBT_ARROW_RIGHT, arrowRight);
        nbt.putString(NBT_LINE, lineName());
        nbt.putInt(NBT_LINE_COLOR, lineColor);
        nbt.putString(NBT_STATIONS, mapStations());
    }

    @Override
    public void onChangeSettings(de.mrjulsen.crn.block.display.properties.IDisplaySettings oldSettings) {
        super.onChangeSettings(oldSettings);
        if (oldSettings instanceof CtdPsdSettings old) {
            station = old.station;
            platform = old.platform;
            stationName = old.stationName;
            nextStation = old.nextStation;
            stationId = old.stationId;
            doorNumber = old.doorNumber;
            showMap = old.showMap;
            arrowRight = old.arrowRight;
            lineName = old.lineName;
            lineColor = old.lineColor;
            mapStations = old.mapStations;
        }
    }

    @Override
    public void buildGui(GuiBuilderContext context) {
        super.buildGui(context);
        buildTextLine(context, "gui.ctdhost.station", station(), this::setStation);
        buildTextLine(context, "gui.ctdhost.platform", platform(), this::setPlatform);
        buildTextLine(context, "gui.ctdhost.psd_door", doorNumber(), this::setDoorNumber);
        buildTextLine(context, "gui.ctdhost.line", lineName(), this::setLineName);
        buildCheckLine(context, "gui.ctdhost.psd_map", showMap(), this::setShowMap);
        buildCheckLine(context, "gui.ctdhost.psd_arrow_right", arrowRight(), this::setArrowRight);
        buildColorPickerLine(context, "gui.ctdhost.line_color", lineColor(), this::setLineColor);
    }

    // ── 复用 CtdPlatformSettings 的两种控件 ───────────────────────────

    private void buildTextLine(GuiBuilderContext context, String langKey,
                               String value, java.util.function.Consumer<String> setter) {
        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel line =
            context.container().addLine(langKey);

        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget icon =
            line.addComponent(new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0));
        icon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.TEXT.getAsSprite(16, 16));
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
                setter.accept(e.text().getPlainText());
                return false;
            });
        box.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));
        line.addComponent(box);
    }

    /** 线路颜色：从 CTD 颜色注册表里选。 */
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
                .withStyle(st -> st.withColor(c & 0xFFFFFF)));
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

    private void buildCheckLine(GuiBuilderContext context, String langKey,                                boolean value, java.util.function.Consumer<Boolean> setter) {
        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel line =
            context.container().addLine(langKey);

        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget icon =
            line.addComponent(new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0));
        icon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.TEXT.getAsSprite(16, 16));
        icon.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));

        de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox box =
            line.addComponent(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox(
                0, 0, 100, de.mrjulsen.crn.client.gui.widgets.create.CreateButton.HEIGHT));
        box.layoutContraint.set(
            de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout.FlowConstraint.FILL);
        box.checked.set(value);
        // 勾选框必须带文字，否则只剩一个空框
        box.text.set(de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey));
        box.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey),
                de.mrjulsen.mcdragonlib.util.TextUtils.translate(langKey + ".description")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)),
            200));
        box.addEventListener(
            de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLToggleButton.CheckedChangedEvent.class,
            (s, e) -> {
                setter.accept(e.checked());
                return false;
            });
    }
}
