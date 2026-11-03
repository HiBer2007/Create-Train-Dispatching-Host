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
 * 站台 PIDS：左右两栏 + 分割线，全部组件独立绘制。
 *
 * <pre>
 * +------------------+---------------------------+
 * | 本次列车开往 海傍 |                    12:34 |  左黄带 / 右蓝带
 * +------------------+---------------------------+
 * | 列车进站                                     |
 * +------------------+---------------------------+
 * | 下一趟开往                                   |
 * | 海傍     2 分钟    |      通知与提示区         |
 * +------------------+---------------------------+
 * | 第三趟开往                                   |
 * | 番禺广场 5 分钟    |                           |
 * +------------------+---------------------------+
 * |                   滚动固定提示区             |  底部蓝带
 * +----------------------------------------------+
 * </pre>
 *
 * 坐标系：{@code position} 是方框左上角，字向下画，行高 8×scale；
 * 分割线用高度 0.6px 的实心条绘制。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel stripLeft = new BERLabel();
    private final BERLabel stripTopRight = new BERLabel();
    private final BERLabel stripBottom = new BERLabel();
    private final BERLabel div1 = new BERLabel();
    private final BERLabel div2 = new BERLabel();
    private final BERLabel div3 = new BERLabel();

    private final BERLabel headLabel = new BERLabel();
    private final BERLabel headDest = new BERLabel();
    private final BERLabel status = new BERLabel();
    private final BERLabel nextLabel = new BERLabel();
    private final BERLabel nextDest = new BERLabel();
    private final BERLabel nextMin = new BERLabel();
    private final BERLabel thirdLabel = new BERLabel();
    private final BERLabel thirdDest = new BERLabel();
    private final BERLabel thirdMin = new BERLabel();

    private final BERLabel clock = new BERLabel();
    private final BERLabel notice = new BERLabel();
    private final BERLabel marquee = new BERLabel();

    private static final BERLabel[] ALL_STRIPS = new BERLabel[6];

    public BERCtdPlatform() {
        setup(headLabel, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(headDest, ETextAlignment.RIGHT, 0xFFFFFFFF);
        setup(status, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(nextLabel, ETextAlignment.LEFT, 0xFF9AA5AD);
        setup(nextDest, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(nextMin, ETextAlignment.LEFT, 0xFFECEFF1);
        setup(thirdLabel, ETextAlignment.LEFT, 0xFF9AA5AD);
        setup(thirdDest, ETextAlignment.LEFT, 0xFF66BB6A);
        setup(thirdMin, ETextAlignment.LEFT, 0xFFECEFF1);
        setup(clock, ETextAlignment.RIGHT, 0xFFFFFFFF);
        setup(notice, ETextAlignment.LEFT, 0xFF9AA5AD);
        setup(marquee, ETextAlignment.LEFT, 0xFFFFFFFF);
        marquee.horizontalScrollMode.set(EScrollMode.ALWAYS);

        for (BERLabel l : new BERLabel[]{stripLeft, stripTopRight, stripBottom, div1, div2, div3}) {
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
        for (BERLabel l : all()) {
            if (l.text.get() != null) l.render(graphics, light);
        }
    }

    private BERLabel[] all() {
        return new BERLabel[]{stripLeft, stripTopRight, stripBottom, div1, div2, div3,
            headLabel, headDest, status, nextLabel, nextDest, nextMin,
            thirdLabel, thirdDest, thirdMin, clock, notice, marquee};
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
        boolean waiting = blockEntity.isWaitingAtStation();

        clear();

        String dest0 = nameOf(stops, 0);
        String dest1 = nameOf(stops, 1);
        String dest2 = nameOf(stops, 2);
        String min1 = minutesOf(stops, now, 1);
        String min2 = minutesOf(stops, now, 2);
        String headDestText = settings.lineMode() == CtdPlatformSettings.LineMode.NORMAL
            ? dest0 : settings.lineMode().zh();

        if (bw >= 3 && bh >= 2) {
            twoColumn(area, 20f, 48f, settings, waiting, headDestText, dest1, dest2, min1, min2,
                currentClock(lvl));
            return;
        }
        if (bh >= 2) {
            twoColumn(area, 14f, 32f, settings, waiting, headDestText, dest1, dest2, min1, min2,
                currentClock(lvl));
            return;
        }

        // 单格高：屏蔽门上方简版（后续按实拍另做）
        paint(stripLeft, area, 0, 0, bw * 16f, 5f, lineColor(settings));
        put(headLabel, area, 1.5f, 1f, bw * 16f - 3f, 3f, 0.22f, headDestText, 0xFFFFFFFF);
        put(status, area, 1.5f, 6.5f, bw * 16f - 3f, 5f, 0.36f,
            minutesOf(stops, now, 0) + " 分钟", 0xFFFF5252);
    }

    /** 左右两栏，所有坐标写死。 */
    private void twoColumn(Rectangle area, float leftW, float panelW,
                           CtdPlatformSettings settings, boolean waiting,
                           String headDestText, String dest1, String dest2,
                           String min1, String min2, String time) {

        DLColor accent = lineColor(settings);
        DLColor blue = DLColor.fromInt(0xFF1E4FA3);
        DLColor divider = DLColor.fromInt(0xFF3A3F44);

        float rightX = leftW + 1f;
        float rightW = panelW - rightX - 1f;
        float bandH = 5f;
        float s = panelW >= 48 ? 1.0f : 0.86f;   // 2x2 相对 3x2 略小

        // 顶部两条带
        paint(stripLeft, area, 0, 0, leftW, bandH, accent);
        paint(stripTopRight, area, rightX, 0, rightW, bandH, blue);

        // 左栏
        put(headLabel, area, 1.2f, 1f, leftW * 0.52f, 3f, 0.15f * s + 0.02f, "本次列车开往", 0xFFFFFFFF);
        put(headDest, area, leftW * 0.52f, 1f, leftW * 0.48f - 1.5f, 3f, 0.17f * s + 0.02f,
            headDestText, 0xFFFFFFFF);

        put(status, area, 1.2f, bandH + 1.5f, leftW - 2.4f, 5f, 0.32f * s,
            waiting ? "列车进站" : "本次列车", 0xFFFFFFFF);

        paint(div1, area, 0, bandH + 7.5f, leftW, 0.6f, divider);

        put(nextLabel, area, 1.2f, bandH + 8.5f, leftW - 2.4f, 3f, 0.14f * s + 0.02f,
            "下一趟开往", 0xFF9AA5AD);
        put(nextDest, area, 1.2f, bandH + 11.5f, leftW - 2.4f, 3f, 0.17f * s + 0.02f,
            dest1, 0xFFFFFFFF);
        put(nextMin, area, 1.2f, bandH + 14.5f, leftW - 2.4f, 3f, 0.16f * s + 0.02f,
            min1 + " 分钟", 0xFFECEFF1);

        paint(div2, area, 0, bandH + 18f, leftW, 0.6f, divider);

        put(thirdLabel, area, 1.2f, bandH + 19f, leftW - 2.4f, 3f, 0.14f * s + 0.02f,
            "第三趟开往", 0xFF9AA5AD);
        put(thirdDest, area, 1.2f, bandH + 22f, leftW - 2.4f, 3f, 0.17f * s + 0.02f,
            dest2, 0xFF66BB6A);
        put(thirdMin, area, 1.2f, bandH + 25f, leftW - 2.4f, 3f, 0.16f * s + 0.02f,
            min2 + " 分钟", 0xFFECEFF1);

        // 右栏
        put(clock, area, rightX + 1.2f, 1f, rightW - 2.4f, 3f, 0.16f * s + 0.02f, time, 0xFFFFFFFF);
        put(notice, area, rightX + 1.2f, 12f, rightW - 2.4f, 4f, 0.14f * s + 0.02f,
            "通知与提示区", 0xFF9AA5AD);

        paint(stripBottom, area, rightX, 28f, rightW, 4f, blue);
        put(marquee, area, rightX + 1.2f, 28.5f, rightW - 2.4f, 3f, 0.13f * s + 0.02f,
            "感谢您的配合，尊敬的乘客。", 0xFFFFFFFF);

        paint(div3, area, rightX, bandH, rightW, 0.6f, divider);
    }

    // ── 原语 ──────────────────────────────────────────────────────────

    private static void paint(BERLabel strip, Rectangle area, float x, float y,
                              float w, float h, DLColor color) {
        strip.clippingArea.set(area);
        strip.text.set(TextUtils.text(" "));
        strip.preferredWidth.set(Math.max(1f, w));
        strip.preferredHeight.set(Math.max(0.2f, h));
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
        label.preferredHeight.set(Math.max(0.2f, h));
        label.position.set(Point.of(x, y));
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
        label.color.set(DLColor.fromInt(argb));
    }

    // ── 数据 ──────────────────────────────────────────────────────────

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

    private static String currentClock(Level level) {
        if (level == null) return "--:--";
        long dayTime = level.getDayTime() % 24000L;
        long hours = (dayTime / 1000L + 6L) % 24L;
        long minutes = (dayTime % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
    }

    private void clear() {
        for (BERLabel l : all()) l.text.set(null);
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
