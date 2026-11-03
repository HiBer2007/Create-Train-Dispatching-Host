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
 * 站台 PIDS，按格数**手写**五套版式。
 *
 * <p>坐标一律是**基线**：{@code BERLabel} 的字从 y 向上长，高度约 {@code 8 × scale}。
 * 所以「带 [top, top+height)」里的基线约为 {@code top + height - 1}。
 * 字号直接写死，靠 min = max 固定住。
 *
 * <table>
 *   <tr><th>格数</th><th>用途</th><th>版式</th></tr>
 *   <tr><td>3x2</td><td>普通站台悬挂屏</td><td>方向条 + 三趟 + 滚动提示</td></tr>
 *   <tr><td>2x2</td><td>小尺寸悬挂屏</td><td>方向条 + 两趟</td></tr>
 *   <tr><td>4x1</td><td>大尺寸屏蔽门上屏</td><td>横排：方向 + 倒计时 + 门灯 + 线路图</td></tr>
 *   <tr><td>3x1</td><td>中尺寸屏蔽门上屏</td><td>横排：方向 + 倒计时 + 门灯</td></tr>
 *   <tr><td>2x1</td><td>小尺寸屏蔽门上屏</td><td>只有倒计时 + 门灯</td></tr>
 * </table>
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private static final String[] ROW_NAMES = {"本趟", "下一趟", "第三趟"};

    private final BERLabel head = new BERLabel();
    private final BERLabel[] rows = new BERLabel[3];
    private final BERLabel minutes = new BERLabel();
    private final BERLabel route = new BERLabel();
    private final BERLabel door = new BERLabel();
    private final BERLabel footer = new BERLabel();

    public BERCtdPlatform() {
        head.horizontalAlign.set(ETextAlignment.LEFT);
        head.color.set(DLColor.fromInt(0xFFECEFF1));
        head.horizontalScrollMode.set(EScrollMode.NEVER);

        for (int i = 0; i < rows.length; i++) {
            rows[i] = new BERLabel();
            rows[i].horizontalAlign.set(ETextAlignment.LEFT);
            rows[i].horizontalScrollMode.set(EScrollMode.NEVER);
        }

        minutes.horizontalAlign.set(ETextAlignment.LEFT);
        minutes.color.set(DLColor.fromInt(0xFFFF5252));

        route.horizontalAlign.set(ETextAlignment.LEFT);
        route.color.set(DLColor.fromInt(0xFFB0BEC5));
        route.horizontalScrollMode.set(EScrollMode.ALWAYS);

        door.horizontalAlign.set(ETextAlignment.LEFT);

        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.color.set(DLColor.fromInt(0xFF9E9E9E));
        footer.horizontalScrollMode.set(EScrollMode.ALWAYS);
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();

        head.render(graphics, light);
        for (BERLabel row : rows) {
            if (row.text.get() != null) row.render(graphics, light);
        }
        if (minutes.text.get() != null) minutes.render(graphics, light);
        if (route.text.get() != null) route.render(graphics, light);
        if (door.text.get() != null) door.render(graphics, light);
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
        float h = bh * 16f;
        Rectangle area = Rectangle.withSize(0, 0, (int) w, (int) h);

        var stops = blockEntity.getStops();
        String line = settings.lineName();
        String dest = stops.isEmpty() ? "—"
            : stops.get(0).displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);

        clearAll();

        if (bh == 1) {
            layoutSingleLine(settings, blockEntity, stops, now, area, w, bw, line, dest);
            return;
        }

        if (bw == 3 && bh == 2) {
            // 普通站台悬挂屏：48×32。头 8px，三行各 8px，底 6px。
            layoutBoard(settings, area, w, line, dest, stops, now,
                8f, 0.42f, new float[]{16f, 24f, 31f}, 0.45f, 3, 33f, 0.26f);
        } else if (bw == 2 && bh == 2) {
            // 小尺寸悬挂屏：32×32。头 9px，两行各 12px。
            layoutBoard(settings, area, w, line, dest, stops, now,
                9f, 0.38f, new float[]{21f, 31f, 0f}, 0.45f, 2, -1f, 0f);
        } else {
            // 其它尺寸：按格数铺开，行数随高度。
            int n = Math.min(3, Math.max(1, bh - 1));
            float headY = Math.max(7f, h * 0.26f);
            float step = (h - headY) / (n + 1);
            float[] ys = new float[3];
            for (int i = 0; i < 3; i++) ys[i] = headY + step * (i + 1);
            layoutBoard(settings, area, w, line, dest, stops, now,
                headY, 0.40f, ys, 0.44f, n, -1f, 0f);
        }
    }

    /** 多行版式：方向条 + N 趟 + 可选底部提示。 */
    private void layoutBoard(CtdPlatformSettings settings, Rectangle area, float w,
                             String line, String dest,
                             java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                             long now, float headY, float headScale, float[] rowY, float rowScale,
                             int rowCount, float footY, float footScale) {

        place(head, area, w, 2, headY, headScale, headText(line, dest, w), lineColor(settings).getAsARGB());

        for (int i = 0; i < rows.length; i++) {
            BERLabel row = rows[i];
            if (i >= rowCount) { row.text.set(null); continue; }

            String minutesText = "--";
            int color = 0xFF757575;
            if (i < stops.size()) {
                minutesText = minutesTo(stops.get(i), now);
                color = i == 0 ? 0xFFFF5252 : 0xFFECEFF1;
            }
            place(row, area, w, 2, rowY[i], rowScale,
                ROW_NAMES[i] + "   " + minutesText + " 分钟", color);
        }

        if (footY > 0) {
            footer.clippingArea.set(area);
            footer.text.set(TextUtils.text("请注意站台间隙，小心慢行。"));
            footer.preferredWidth.set((float) Math.max(1, w - 4));
            footer.position.set(Point.of(2, footY));
            setScale(footer, footScale);
        } else {
            footer.text.set(null);
        }
    }

    /** 单格高：横排。3/4 宽带方向与线路图，2/3 宽带门灯。 */
    private void layoutSingleLine(CtdPlatformSettings settings, AdvancedDisplayBlockEntity be,
                                  java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                                  long now, Rectangle area, float w, int bw, String line, String dest) {

        String minutesText = stops.isEmpty() ? "--" : minutesTo(stops.get(0), now);

        if (bw >= 3) {
            place(head, area, w * 0.55f, 2, 6.5f, bw == 4 ? 0.38f : 0.34f,
                headText(line, dest, w * 0.5f), lineColor(settings).getAsARGB());

            minutes.clippingArea.set(area);
            minutes.text.set(TextUtils.text(minutesText + " 分钟"));
            minutes.preferredWidth.set(w * 0.42f);
            minutes.position.set(Point.of(w * 0.55f, 13.5f));
            setScale(minutes, 0.45f);

            if (settings.showDoorLight()) {
                boolean open = be.isWaitingAtStation();
                door.clippingArea.set(area);
                door.text.set(TextUtils.text(open ? "◀ ● ▶" : "◀ ○ ▶"));
                door.preferredWidth.set(16f);
                door.position.set(Point.of(w - 17f, 6.5f));
                setScale(door, 0.40f);
                door.color.set(DLColor.fromInt(open ? 0xFF66BB6A : 0xFF546E7A));
            }

            if (settings.showRouteMap() && bw >= 3) {
                StringBuilder map = new StringBuilder();
                int shown = 0;
                for (var s : be.getRemainingStops()) {
                    if (shown++ >= 6) break;
                    if (map.length() > 0) map.append(" — ");
                    map.append(s.title());
                }
                if (map.length() > 0) {
                    route.clippingArea.set(area);
                    route.text.set(TextUtils.text(map.toString()));
                    route.preferredWidth.set((float) Math.max(1, w - 4));
                    route.position.set(Point.of(2, 15.5f));
                    setScale(route, 0.24f);
                }
            }
            return;
        }

        // 2 格宽：只剩倒计时 + 门灯
        minutes.clippingArea.set(area);
        minutes.text.set(TextUtils.text(minutesText + " 分钟"));
        minutes.preferredWidth.set(w - 18f);
        minutes.position.set(Point.of(2, 11f));
        setScale(minutes, 0.50f);

        if (settings.showDoorLight()) {
            boolean open = be.isWaitingAtStation();
            door.clippingArea.set(area);
            door.text.set(TextUtils.text(open ? "◀●" : "◀○"));
            door.preferredWidth.set(14f);
            door.position.set(Point.of(w - 14f, 11f));
            setScale(door, 0.42f);
            door.color.set(DLColor.fromInt(open ? 0xFF66BB6A : 0xFF546E7A));
        }
    }

    // ── 小工具 ──────────────────────────────────────────────────────────

    private void clearAll() {
        head.text.set(null);
        for (BERLabel row : rows) row.text.set(null);
        minutes.text.set(null);
        route.text.set(null);
        door.text.set(null);
        footer.text.set(null);
    }

    private static String headText(String line, String dest, float maxWidthPx) {
        // 中文约 8px/字（scale=1），按最坏情况截断，避免撑破。
        int budget = (int) Math.max(4, maxWidthPx / 8f);
        StringBuilder sb = new StringBuilder();
        if (!line.isEmpty()) sb.append("【").append(line).append("】");
        sb.append("开往 ");
        int room = Math.max(2, budget - sb.length());
        sb.append(dest.length() > room ? dest.substring(0, room) : dest);
        return sb.toString();
    }

    private static String minutesTo(de.mrjulsen.crn.api.core.snapshot.BoardEntry e, long now) {
        long ticks = e.realtime().isKnown()
            ? e.realtime().departureIn(now)
            : e.scheduled().departureIn(now);
        return String.valueOf(Math.max(0, ticks / 20 / 60));
    }

    private static void place(BERLabel label, Rectangle area, float width,
                              float x, float baselineY, float scale, String text, int color) {
        label.clippingArea.set(area);
        label.text.set(TextUtils.text(text));
        label.preferredWidth.set((float) Math.max(1, width - x - 1));
        label.position.set(Point.of(x, baselineY));
        setScale(label, scale);
        label.color.set(DLColor.fromInt(color));
    }

    private static void setScale(BERLabel label, float s) {
        // min = max = s：calculateFinalScales() 里 clamp(1, min, max) 得到 s。
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
    }

    /** 线路色：用户选的优先，其次按代号查 CTD 色号表，最后按名字哈希兜底。 */
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
