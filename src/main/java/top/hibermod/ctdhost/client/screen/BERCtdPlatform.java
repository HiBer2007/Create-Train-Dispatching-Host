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
 * 站台 PIDS。五段式，照参考图：
 *
 * <pre>
 * +------------------------------+  <- 线路色底
 * | 本次列车开往 海傍      [11] |
 * +------------------------------+
 * | 列车进站                     |  <- 最大
 * | 下一趟开往 海傍   2 分钟     |
 * | 第三趟开往 番禺广场 5 分钟   |
 * | 感谢您的配合，尊敬的乘客      |  <- 滚动
 * +------------------------------+
 * </pre>
 *
 * <b>坐标系</b>：{@code BERLabel.position} 是<b>方框左上角</b>，文字从框顶向下画，
 * 行高 {@code 8 x scale}。所以「带 [y, y+h)」直接写 {@code y}，不要当基线。
 * 色带用 {@code fullBackground + backgroundColor}，铺满 {@code (x, y, preferredWidth, preferredHeight)}。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    /** 只用来铺底色的标签，文字是一个空格。 */
    private final BERLabel strip = new BERLabel();
    private final BERLabel head = new BERLabel();
    private final BERLabel badge = new BERLabel();
    private final BERLabel status = new BERLabel();
    private final BERLabel nextLine = new BERLabel();
    private final BERLabel thirdLine = new BERLabel();
    private final BERLabel footer = new BERLabel();

    public BERCtdPlatform() {
        strip.fullBackground.set(true);

        head.horizontalAlign.set(ETextAlignment.LEFT);
        head.color.set(DLColor.fromInt(0xFFFFFFFF));

        badge.horizontalAlign.set(ETextAlignment.RIGHT);
        badge.color.set(DLColor.fromInt(0xFFFFFFFF));

        status.horizontalAlign.set(ETextAlignment.LEFT);
        status.color.set(DLColor.fromInt(0xFFFFFFFF));

        nextLine.horizontalAlign.set(ETextAlignment.LEFT);
        nextLine.color.set(DLColor.fromInt(0xFFECEFF1));

        thirdLine.horizontalAlign.set(ETextAlignment.LEFT);
        thirdLine.color.set(DLColor.fromInt(0xFFECEFF1));

        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.horizontalScrollMode.set(EScrollMode.ALWAYS);
        footer.color.set(DLColor.fromInt(0xFFB0BEC5));

        for (BERLabel l : new BERLabel[]{strip, head, badge, status, nextLine, thirdLine}) {
            l.verticalScrollMode.set(EScrollMode.NEVER);
        }
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();

        for (BERLabel l : new BERLabel[]{strip, head, badge, status, nextLine, thirdLine, footer}) {
            if (l.text.get() != null) l.render(graphics, light);
        }
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
        Rectangle area = Rectangle.withSize(0, 0, bw * 16, bh * 16);

        var stops = blockEntity.getStops();
        String line = settings.lineName();
        DLColor accent = lineColor(settings);
        boolean waiting = blockEntity.isWaitingAtStation();

        clear();

        String dest0 = nameOf(stops, 0);
        String dest1 = nameOf(stops, 1);
        String dest2 = nameOf(stops, 2);
        String min0 = minutesOf(stops, now, 0);
        String min1 = minutesOf(stops, now, 1);
        String min2 = minutesOf(stops, now, 2);
        String tag = line.isEmpty() ? "" : "[" + line + "]";

        if (bw == 3 && bh == 2) {
            // 48x32。字号以 0.30 为「正常小字」基准（8*0.30 = 2.4px 高）。
            paintStrip(area, 0, 0, 48, 6, accent);
            put(head, area, 2, 1f, 28, 4, 0.30f, "本次列车开往 " + dest0, 0xFFFFFFFF);
            put(badge, area, 30, 1f, 16, 4, 0.30f, tag, 0xFFFFFFFF);
            put(status, area, 2, 8f, 44, 6, 0.60f, waiting ? "列车进站" : "本次列车", 0xFFFFFFFF);
            put(nextLine, area, 2, 16f, 44, 3, 0.30f,
                "下一趟开往 " + dest1 + "   " + min1 + " 分钟", 0xFFECEFF1);
            put(thirdLine, area, 2, 22f, 44, 3, 0.30f,
                "第三趟开往 " + dest2 + "   " + min2 + " 分钟", 0xFFECEFF1);
            put(footer, area, 2, 27f, 44, 3, 0.26f, "感谢您的配合，尊敬的乘客。", 0xFFB0BEC5);
            return;
        }

        if (bw == 2 && bh == 2) {
            // 32x32。字宽 9*s/汉字，可用宽 28 → 小字 s ≤ 0.31
            paintStrip(area, 0, 0, 32, 6, accent);
            put(head, area, 2, 1f, 17, 4, 0.26f, "开往 " + dest0, 0xFFFFFFFF);
            put(badge, area, 20, 1f, 10, 4, 0.26f, tag, 0xFFFFFFFF);
            put(status, area, 2, 8f, 28, 6, 0.54f, waiting ? "列车进站" : "本次列车", 0xFFFFFFFF);
            put(nextLine, area, 2, 16f, 28, 3, 0.26f,
                "下一趟 " + dest1 + " " + min1 + "分", 0xFFECEFF1);
            put(thirdLine, area, 2, 22f, 28, 3, 0.26f,
                "第三趟 " + dest2 + " " + min2 + "分", 0xFFECEFF1);
            put(footer, area, 2, 27f, 28, 3, 0.22f, "感谢您的配合。", 0xFFB0BEC5);
            return;
        }

        if (bw == 4 && bh == 1) {
            // 64x16。横向排：方向（左）| 分钟（中）| 下一趟（右）| 底栏
            paintStrip(area, 0, 0, 64, 5, accent);
            put(head, area, 2, 1f, 44, 3, 0.26f, "本次列车开往 " + dest0, 0xFFFFFFFF);
            put(badge, area, 48, 1f, 14, 3, 0.26f, tag, 0xFFFFFFFF);
            put(status, area, 2, 6f, 20, 5, 0.50f, min0 + " 分钟", 0xFFFF5252);
            put(nextLine, area, 24, 7f, 38, 3, 0.26f,
                "下一趟 " + dest1 + " " + min1 + " 分钟", 0xFFECEFF1);
            put(footer, area, 2, 12f, 60, 3, 0.22f, "感谢您的配合，尊敬的乘客。", 0xFFB0BEC5);
            return;
        }

        if (bw == 3 && bh == 1) {
            // 48x16
            paintStrip(area, 0, 0, 48, 5, accent);
            put(head, area, 2, 1f, 32, 3, 0.24f, "开往 " + dest0, 0xFFFFFFFF);
            put(badge, area, 36, 1f, 10, 3, 0.24f, tag, 0xFFFFFFFF);
            put(status, area, 2, 6f, 18, 5, 0.46f, min0 + " 分钟", 0xFFFF5252);
            put(nextLine, area, 22, 7f, 26, 3, 0.24f, "下一趟 " + min1 + " 分钟", 0xFFECEFF1);
            put(footer, area, 2, 12f, 44, 3, 0.20f, "感谢您的配合。", 0xFFB0BEC5);
            return;
        }

        // 2x1 及其它窄格
        paintStrip(area, 0, 0, 32, 5, accent);
        put(head, area, 2, 1f, 20, 3, 0.22f, "开往 " + dest0, 0xFFFFFFFF);
        put(badge, area, 22, 1f, 9, 3, 0.22f, tag, 0xFFFFFFFF);
        put(status, area, 2, 6f, 28, 5, 0.44f, min0 + " 分钟", 0xFFFF5252);
    }

    // ── 绘制原语 ──────────────────────────────────────────────────────

    /** 线路色带：一块实心矩形。 */
    private void paintStrip(Rectangle area, float x, float y, float w, float h, DLColor color) {
        strip.clippingArea.set(area);
        strip.text.set(TextUtils.text(" "));
        strip.preferredWidth.set(w);
        strip.preferredHeight.set(h);
        strip.position.set(Point.of(x, y));
        strip.horizontalMinScale.set(0.1f);
        strip.horizontalMaxScale.set(0.1f);
        strip.verticalMinScale.set(0.1f);
        strip.verticalMaxScale.set(0.1f);
        strip.backgroundColor.set(color);
        strip.fullBackground.set(true);
    }

    /** 一段文字：位置是框左上角，字号 s（行高 8s），框 w×h。 */
    private static void put(BERLabel label, Rectangle area, float x, float y,
                            float w, float h, float s, String content, int argb) {
        label.clippingArea.set(area);
        label.text.set(TextUtils.text(content));
        label.preferredWidth.set(Math.max(1f, w));
        label.preferredHeight.set(Math.max(1f, h));
        label.position.set(Point.of(x, y));
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
        label.color.set(DLColor.fromInt(argb));
    }

    private void clear() {
        strip.text.set(null);
        head.text.set(null);
        badge.text.set(null);
        status.text.set(null);
        nextLine.text.set(null);
        thirdLine.text.set(null);
        footer.text.set(null);
    }

    private static String nameOf(java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops, int i) {
        if (i >= stops.size()) return "—";
        return stops.get(i).displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
    }

    private static String minutesOf(java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                                    long now, int i) {
        if (i >= stops.size()) return "--";
        var e = stops.get(i);
        long ticks = e.realtime().isKnown()
            ? e.realtime().departureIn(now)
            : e.scheduled().departureIn(now);
        return String.valueOf(Math.max(0, ticks / 20 / 60));
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
