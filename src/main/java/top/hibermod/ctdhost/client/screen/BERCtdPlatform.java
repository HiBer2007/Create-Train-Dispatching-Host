package top.hibermod.ctdhost.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel.EScrollMode;
import de.mrjulsen.mcdragonlib.client.util.RenderUtils;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 站台 PIDS：左右两栏，色带与分割线用 {@link RenderUtils#fillColor} 画（与原生发车板同一套）。
 *
 * <pre>
 * +-------------+----------------------+
 * |本次列车开往 海傍|              12:34 |  左黄带 / 右蓝带
 * +-------------+----------------------+
 * | 列车进站                            |
 * +-------------+----------------------+
 * | 下一趟开往      |    通知与提示区     |
 * | 海傍   2 分钟   |                    |
 * +-------------+----------------------+
 * | 第三趟开往      |                    |
 * | 番禺广场 5 分钟 |                    |
 * +-------------+----------------------+
 * |              滚动固定提示区          |  底部蓝带
 * +------------------------------------+
 * </pre>
 *
 * 坐标约定：整块内容从 (2,2) 开始，留 2px 边框余量（原生也用
 * {@code Rectangle.withSize(2, 2, w-4, h-4)} 作裁剪区）。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private record Rect(float x, float y, float z, float w, float h, DLColor color) {}

    private final List<Rect> rects = new ArrayList<>();

    private final BERLabel headLabel = new BERLabel();
    private final BERLabel headLabel2 = new BERLabel();
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

    public BERCtdPlatform() {
        setup(headLabel, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(headLabel2, ETextAlignment.LEFT, 0xFFFFFFFF);
        setup(headDest, ETextAlignment.CENTER, 0xFFFFFFFF);
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

        var facing = graphics.blockEntity().getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        for (Rect r : rects) {
            RenderUtils.fillColor(graphics, new Vector3f(r.x(), r.y(), r.z()),
                r.w(), r.h(), r.color(), facing);
        }

        for (BERLabel l : labels()) {
            if (l.text.get() != null) l.render(graphics);
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

        var stops = blockEntity.getStops();
        boolean waiting = blockEntity.isWaitingAtStation();
        boolean noBoard = blockEntity instanceof CtdHoldAccess acc && acc.ctdgetNoBoarding();
        String noticeText = blockEntity instanceof CtdHoldAccess acc2 ? acc2.ctdgetNotice() : "";

        clear();

        String dest0 = nameOf(stops, 0);
        String dest1 = nameOf(stops, 1);
        String dest2 = nameOf(stops, 2);
        String min1 = minutesOf(stops, now, 1);
        String min2 = minutesOf(stops, now, 2);
        String headDestText = settings.lineMode() == CtdPlatformSettings.LineMode.NORMAL
            ? dest0 : settings.lineMode().zh();

        if (bw >= 3 && bh >= 2) {
            twoColumn(blockEntity, settings, waiting, headDestText, dest1, dest2, min1, min2,
                currentClock(lvl), 19f, 46f, 1.0f, stops, now, noBoard, noticeText);
            return;
        }
        if (bh >= 2) {
            twoColumn(blockEntity, settings, waiting, headDestText, dest1, dest2, min1, min2,
                currentClock(lvl), 13f, 30f, 0.84f, stops, now, noBoard, noticeText);
            return;
        }

        // 单格高：简版（屏蔽门上方版式另做）
        var clip = Rectangle.withSize(2, 2, bw * 16 - 4, bh * 16 - 4);
        DLColor accent = lineColor(settings);
        rects.add(new Rect(2, 2, 0f, bw * 16 - 4, 5f, accent));
        put(headLabel, clip, 3, 2.8f, bw * 16 - 6, 3f, 0.20f, headDestText, contrastText(accent));
        put(status, clip, 3, 8f, bw * 16 - 6, 5f, 0.34f,
            minutesOf(stops, now, 0) + " 分钟", 0xFFFF5252);
    }

    /** 左右两栏主体。所有数字都是像素，已含 2px 边距。 */
    private void twoColumn(AdvancedDisplayBlockEntity be, CtdPlatformSettings settings,
                           boolean waiting, String headDestText, String dest1, String dest2,
                           String min1, String min2, String time,
                           float leftRightEdge, float panelRightEdge, float k,
                           java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops, long now,
                           boolean noBoard, String noticeText) {

        DLColor accent = lineColor(settings);
        DLColor blue = DLColor.fromInt(0xFF1E4FA3);
        DLColor divider = DLColor.fromInt(0xFF9AA5AD);

        var clip = Rectangle.withSize(2, 2,
            be.getXSizeScaled() * 16 - 4, be.getYSizeScaled() * 16 - 4);

        float leftX = 2f;
        float leftW = leftRightEdge - 2f;
        float rightX = leftRightEdge + 1f;
        float rightW = panelRightEdge - rightX;

        float bandH = 4f;                 // 色带只留 4px（原来 7px 太高）

        // 亮色线路用黑字，暗色线路用白字
        int onAccent = contrastText(accent);

        // 顶部两条带
        rects.add(new Rect(leftX, 2, 0f, leftW, bandH, accent));
        rects.add(new Rect(rightX, 2, 0f, rightW, bandH, blue));

        // 左带：两行三字「本次列 / 车开往」，左半格内居中且在带内纵向居中
        headLabel.horizontalAlign.set(ETextAlignment.CENTER);
        headLabel2.horizontalAlign.set(ETextAlignment.CENTER);
        float headLineH = 8f * 0.18f;
        float headTop = 2 + (bandH - headLineH * 2f) / 2f;
        put(headLabel, clip, leftX, headTop, leftW * 0.52f, headLineH + 0.4f, 0.18f, "本次列", onAccent);
        put(headLabel2, clip, leftX, headTop + headLineH, leftW * 0.52f, headLineH + 0.4f, 0.18f, "车开往", onAccent);

        headDest.horizontalAlign.set(ETextAlignment.CENTER);
        put(headDest, clip, leftX + leftW * 0.52f, 2 + (bandH - 8f * 0.23f) / 2f,
            leftW * 0.48f - 1f, 8f * 0.23f + 0.4f, 0.23f, headDestText, onAccent);
        float divTop = 2 + bandH + 6f;    // 第一条分割线
        rects.add(new Rect(leftX, divTop, 0.01f, leftW, 0.3f, divider));

        // 状态行：在色带与分割线之间垂直居中
        float statusBand = divTop - (2 + bandH);
        float statusTextH = 8f * 0.32f * k;
        put(status, clip, leftX, 2 + bandH + (statusBand - statusTextH) / 2f, leftW, statusTextH + 0.5f,
            0.32f * k, noBoard ? "请勿上车" : statusText(waiting, min1, now, stops),
            noBoard ? 0xFFFF9800 : 0xFFFFFFFF);
        status.horizontalAlign.set(ETextAlignment.CENTER);

        // 两趟车：每趟一行（左标签 / 右值），各自在自己的分割线区间内垂直居中
        float rowBand = 9f;
        float valH = 8f * 0.17f * k;
        float row1 = divTop;
        nextLabel.horizontalAlign.set(ETextAlignment.LEFT);
        nextDest.horizontalAlign.set(ETextAlignment.RIGHT);
        put(nextLabel, clip, leftX + 1f, row1 + 0.8f, leftW - 2f, 3f, 0.18f * k, "下一趟开往", 0xFF9AA5AD);
        put(nextDest, clip, leftX + 1f, row1 + 4.6f, leftW * 0.6f, 3f, 0.19f * k,
            dest1, 0xFFFFFFFF);

        nextMin.horizontalAlign.set(ETextAlignment.RIGHT);
        put(nextMin, clip, leftX + leftW * 0.6f, row1 + 4.6f, leftW * 0.4f - 1f, 3f, 0.19f * k,
            min1 + " 分钟", 0xFFECEFF1);

        float divMid = divTop + rowBand;
        rects.add(new Rect(leftX, divMid, 0.01f, leftW, 0.3f, divider));

        float row2 = divMid;
        put(thirdLabel, clip, leftX + 1f, row2 + 0.8f, leftW - 2f, 3f, 0.18f * k, "第三趟开往", 0xFF9AA5AD);
        put(thirdDest, clip, leftX + 1f, row2 + 4.6f, leftW * 0.6f, 3f, 0.19f * k,
            dest2, 0xFF66BB6A);

        thirdMin.horizontalAlign.set(ETextAlignment.RIGHT);
        put(thirdMin, clip, leftX + leftW * 0.6f, row2 + 4.6f, leftW * 0.4f - 1f, 3f, 0.19f * k,
            min2 + " 分钟", 0xFFFFE082);


        // 右栏
        put(clock, clip, rightX + 1f, 2.6f, rightW - 2f, 3f, 0.22f * k, time, 0xFFFFFFFF);
        // 公告：调度没发布就留空，不显示占位
        if (noticeText != null && !noticeText.isBlank()) {
            notice.horizontalScrollMode.set(EScrollMode.ALWAYS);
            put(notice, clip, rightX + 1f, 10f, rightW - 2f, 4f, 0.12f * k, noticeText, 0xFFECEFF1);
        } else {
            notice.text.set(null);
        }

        float bottomY = be.getYSizeScaled() * 16 - 6f;
        rects.add(new Rect(rightX, bottomY, 0f, rightW, 4f, blue));
        put(marquee, clip, rightX + 1f, bottomY + 0.6f, rightW - 2f, 3.4f, 0.38f * k,
            "感谢您的配合，尊敬的乘客。", 0xFFFFFFFF);
    }

    // ── 原语 ──────────────────────────────────────────────────────────

    private static void put(BERLabel label, Rectangle clip, float x, float y,
                            float w, float h, float s, String content, int argb) {
        label.clippingArea.set(clip);
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

    private BERLabel[] labels() {
        return new BERLabel[]{headLabel, headLabel2, headDest, status, nextLabel, nextDest, nextMin,
            thirdLabel, thirdDest, thirdMin, clock, notice, marquee};
    }

    private void clear() {
        rects.clear();
        for (BERLabel l : labels()) l.text.set(null);
    }

    // ── 数据 ──────────────────────────────────────────────────────────

    /** 主状态：进站 / 即将到达 / 剩余 N 分钟。 */
    private static String statusText(boolean waiting, String minNext,
                                     long now,
                                     java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops) {
        if (waiting) return "列车进站";
        if (stops.isEmpty()) return "暂无列车";
        String m0 = minutesOf(stops, now, 0);
        if ("--".equals(m0)) return "暂无列车";
        try {
            int m = Integer.parseInt(m0);
            if (m <= 1) return "即将到达";
            return m + " 分钟";
        } catch (NumberFormatException e) {
            return "即将到达";
        }
    }

    private static String nameOf(java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops, int i) {
        if (i >= stops.size()) return "--";
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

    /** 亮色底用黑字、暗色底用白字（按相对亮度）。 */
    private static int contrastText(DLColor background) {
        int argb = background.getAsARGB();
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        return luminance > 0.55 ? 0xFF101010 : 0xFFFFFFFF;
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
