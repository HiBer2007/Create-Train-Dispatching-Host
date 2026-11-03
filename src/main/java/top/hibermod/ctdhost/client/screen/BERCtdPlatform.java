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
    private String headLine2Text = "车开往";
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

    /** 1 格高版式专用的标签池，避免复用导致互相覆盖。 */
    private final BERLabel[] pool = new BERLabel[16];

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
        for (BERLabel l : pool) {
            if (l != null && l.text.get() != null) l.render(graphics);
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
        boolean loopExit = blockEntity instanceof CtdHoldAccess acc3 && acc3.ctdgetLoopExit();

        clear();

        String dest0 = nameOf(stops, 0);
        String dest1 = nameOf(stops, 1);
        String dest2 = nameOf(stops, 2);
        String min1 = minutesOf(stops, now, 1);
        String min2 = minutesOf(stops, now, 2);
        String headDestText = settings.lineMode() == CtdPlatformSettings.LineMode.NORMAL
            ? dest0 : settings.lineMode().zh();
        boolean loop = settings.lineMode() != CtdPlatformSettings.LineMode.NORMAL;
        if (loop) {
            headDestText = loopExit ? dest0 : settings.lineMode().zh();
        }
        String headLabelText = loop && !loopExit ? "本次列车方向" : "本次列车开往";
        headLine2Text = loop && !loopExit ? "车方向" : "车开往";
        String nextLabelText = loop && !loopExit ? "下一趟方向" : "下一趟开往";
        String thirdLabelText = loop && !loopExit ? "第三趟方向" : "第三趟开往";

        if (bw >= 3 && bh >= 2) {
            twoColumn(blockEntity, settings, waiting, headDestText, dest1, dest2, min1, min2,
                realClockText() + " " + currentClock(lvl), 19f, 46f, 1.0f, stops, now, noBoard, noticeText, headLabelText, nextLabelText, thirdLabelText);
            return;
        }
        if (bh >= 2) {
            singleColumn(blockEntity, settings, waiting, headDestText, dest1, dest2, min1, min2,
                noBoard, headLabelText, nextLabelText, thirdLabelText, stops, now, currentClock(lvl));
            return;
        }

        // 1 格高：4x1 两方向拼接，2x1 单方向
        if (bw >= 4) {
            rowLayout(blockEntity, settings, stops, now, waiting, noBoard, true);
        } else {
            rowLayout(blockEntity, settings, stops, now, waiting, noBoard, false);
        }
    }

    /**
     * 1 格高的版式（2x1 / 4x1），照参考图：
     * 左半上方是线路色带（本次列车开往 + 终点站），右上蓝带是站台 ID 或时钟，
     * 中间左侧大字「列车进站」，右侧「下一趟开往 / 站名 / N 分钟」，底部整条蓝带。
     */
    private void rowLayout(AdvancedDisplayBlockEntity be, CtdPlatformSettings settings,
                           java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                           long now, boolean waiting, boolean noBoard, boolean dual) {

        DLColor accent = lineColor(settings);
        DLColor blue = DLColor.fromInt(0xFF1E4FA3);
        var clip = Rectangle.withSize(2, 2, be.getXSizeScaled() * 16 - 4, be.getYSizeScaled() * 16 - 4);

        float totalW = be.getXSizeScaled() * 16 - 4f;
        float halfW = dual ? (totalW - 1f) / 2f : totalW;

        String time = be.getLevel() == null ? "--:--" : currentClock(be.getLevel());
        String statusStr = noBoard ? "请勿上车"
            : (waiting ? "列车进站" : statusText(waiting, minutesOf(stops, now, 0), now, stops));

        boolean loop = settings.lineMode() != CtdPlatformSettings.LineMode.NORMAL;
        boolean loopExit = be instanceof CtdHoldAccess acc && acc.ctdgetLoopExit();
        String smallText = loop && !loopExit ? "本次列车方向" : "本次列车开往";

        // 4x1 两半之间的竖分割条
        if (dual) {
            rects.add(new Rect(2f + halfW - 0.2f, 2f, 0.02f, 0.4f, 10.2f,
                DLColor.fromInt(0xFF9AA5AD)));
        }

        for (int i = 0; i < (dual ? 2 : 1); i++) {
            float hx = 2f + i * (halfW + 1f);
            int idx = dual ? i : 0;
            int base = i * 8;

            // 上方色带：线路色与蓝色各半
            float yellowW = halfW * 0.50f;
            rects.add(new Rect(hx, 2f, 0f, yellowW, 4f, accent));
            rects.add(new Rect(hx + yellowW + 0.4f, 2f, 0f, halfW - yellowW - 0.4f, 4f, blue));

            // 黄带：两行三字靠左 + 终点站居中于剩余空间
            BERLabel line1 = lab(base);
            BERLabel line2 = lab(base + 1);
            BERLabel dest = lab(base + 3);
            BERLabel clockLab = lab(base + 2);

            line1.horizontalAlign.set(ETextAlignment.LEFT);
            line2.horizontalAlign.set(ETextAlignment.LEFT);
            float titleScale = 0.16f;                 // 与 3x2 同大
            float titleLineH = 8f * titleScale;
            float titleTop = 2 + (4f - titleLineH * 2f) / 2f;
            put(line1, clip, hx + 0.6f, titleTop, 8f, titleLineH + 0.2f, titleScale,
                smallText.substring(0, 3), contrastText(accent));
            put(line2, clip, hx + 0.6f, titleTop + titleLineH, 8f, titleLineH + 0.2f, titleScale,
                headLine2Text, contrastText(accent));

            dest.horizontalAlign.set(ETextAlignment.CENTER);
            String destText = loop && !loopExit ? settings.lineMode().zh() : nameOf(stops, idx);
            put(dest, clip, hx + 8.4f, 2 + (4f - 8f * 0.18f) / 2f, yellowW - 8.8f, 3f, 0.18f,
                destText, contrastText(accent));

            // 蓝带：4x1 站台 ID（线路色） / 2x1 时钟（右对齐）
            clockLab.horizontalAlign.set(dual ? ETextAlignment.CENTER : ETextAlignment.RIGHT);
            String rightText = dual ? (i == 0 ? settings.platform() : settings.platform2()) : time;
            put(clockLab, clip, hx + yellowW + 0.4f, 2 + (4f - 8f * 0.18f) / 2f,
                halfW - yellowW - 0.8f, 3f, 0.18f, rightText,
                dual ? accent.getAsARGB() : 0xFFFFFFFF);

            // 中部左：大字状态
            BERLabel state = lab(base + 4);
            state.horizontalAlign.set(ETextAlignment.CENTER);
            float stateScale = 0.32f;
            put(state, clip, hx, 6f + (6.4f - 8f * stateScale) / 2f, halfW * 0.46f,
                8f * stateScale + 0.3f, stateScale, statusStr,
                noBoard ? 0xFFFF9800 : 0xFFFFFFFF);

            // 中部右：两行（标签 / 终点站 + 倒计时同行），整体垂直居中
            float rowTop = 6f + (6.4f - 2f * 2.1f) / 2f;
            BERLabel nextLbl = lab(base + 5);
            nextLbl.horizontalAlign.set(ETextAlignment.LEFT);
            put(nextLbl, clip, hx + halfW * 0.5f, rowTop, halfW * 0.5f, 2f, 0.15f, "下一趟开往",
                0xFF9AA5AD);

            BERLabel nextD = lab(base + 6);
            nextD.horizontalAlign.set(ETextAlignment.LEFT);
            put(nextD, clip, hx + halfW * 0.5f, rowTop + 2.1f, halfW * 0.32f, 2f, 0.19f,
                nameOf(stops, idx + 1), 0xFFFFFFFF);

            BERLabel nextM = lab(base + 7);
            nextM.horizontalAlign.set(ETextAlignment.RIGHT);
            put(nextM, clip, hx + halfW * 0.5f + halfW * 0.28f, rowTop + 2.1f, halfW * 0.22f, 2f,
                0.19f, minutesOf(stops, now, idx + 1) + " 分钟", 0xFFFFE082);
        }

        // 底部蓝带（整条）
        rects.add(new Rect(2f, 12.2f, 0f, totalW, 2.8f, blue));

        lab(14).horizontalScrollMode.set(EScrollMode.ALWAYS);
        lab(15).horizontalScrollMode.set(EScrollMode.ALWAYS);

        var barL = Rectangle.withSize(3f, 12.2f,
            dual ? totalW * 0.5f - 4f : totalW - 2f, 3f);
        var barR = Rectangle.withSize(3f + totalW * 0.5f, 12.2f, totalW * 0.5f - 4f, 3f);

        if (dual) {
            put(lab(14), barL, 3f, 12.3f, totalW * 0.5f - 4f, 2.6f, 0.30f, realClockText(),
                0xFFFFFFFF);
            put(lab(15), barR, 3f + totalW * 0.5f, 12.3f, totalW * 0.5f - 4f, 2.6f, 0.30f,
                "感谢您的配合，尊敬的乘客。", 0xFFFFFFFF);
        } else {
            put(lab(14), barL, 3f, 12.3f, totalW - 2f, 2.6f, 0.30f, "感谢您的配合，尊敬的乘客。",
                0xFFFFFFFF);
        }
    }

    /** 左右两栏主体。所有数字都是像素，已含 2px 边距。 */
    private void twoColumn(AdvancedDisplayBlockEntity be, CtdPlatformSettings settings,
                           boolean waiting, String headDestText, String dest1, String dest2,
                           String min1, String min2, String time,
                           float leftRightEdge, float panelRightEdge, float k,
                           java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops, long now,
                           boolean noBoard, String noticeText,
                           String headLabelText, String nextLabelText, String thirdLabelText) {

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

        // 左带：两行三字靠左，剩余宽度全部给终点站（在剩余区间内居中）
        headLabel.horizontalAlign.set(ETextAlignment.LEFT);
        headLabel2.horizontalAlign.set(ETextAlignment.LEFT);
        float headLineH = 8f * 0.18f;
        float headTop = 2 + (bandH - headLineH * 2f) / 2f;
        float headTextW = 8f * 3f;                 // 三个汉字宽 ≈ 24px 封顶
        float headBoxW = Math.min(leftW * 0.46f, headTextW);
        put(headLabel, clip, leftX + 0.8f, headTop, headBoxW, headLineH + 0.4f, 0.16f, "本次列", onAccent);
        put(headLabel2, clip, leftX + 0.8f, headTop + headLineH, headBoxW, headLineH + 0.4f, 0.16f,
            headLine2Text, onAccent);

        headDest.horizontalAlign.set(ETextAlignment.CENTER);
        float destX = leftX + headBoxW + 1.2f;
        put(headDest, clip, destX, 2 + (bandH - 8f * 0.23f) / 2f,
            leftW - headBoxW - 1.2f, 8f * 0.23f + 0.4f, 0.23f, headDestText, onAccent);
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
        nextDest.horizontalAlign.set(ETextAlignment.LEFT);
        put(nextLabel, clip, leftX + 1f, row1 + 0.8f, leftW - 2f, 3f, 0.18f * k, nextLabelText, 0xFF9AA5AD);
        put(nextDest, clip, leftX + 2f, row1 + 4.6f, leftW * 0.5f, 3f, 0.19f * k,
            dest1, 0xFFFFFFFF);

        nextMin.horizontalAlign.set(ETextAlignment.RIGHT);
        put(nextMin, clip, leftX + leftW * 0.5f, row1 + 4.6f, leftW * 0.5f - 2f, 3f, 0.19f * k,
            min1 + " 分钟", 0xFFECEFF1);

        float divMid = divTop + rowBand;
        rects.add(new Rect(leftX, divMid, 0.01f, leftW, 0.3f, divider));

        float row2 = divMid;
        put(thirdLabel, clip, leftX + 1f, row2 + 0.8f, leftW - 2f, 3f, 0.18f * k, thirdLabelText, 0xFF9AA5AD);
        put(thirdDest, clip, leftX + 2f, row2 + 4.6f, leftW * 0.5f, 3f, 0.19f * k,
            dest2, 0xFF66BB6A);

        thirdMin.horizontalAlign.set(ETextAlignment.RIGHT);
        put(thirdMin, clip, leftX + leftW * 0.5f, row2 + 4.6f, leftW * 0.5f - 2f, 3f, 0.19f * k,
            min2 + " 分钟", 0xFFFFE082);


        // 右栏
        float clockScale = 0.22f * k;
        float clockH = 8f * clockScale;
        put(clock, clip, rightX + 1f, 2 + (bandH - clockH) / 2f, rightW - 2f, clockH + 0.3f,
            clockScale, time, 0xFFFFFFFF);
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

    /** 2x2：单栏（无公告区），版式与 3x2 相同但顶到满宽。 */
    private void singleColumn(AdvancedDisplayBlockEntity be, CtdPlatformSettings settings,
                              boolean waiting, String headDestText,
                              String dest1, String dest2, String min1, String min2,
                              boolean noBoard, String headLabelText, String nextLabelText,
                              String thirdLabelText,
                              java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops,
                              long now, String time) {

        DLColor accent = lineColor(settings);
        DLColor divider = DLColor.fromInt(0xFF9AA5AD);
        var clip = Rectangle.withSize(2, 2, be.getXSizeScaled() * 16 - 4, be.getYSizeScaled() * 16 - 4);
        int onAccent = contrastText(accent);

        float leftX = 2f;
        float leftW = be.getXSizeScaled() * 16 - 4f;
        float bandH = 4f;

        // 色带拆两段：黄（线路色）一格多一点，其余蓝（时钟）
        float yellowW = Math.min(leftW - 9f, 18f);
        rects.add(new Rect(leftX, 2, 0f, yellowW, bandH, accent));
        rects.add(new Rect(leftX + yellowW + 0.5f, 2, 0f, leftW - yellowW - 0.5f, bandH,
            DLColor.fromInt(0xFF1E4FA3)));

        // 两行三字靠左 + 终点站居中于剩余空间
        float headLineH = 8f * 0.16f;
        float headTop = 2 + (bandH - headLineH * 2f) / 2f;
        headLabel.horizontalAlign.set(ETextAlignment.LEFT);
        headLabel2.horizontalAlign.set(ETextAlignment.LEFT);
        float headBoxW = Math.min(yellowW * 0.42f, 26f);
        put(headLabel, clip, leftX + 0.6f, headTop, headBoxW, headLineH + 0.3f, 0.16f,
            headLabelText.substring(0, 3), onAccent);
        put(headLabel2, clip, leftX + 0.6f, headTop + headLineH, headBoxW, headLineH + 0.3f, 0.16f,
            headLine2Text, onAccent);

        headDest.horizontalAlign.set(ETextAlignment.CENTER);
        put(headDest, clip, leftX + headBoxW + 1f, 2 + (bandH - 8f * 0.20f) / 2f,
            yellowW - headBoxW - 1.2f, 8f * 0.20f + 0.3f, 0.20f, headDestText, onAccent);

        // 蓝带里的游戏内时钟，垂直居中
        clock.horizontalAlign.set(ETextAlignment.RIGHT);
        float gameClock = 0.20f;
        put(clock, clip, leftX + yellowW + 1f, 2 + (bandH - 8f * gameClock) / 2f,
            leftW - yellowW - 2f, 8f * gameClock + 0.3f, gameClock, time, 0xFFFFFFFF);

        float divTop = 2 + bandH + 5f;
        rects.add(new Rect(leftX, divTop, 0.01f, leftW, 0.3f, divider));

        float statusBand = divTop - (2 + bandH);
        float statusH = 8f * 0.32f;
        status.horizontalAlign.set(ETextAlignment.CENTER);
        put(status, clip, leftX, 2 + bandH + (statusBand - statusH) / 2f, leftW, statusH + 0.4f, 0.32f,
            noBoard ? "请勿上车" : statusText(waiting, min1, now, stops),
            noBoard ? 0xFFFF9800 : 0xFFFFFFFF);

        // 第一趟
        float row1 = divTop + 0.8f;
        nextLabel.horizontalAlign.set(ETextAlignment.LEFT);
        put(nextLabel, clip, leftX + 1f, row1, leftW - 2f, 2f, 0.18f, nextLabelText, 0xFF9AA5AD);
        nextDest.horizontalAlign.set(ETextAlignment.LEFT);
        put(nextDest, clip, leftX + 2f, row1 + 3.4f, leftW * 0.5f, 3f, 0.19f, dest1, 0xFFFFFFFF);
        nextMin.horizontalAlign.set(ETextAlignment.RIGHT);
        put(nextMin, clip, leftX + leftW * 0.5f, row1 + 3.4f, leftW * 0.5f - 2f, 3f, 0.19f,
            min1 + " 分钟", 0xFFECEFF1);

        float divMid = divTop + 9f;
        rects.add(new Rect(leftX, divMid, 0.01f, leftW, 0.3f, divider));

        float row2 = divMid + 0.8f;
        thirdLabel.horizontalAlign.set(ETextAlignment.LEFT);
        put(thirdLabel, clip, leftX + 1f, row2, leftW - 2f, 2f, 0.18f, thirdLabelText, 0xFF9AA5AD);
        thirdDest.horizontalAlign.set(ETextAlignment.LEFT);
        put(thirdDest, clip, leftX + 2f, row2 + 3.4f, leftW * 0.5f, 3f, 0.19f, dest2, 0xFF66BB6A);
        thirdMin.horizontalAlign.set(ETextAlignment.RIGHT);
        put(thirdMin, clip, leftX + leftW * 0.5f, row2 + 3.4f, leftW * 0.5f - 2f, 3f, 0.19f,
            min2 + " 分钟", 0xFFECE682);

        notice.text.set(null);
        marquee.text.set(null);
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

    private BERLabel lab(int i) {
        BERLabel l = pool[i];
        if (l == null) {
            l = new BERLabel();
            setup(l, ETextAlignment.LEFT, 0xFFFFFFFF);
            pool[i] = l;
        }
        return l;
    }

    private BERLabel[] labels() {
        return new BERLabel[]{headLabel, headLabel2, headDest, status, nextLabel, nextDest, nextMin,
            thirdLabel, thirdDest, thirdMin, clock, notice, marquee};
    }

    private void clear() {
        rects.clear();
        for (BERLabel l : labels()) l.text.set(null);
        for (BERLabel l : pool) if (l != null) l.text.set(null);
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

    /** 现实日期 + 时间（3x2 的蓝带用）。 */
    private static String realClockText() {
        var now = java.time.LocalDateTime.now();
        return now.getMonthValue() + "月" + now.getDayOfMonth() + "日 "
            + String.format("%02d:%02d", now.getHour(), now.getMinute());
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
