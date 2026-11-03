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
 * 站台 PIDS。左右两栏，照参考图：
 *
 * <pre>
 * +----------------+------------------------+
 * | 本次列车开往 海傍| 12:34                  |  左：黄带；右：蓝带时间
 * | 列车进站        |                        |
 * | 下一趟开往 海傍  |     通知与提示区        |
 * | 2 分钟          |                        |
 * | 第三趟开往 番禺  |                        |
 * | 5 分钟          | 滚动固定提示区          |  右下：蓝带滚动
 * +----------------+------------------------+
 * </pre>
 *
 * 环线（{@code LineMode != NORMAL}）时左带显示「内环 / 外环」而不是终点站；
 * 若该趟车终点站与环别的默认终点不同（小交路、回库车），仍显示「开往 终点站」。
 *
 * <p>坐标：{@code BERLabel.position} 是方框左上角，字向下画，行高 8×scale。
 * 字号按参考富文本标定：0.30 ≈ 正常小字（汉字 9px×0.30 = 2.7px）。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel leftStrip = new BERLabel();
    private final BERLabel leftHead = new BERLabel();
    private final BERLabel leftDest = new BERLabel();
    private final BERLabel status = new BERLabel();
    private final BERLabel nextLabel = new BERLabel();
    private final BERLabel nextDest = new BERLabel();
    private final BERLabel nextMin = new BERLabel();
    private final BERLabel thirdLabel = new BERLabel();
    private final BERLabel thirdDest = new BERLabel();
    private final BERLabel thirdMin = new BERLabel();

    private final BERLabel rightStripTop = new BERLabel();
    private final BERLabel clock = new BERLabel();
    private final BERLabel notice = new BERLabel();
    private final BERLabel rightStripBottom = new BERLabel();
    private final BERLabel marquee = new BERLabel();

    public BERCtdPlatform() {
        setup(leftHead, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(leftDest, ETextAlignment.RIGHT, 0xFFFFFFFF);
        setup(status, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(nextLabel, ETextAlignment.LEFT, 0xFFCFD8DC);
        setup(nextDest, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(nextMin, ETextAlignment.LEFT, 0xFFECEFF1);
        setup(thirdLabel, ETextAlignment.LEFT, 0xFFCFD8DC);
        setup(thirdDest, ETextAlignment.LEFT, 0xFF66BB6A);
        setup(thirdMin, ETextAlignment.LEFT, 0xFFECEFF1);
        setup(clock, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(notice, ETextAlignment.LEFT, 0xFFB0BEC5);
        setup(marquee, ETextAlignment.LEFT, 0xFFFFFFFF);
        marquee.horizontalScrollMode.set(EScrollMode.ALWAYS);

        for (BERLabel l : new BERLabel[]{leftStrip, rightStripTop, rightStripBottom}) {
            l.fullBackground.set(true);
            l.verticalScrollMode.set(EScrollMode.NEVER);
        }
    }

    private static void setup(BERLabel l, ETextAlignment align, int argb) {
        l.horizontalAlign.set(align);
        l.horizontalScrollMode.set(EScrollMode.NEVER);
        l.verticalScrollMode.set(EScrollMode.NEVER);
        l.color.set(DLColor.fromInt(argb));
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();

        for (BERLabel l : new BERLabel[]{leftStrip, rightStripTop, rightStripBottom,
                leftHead, leftDest, status, nextLabel, nextDest, nextMin,
                thirdLabel, thirdDest, thirdMin, clock, notice, marquee}) {
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
        DLColor accent = lineColor(settings);
        DLColor blue = DLColor.fromInt(0xFF1E4FA3);
        boolean waiting = blockEntity.isWaitingAtStation();

        clear();

        String dest0 = nameOf(stops, 0);
        String dest1 = nameOf(stops, 1);
        String dest2 = nameOf(stops, 2);
        String min0 = minutesOf(stops, now, 0);
        String min1 = minutesOf(stops, now, 1);
        String min2 = minutesOf(stops, now, 2);

        // 环线：默认显示环别；小交路/回库（终点站与环别不符）仍显示终点站
        String headDest = dest0;
        if (settings.lineMode() != CtdPlatformSettings.LineMode.NORMAL && !isShortRouting(stops)) {
            headDest = settings.lineMode().zh();
        }

        String time = currentClock(lvl);

        if (bw >= 3 && bh >= 2) {
            layout(area, 20f, 48f,
                accent, blue, time, waiting, headDest, dest1, dest2, min1, min2, 0.30f);
            return;
        }

        if (bh >= 2) {
            layout(area, 14f, 32f,
                accent, blue, time, waiting, headDest, dest1, dest2, min1, min2, 0.24f);
            return;
        }

        // 1 格高：暂为简版（屏蔽门上方版式稍后另做）
        paintStrip(leftStrip, area, 0, 0, bw * 16, 5, accent);
        put(leftHead, area, 2, 1f, bw * 16 - 4, 3, 0.24f, headDest, 0xFFFFFFFF);
        put(status, area, 2, 6f, bw * 16 - 4, 6, 0.46f, min0 + " 分钟", 0xFFFF5252);
    }

    /** 左右两栏布局。leftW 为左栏宽，全部坐标按参考图比例写死。 */
    private void layout(Rectangle area, float leftW, float panelW,
                        DLColor accent, DLColor blue, String time, boolean waiting,
                        String headDest, String dest1, String dest2,
                        String min1, String min2, float base) {

        float band = 6f;
        float rightX = leftW + 1f;
        float rightW = panelW - rightX - 1f;

        // ── 左栏 ──
        paintStrip(leftStrip, area, 0, 0, leftW, band, accent);
        put(leftHead, area, 1.5f, 1f, leftW * 0.55f, 4, base * 0.72f, "本次列车开往", 0xFFFFFFFF);
        put(leftDest, area, leftW * 0.5f, 1f, leftW * 0.5f - 2f, 4, base * 0.80f, headDest, 0xFFFFFFFF);

        put(status, area, 1.5f, band + 1f, leftW - 3f, 6, base * 1.55f,
            waiting ? "列车进站" : "本次列车", 0xFFFFFFFF);

        put(nextLabel, area, 1.5f, band + 8f, leftW - 3f, 3, base * 0.70f, "下一趟开往", 0xFFCFD8DC);
        put(nextDest, area, 1.5f, band + 12f, leftW - 3f, 4, base * 0.90f, dest1, 0xFFFFFFFF);
        put(nextMin, area, 1.5f, band + 16f, leftW - 3f, 4, base * 0.85f, min1 + " 分钟", 0xFFECEFF1);

        put(thirdLabel, area, 1.5f, band + 20f, leftW - 3f, 3, base * 0.70f, "第三趟开往", 0xFFCFD8DC);
        put(thirdDest, area, 1.5f, band + 24f, leftW - 3f, 4, base * 0.90f, dest2, 0xFF66BB6A);
        put(thirdMin, area, 1.5f, band + 28f, leftW - 3f, 4, base * 0.85f, min2 + " 分钟", 0xFFECEFF1);

        // ── 右栏 ──
        paintStrip(rightStripTop, area, rightX, 0, rightW, band, blue);
        put(clock, area, rightX + 1.5f, 1f, rightW - 3f, 4, base * 0.75f, time, 0xFFFFFFFF);

        put(notice, area, rightX + 1.5f, band + 8f, rightW - 3f, 8, base * 0.70f,
            "通知与提示区", 0xFFB0BEC5);

        paintStrip(rightStripBottom, area, rightX, 28f, rightW, 4f, blue);
        put(marquee, area, rightX + 1.5f, 28.5f, rightW - 3f, 3f, base * 0.60f,
            "感谢您的配合，尊敬的乘客。", 0xFFFFFFFF);
    }

    // ── 原语 ──────────────────────────────────────────────────────────

    private static void paintStrip(BERLabel strip, Rectangle area, float x, float y,
                                   float w, float h, DLColor color) {
        strip.clippingArea.set(area);
        strip.text.set(TextUtils.text(" "));
        strip.preferredWidth.set(Math.max(1f, w));
        strip.preferredHeight.set(Math.max(1f, h));
        strip.position.set(Point.of(x, y));
        strip.horizontalMinScale.set(0.1f);
        strip.horizontalMaxScale.set(0.1f);
        strip.verticalMinScale.set(0.1f);
        strip.verticalMaxScale.set(0.1f);
        strip.backgroundColor.set(color);
        strip.fullBackground.set(true);
    }

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

    // ── 数据 ──────────────────────────────────────────────────────────

    /** 小交路/回库：该趟车终点站与环线默认终点不同。 */
    private static boolean isShortRouting(java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops) {
        return stops.isEmpty() || stops.get(0).terminus();
    }

    private static String currentClock(Level level) {
        if (level == null) return "--:--";
        long dayTime = level.getDayTime() % 24000L;
        long hours = (dayTime / 1000L + 6L) % 24L;
        long minutes = (dayTime % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
    }

    private void clear() {
        for (BERLabel l : new BERLabel[]{leftStrip, rightStripTop, rightStripBottom,
                leftHead, leftDest, status, nextLabel, nextDest, nextMin,
                thirdLabel, thirdDest, thirdMin, clock, notice, marquee}) {
            l.text.set(null);
        }
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
