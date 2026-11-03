package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel.EScrollMode;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 站台 PIDS。**按组件拆分**：每一段文字是独立的 {@link BERLabel}，
 * 各自有固定的坐标、字号与颜色，互不牵连。
 *
 * <p>版式参照实际站台屏：
 * <pre>
 *   【11号线】开往 员村                  ← 方向条（线路色，小字）
 *   本趟     3 分钟                      ← 数字最大
 *   下一趟  10 分钟
 *   第三趟  18 分钟
 *   请注意站台间隙，小心慢行。            ← 滚动提示
 * </pre>
 *
 * <p>坐标是**基线**（字向上长，高约 8×scale）。五种格数各写一套，不做自动缩放。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private static final String[] ROW_LABELS = {"本趟", "下一趟", "第三趟"};

    private final BERLabel head = new BERLabel();
    private final BERLabel[] label = new BERLabel[3];
    private final BERLabel[] number = new BERLabel[3];
    private final BERLabel[] unit = new BERLabel[3];
    private final BERLabel door = new BERLabel();
    private final BERLabel route = new BERLabel();
    private final BERLabel footer = new BERLabel();

    public BERCtdPlatform() {
        head.horizontalAlign.set(ETextAlignment.LEFT);
        head.horizontalScrollMode.set(EScrollMode.NEVER);

        for (int i = 0; i < 3; i++) {
            label[i] = new BERLabel();
            label[i].horizontalAlign.set(ETextAlignment.LEFT);
            label[i].horizontalScrollMode.set(EScrollMode.NEVER);
            label[i].color.set(DLColor.fromInt(0xFFCFD8DC));

            number[i] = new BERLabel();
            number[i].horizontalAlign.set(ETextAlignment.LEFT);
            number[i].horizontalScrollMode.set(EScrollMode.NEVER);

            unit[i] = new BERLabel();
            unit[i].horizontalAlign.set(ETextAlignment.LEFT);
            unit[i].horizontalScrollMode.set(EScrollMode.NEVER);
            unit[i].color.set(DLColor.fromInt(0xFFCFD8DC));
        }

        door.horizontalAlign.set(ETextAlignment.LEFT);
        door.horizontalScrollMode.set(EScrollMode.NEVER);

        route.horizontalAlign.set(ETextAlignment.LEFT);
        route.horizontalScrollMode.set(EScrollMode.ALWAYS);
        route.color.set(DLColor.fromInt(0xFF9E9E9E));

        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.horizontalScrollMode.set(EScrollMode.ALWAYS);
        footer.color.set(DLColor.fromInt(0xFF8A8A8A));
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();

        head.render(graphics, light);
        for (int i = 0; i < 3; i++) {
            if (label[i].text.get() != null) label[i].render(graphics, light);
            if (number[i].text.get() != null) number[i].render(graphics, light);
            if (unit[i].text.get() != null) unit[i].render(graphics, light);
        }
        if (door.text.get() != null) door.render(graphics, light);
        if (route.text.get() != null) route.render(graphics, light);
        if (footer.text.get() != null) footer.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        CtdPlatformSettings settings = safeSettings(blockEntity);
        Level lvl = blockEntity.getLevel();
        long now = lvl == null ? 0 : lvl.getGameTime();

        int bw = Math.max(1, (int) blockEntity.getXSizeScaled());
        int bh = Math.max(1, (int) blockEntity.getYSizeScaled());
        float w = bw * 16f;
        Rectangle area = Rectangle.withSize(0, 0, bw * 16, bh * 16);

        var stops = blockEntity.getStops();
        String line = settings.lineName();
        String dest = stops.isEmpty() ? "—"
            : stops.get(0).displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);

        clear();

        if (bh >= 2 && bw >= 3) {
            layoutBig(settings, area, w, line, dest, stops, now);
        } else if (bh >= 2) {
            layoutMedium(settings, area, w, line, dest, stops, now);
        } else {
            layoutSingle(settings, blockEntity, area, w, bw, line, dest, stops, now);
        }
    }

    // ── 3x2 及更大 ────────────────────────────────────────────────────
    private void layoutBig(CtdPlatformSettings settings, Rectangle area, float w,
                           String line, String dest,
                           java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                           long now) {
        head(area, w, line, dest, 2, 6.5f, 0.30f, settings);

        float[] labelY = {17f, 24.5f, 31f};
        float[] numY = {18.5f, 25.5f, 31.5f};
        float[] numScale = {0.72f, 0.40f, 0.40f};

        for (int i = 0; i < 3; i++) {
            row(i, area, w, stops, now, labelY[i], numY[i], numScale[i], 0.26f);
        }

        footer(area, w, "请注意站台间隙，小心慢行。", 32.5f, 0.22f);
    }

    // ── 2x2 ──────────────────────────────────────────────────────────
    private void layoutMedium(CtdPlatformSettings settings, Rectangle area, float w,
                              String line, String dest,
                              java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                              long now) {
        head(area, w, line, dest, 2, 7f, 0.28f, settings);

        float[] labelY = {19f, 30f, 0f};
        float[] numY = {20f, 30.5f, 0f};
        float[] numScale = {0.68f, 0.42f, 0f};

        for (int i = 0; i < 2; i++) {
            row(i, area, w, stops, now, labelY[i], numY[i], numScale[i], 0.24f);
        }
    }

    // ── 1 格高：横排 ──────────────────────────────────────────────────
    private void layoutSingle(CtdPlatformSettings settings, AdvancedDisplayBlockEntity be,
                              Rectangle area, float w, int bw, String line, String dest,
                              java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                              long now) {
        if (bw >= 3) {
            head(area, w * 0.5f, line, dest, 2, 6.5f, bw == 4 ? 0.30f : 0.26f, settings);

            number[0].clippingArea.set(area);
            number[0].text.set(TextUtils.text(minutesOf(stops, now, 0)));
            number[0].preferredWidth.set(w * 0.25f);
            number[0].position.set(Point.of(w * 0.52f, 10.5f));
            scale(number[0], 0.55f);
            number[0].color.set(DLColor.fromInt(0xFFFF5252));

            unit[0].clippingArea.set(area);
            unit[0].text.set(TextUtils.text("分钟"));
            unit[0].preferredWidth.set(w * 0.2f);
            unit[0].position.set(Point.of(w * 0.72f, 10.5f));
            scale(unit[0], 0.28f);

            if (settings.showDoorLight()) {
                boolean open = be.isWaitingAtStation();
                door.clippingArea.set(area);
                door.text.set(TextUtils.text(open ? "◀ ●" : "◀ ○"));
                door.preferredWidth.set(14f);
                door.position.set(Point.of(w - 15f, 6.5f));
                scale(door, 0.34f);
                door.color.set(DLColor.fromInt(open ? 0xFF66BB6A : 0xFF546E7A));
            }

            if (settings.showRouteMap()) {
                StringBuilder map = new StringBuilder();
                int shown = 0;
                for (var s : be.getRemainingStops()) {
                    if (shown++ >= 5) break;
                    if (map.length() > 0) map.append(" — ");
                    map.append(s.title());
                }
                if (map.length() > 0) {
                    route.clippingArea.set(area);
                    route.text.set(TextUtils.text(map.toString()));
                    route.preferredWidth.set((float) Math.max(1, w - 4));
                    route.position.set(Point.of(2, 15.5f));
                    scale(route, 0.20f);
                }
            }
            return;
        }

        number[0].clippingArea.set(area);
        number[0].text.set(TextUtils.text(minutesOf(stops, now, 0)));
        number[0].preferredWidth.set(w * 0.4f);
        number[0].position.set(Point.of(2, 11f));
        scale(number[0], 0.62f);
        number[0].color.set(DLColor.fromInt(0xFFFF5252));

        unit[0].clippingArea.set(area);
        unit[0].text.set(TextUtils.text("分钟"));
        unit[0].preferredWidth.set(w * 0.3f);
        unit[0].position.set(Point.of(14f, 11f));
        scale(unit[0], 0.30f);

        if (settings.showDoorLight()) {
            boolean open = be.isWaitingAtStation();
            door.clippingArea.set(area);
            door.text.set(TextUtils.text(open ? "◀●" : "◀○"));
            door.preferredWidth.set(12f);
            door.position.set(Point.of(w - 13f, 11f));
            scale(door, 0.34f);
            door.color.set(DLColor.fromInt(open ? 0xFF66BB6A : 0xFF546E7A));
        }
    }

    // ── 组件绘制 ──────────────────────────────────────────────────────

    private void head(Rectangle area, float width, String line, String dest,
                      float x, float y, float s, CtdPlatformSettings settings) {
        String text = (line.isEmpty() ? "" : "【" + line + "】") + "开往 " + clip(dest, width, s);
        head.clippingArea.set(area);
        head.text.set(TextUtils.text(text));
        head.preferredWidth.set((float) Math.max(1, width - x - 1));
        head.position.set(Point.of(x, y));
        scale(head, s);
        head.color.set(lineColor(settings));
    }

    private void row(int i, Rectangle area, float w,
                     java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                     long now, float labelY, float numY, float numScale, float labelScale) {
        String mins = minutesOf(stops, now, i);

        label[i].clippingArea.set(area);
        label[i].text.set(TextUtils.text(ROW_LABELS[i]));
        label[i].preferredWidth.set(w * 0.3f);
        label[i].position.set(Point.of(2, labelY));
        scale(label[i], labelScale);

        number[i].clippingArea.set(area);
        number[i].text.set(TextUtils.text(mins));
        number[i].preferredWidth.set(w * 0.18f);
        number[i].position.set(Point.of(15f, numY));
        scale(number[i], numScale);
        number[i].color.set(DLColor.fromInt(i == 0 ? 0xFFFF5252 : 0xFFECEFF1));

        unit[i].clippingArea.set(area);
        unit[i].text.set(TextUtils.text("分钟"));
        unit[i].preferredWidth.set(w * 0.2f);
        unit[i].position.set(Point.of(16f + 6f * numScale * mins.length(), labelY));
        scale(unit[i], labelScale);
    }

    private void footer(Rectangle area, float w, String text, float y, float s) {
        footer.clippingArea.set(area);
        footer.text.set(TextUtils.text(text));
        footer.preferredWidth.set((float) Math.max(1, w - 4));
        footer.position.set(Point.of(2, y));
        scale(footer, s);
    }

    private void clear() {
        head.text.set(null);
        for (int i = 0; i < 3; i++) {
            label[i].text.set(null);
            number[i].text.set(null);
            unit[i].text.set(null);
        }
        door.text.set(null);
        route.text.set(null);
        footer.text.set(null);
    }

    // ── 取值与小工具 ──────────────────────────────────────────────────

    private static String minutesOf(java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                                    long now, int i) {
        if (i >= stops.size()) return "--";
        var e = stops.get(i);
        long ticks = e.realtime().isKnown()
            ? e.realtime().departureIn(now)
            : e.scheduled().departureIn(now);
        return String.valueOf(Math.max(0, ticks / 20 / 60));
    }

    /** 按可用宽度裁掉过长的目的地（中文 ≈ 8px/字 × scale）。 */
    private static String clip(String dest, float widthPx, float s) {
        int max = (int) Math.max(2, (widthPx * 0.55f) / (8f * s));
        return dest.length() > max ? dest.substring(0, max) : dest;
    }

    private static void scale(BERLabel label, float s) {
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
    }

    static DLColor lineColor(CtdPlatformSettings settings) {
        if (settings.lineColor() != 0) return DLColor.fromInt(settings.lineColor());

        String key = settings.lineName();
        if (key != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+)").matcher(key);
            if (m.find()) {
                var id = net.minecraft.resources.ResourceLocation.tryParse("ctd:line" + m.group(1));
                int c = top.hibermod.ctdapi.display.LineColors.getOrDefault(id, 0);
                if (c != 0) return DLColor.fromInt(c);
            }
        }

        int hash = key == null ? 0 : key.hashCode();
        int[] palette = {
            0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047,
            0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFF6D4C41
        };
        return DLColor.fromInt(palette[Math.floorMod(hash, palette.length)]);
    }

    static CtdPlatformSettings safeSettings(AdvancedDisplayBlockEntity blockEntity) {
        var s = blockEntity.getSettings();
        return s instanceof CtdPlatformSettings ctd ? ctd : new CtdPlatformSettings();
    }
}
