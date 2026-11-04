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
 * CTD 屏蔽门挡板：装在站台门上方的长条屏。背景固定白色，主色为线路颜色。
 *
 * <p>四种版式按尺寸自动选择：
 * <ul>
 *   <li>4x1：整屏线路图（无指示灯）</li>
 *   <li>3x1：线路图 或 指示灯（由设置决定），无图时为「站名 + 下一站 + 站台号」版</li>
 *   <li>2x1：只有指示灯，无线路图</li>
 * </ul>
 *
 * <p>「站台号」与屏蔽门联动：门号来自设置，开关状态来自
 * {@code top.hibermod.ctdapi.status.DoorStates}（调度系统下发）。
 */
public class BERCtdPsd implements AbstractAdvancedDisplayRenderer<CtdPsdSettings> {

    private record Rect(float x, float y, float z, float w, float h, DLColor color) {}

    /** 一次性诊断开关。 */
    private static final Once DEBUG = new Once();

    private static final class Once {
        private boolean done;
        synchronized boolean once() {
            if (done) return false;
            done = true;
            return true;
        }
    }

    private final List<Rect> rects = new ArrayList<>();

    private final BERLabel name = new BERLabel();
    private final BERLabel next = new BERLabel();
    private final BERLabel doorNo = new BERLabel();
    private final BERLabel doorUnit = new BERLabel();
    private final BERLabel doorUnit2 = new BERLabel();
    private final BERLabel stationId = new BERLabel();
    private final BERLabel[] mapLabels = new BERLabel[24];

    public BERCtdPsd() {
        setup(name, ETextAlignment.CENTER, 0xFF000000);
        setup(next, ETextAlignment.LEFT, 0xFF000000);
        setup(doorNo, ETextAlignment.CENTER, 0xFF000000);
        setup(doorUnit, ETextAlignment.CENTER, 0xFF000000);
        setup(doorUnit2, ETextAlignment.LEFT, 0xFF000000);
        setup(stationId, ETextAlignment.CENTER, 0xFF000000);
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
        // 文字整体抬到色块前面，否则圆章/色带会盖住字（原生发车板也是这么做的）
        graphics.poseStack().pushPose();
        graphics.poseStack().translate(0, 0, 0.08f);
        for (BERLabel l : labels()) {
            if (l != null && l.text.get() != null) l.render(graphics);
        }
        graphics.poseStack().popPose();
    }
    private BERLabel[] labels() {
        BERLabel[] all = new BERLabel[6 + mapLabels.length];
        all[0] = name;
        all[1] = next;
        all[2] = doorNo;
        all[3] = doorUnit;
        all[4] = stationId;
        all[5] = doorUnit2;
        System.arraycopy(mapLabels, 0, all, 6, mapLabels.length);
        return all;
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        CtdPsdSettings s = settingsOf(blockEntity);
        int bw = Math.max(1, (int) blockEntity.getXSizeScaled());
        int bh = Math.max(1, (int) blockEntity.getYSizeScaled());
        float w = bw * 16f;
        float h = bh * 16f;
        Rectangle clip = Rectangle.withSize(1, 1, w - 2, h - 2);

        clear();

        DLColor line = BERCtdPlatform.lineColor(s.lineName(), s.lineColor());
        int onLine = contrast(line);
        DLColor white = DLColor.fromInt(0xFFFFFFFF);

        // 白底（必须）
        rects.add(new Rect(0, 0, 0f, w, h, white));

        boolean open = isDoorOpen(blockEntity, s);

        // 4x1 或（3x1 且选了线路图）：画「下一趟列车」的运行路线
        if (bw >= 4 || (bw == 3 && s.showMap())) {
            if (bw >= 4) {
                float half = (w - 1f) / 2f;
                drawRoute(clip, blockEntity, s.platform(), s, line, 0f, half, h);
                rects.add(new Rect(half, 1f, 0.02f, 0.4f, h - 5f, line));
                drawRoute(clip, blockEntity, s.platform2(), s, line, half + 1f, half, h);
            } else {
                drawRoute(clip, blockEntity, s.platform(), s, line, 0f, w, h);
            }
            rects.add(new Rect(0, h - 3f, 0.01f, w, 3f, line));
            return;
        }

        // 站名版式（2x1 / 3x1 无图）
        float bottomBar = 2.8f;
        float contentBottom = h - bottomBar;

        boolean known = !s.stationId().isBlank() && !s.stationName().isBlank();
        String nameText = known ? s.stationName() : "未知车站";
        String nextText = s.nextStation().isBlank() ? "未知下一站" : s.nextStation();
        String arrow = s.arrowRight() ? "→" : "←";

        // 右栏留给圆章，左栏放站名与下一站
        float rightW = bw >= 3 ? 13f : 10.5f;
        float leftW = w - rightW - 2f;

        // 站名：左栏居中，字号按「4 个汉字刚好放进左栏」算
        float nameScale = Math.min(0.52f, leftW / (4f * 9f) * 0.95f);
        float nameH = 8f * nameScale;
        name.horizontalAlign.set(ETextAlignment.CENTER);
        put(name, clip, 0f, 3.4f, w, nameH + 0.4f, nameScale, nameText, 0xFF000000);

        // 下一站：站名下方，左对齐
        float nextScale = Math.min(0.20f, leftW / (9f * 9f) * 0.95f);
        next.horizontalAlign.set(ETextAlignment.LEFT);
        put(next, clip, 1.5f, 3.4f + nameH + 1.2f, leftW, 8f * nextScale + 0.4f, nextScale,
            "下一站：" + nextText + " " + arrow, 0xFF000000);

        // 右栏：线路色圆章 = 站台号；下面「站台」；再下面是车站号
        float badgeSize = bw >= 3 ? 6.5f : 5.5f;
        float bx = w - badgeSize - 2.5f;
        float by = 1.6f;
        rects.add(new Rect(bx, by + badgeSize * 0.18f, 0.02f, badgeSize, badgeSize * 0.64f, line));
        rects.add(new Rect(bx + badgeSize * 0.12f, by, 0.02f, badgeSize * 0.76f, badgeSize, line));

        String platformText = s.platform().isBlank() ? "--" : s.platform();
        float noScale = Math.min(0.34f, badgeSize / (2f * 9f) * 1.6f);
        doorNo.horizontalAlign.set(ETextAlignment.CENTER);
        put(doorNo, clip, bx - 0.4f, by + (badgeSize - 8f * noScale) / 2f, badgeSize + 0.8f,
            8f * noScale + 0.4f, noScale, platformText, onLine);

        doorUnit.horizontalAlign.set(ETextAlignment.CENTER);
        put(doorUnit, clip, bx - 1f, by + badgeSize + 0.6f, badgeSize + 2f, 2.4f, 0.16f,
            "站台", 0xFF000000);

        stationId.horizontalAlign.set(ETextAlignment.CENTER);
        put(stationId, clip, bx - 1.5f, by + badgeSize + 3.4f, badgeSize + 3f, 2.4f, 0.14f,
            s.station().isBlank() ? "--" : s.station(), 0xFF000000);

        // 屏蔽门号：紧贴指示灯上方
        doorUnit2.horizontalAlign.set(ETextAlignment.CENTER);
        put(doorUnit2, clip, 0f, contentBottom - 2.3f, w, 2.2f, 0.14f,
            "屏蔽门 " + (s.doorNumber().isBlank() ? "1" : s.doorNumber()), 0xFF000000);

        // 一次性打印实际取值，便于确认字段到底有没有存进来
        if (DEBUG.once()) {
            org.slf4j.LoggerFactory.getLogger("ctdhost").warn(
                "ctdhost: [PSD] station=[{}] platform=[{}] door=[{}] name=[{}] next=[{}] line=[{}]",
                s.station(), s.platform(), s.doorNumber(), s.stationName(), s.nextStation(),
                s.lineName());
        }

        // 底部色带（先画带，再画带上的指示灯，避免被盖）
        rects.add(new Rect(0, contentBottom, 0.01f, w, bottomBar, line));

        // 指示灯：无线路图时才显示；两枚并排居中于色带
        if (!s.showMap()) {
            float sq = 1.6f;
            float cx = w * 0.5f - sq - 0.7f;
            float cy = contentBottom + (bottomBar - sq) / 2f;
            rects.add(new Rect(cx, cy, 0.03f, sq, sq,
                DLColor.fromInt(open ? 0xFF2ECC40 : 0xFF555555)));
            rects.add(new Rect(cx + sq + 1.4f, cy, 0.03f, sq, sq,
                DLColor.fromInt(open ? 0xFF555555 : 0xFFD32F2F)));
        }
    }

    /**
     * 按「该站台的下一趟列车」画它的运行路线：
     * 普通线路画单横线 + 末端方向箭头，环线画闭环 + 两侧环别。
     *
     * @param x0 本段起点（4x1 时左右两半各一段）
     */
    private void drawRoute(Rectangle clip, AdvancedDisplayBlockEntity be, String platform,
                           CtdPsdSettings s, DLColor line, float x0, float segW, float h) {

        top.hibermod.ctdapi.route.RouteStore.Line route = resolveNextRoute(be, platform, s);
        List<String> names = route == null ? List.of() : route.stations();
        boolean loop = route != null && route.loop();

        float top = h * 0.36f;
        float bottom = h * 0.72f;
        float left = x0 + 6f;
        float right = x0 + segW - 6f;
        if (right - left < 6f) return;

        if (names.isEmpty()) {
            rects.add(new Rect(left, top, 0.01f, right - left, 0.9f, line));
            return;
        }

        rects.add(new Rect(left, top, 0.01f, right - left, 0.9f, line));
        if (loop) {
            rects.add(new Rect(left, bottom, 0.01f, right - left, 0.9f, line));
            rects.add(new Rect(left - 0.9f, top, 0.01f, 0.9f, bottom - top, line));
            rects.add(new Rect(right, top, 0.01f, 0.9f, bottom - top, line));
        } else {
            // 非环线：只在末端画方向箭头
            boolean rightward = s.arrowRight();
            float ax = rightward ? right : left;
            rects.add(new Rect(ax - 1.4f, top - 1.2f, 0.02f, 2.8f, 0.6f, line));
            rects.add(new Rect(ax - 0.6f, top - 2.0f, 0.02f, 1.4f, 0.6f, line));
        }

        int n = Math.min(names.size(), mapLabels.length);
        int perRow = loop ? (n + 1) / 2 : n;

        for (int i = 0; i < n; i++) {
            boolean onTop = !loop || i < perRow;
            float t = onTop
                ? (float) i / Math.max(1, perRow - 1)
                : (float) (i - perRow) / Math.max(1, n - perRow - 1);
            float x = left + (right - left) * t;
            float y = onTop ? top : bottom;

            rects.add(new Rect(x - 0.6f, y - 0.6f, 0.02f, 1.8f, 1.8f, line));

            BERLabel l = mapLabels[i];
            if (l == null) {
                l = new BERLabel();
                setup(l, ETextAlignment.CENTER, 0xFF000000);
                mapLabels[i] = l;
            }
            l.horizontalAlign.set(ETextAlignment.CENTER);
            put(l, clip, x - 6f, onTop ? y - 3.8f : y + 1.8f, 12f, 3.2f, 0.11f, names.get(i),
                0xFF000000);
        }
    }

    /** 取该站台下一趟车次 → 它的 RunPlan → 线路站序。 */
    private static top.hibermod.ctdapi.route.RouteStore.Line resolveNextRoute(
            AdvancedDisplayBlockEntity be, String platform, CtdPsdSettings s) {

        var stops = be.getStops();
        if (stops.isEmpty()) return null;

        String train = stops.get(0).displayName(
            de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
        var plan = top.hibermod.ctdapi.route.RouteStore.plan(train);
        if (plan == null) return null;
        return top.hibermod.ctdapi.route.RouteStore.line(plan.line());
    }

    private static List<String> splitStations(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("[,，、|]")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static boolean isDoorOpen(AdvancedDisplayBlockEntity be, CtdPsdSettings s) {
        try {
            return top.hibermod.ctdapi.status.DoorStates.isOpen(s.station(), s.platform());
        } catch (Throwable t) {
            return false;
        }
    }

    // ── 原语 ──────────────────────────────────────────────────────────

    private static void put(BERLabel label, Rectangle clip, float x, float y,
                            float w, float h, float scale, String content, int argb) {
        label.clippingArea.set(clip);
        label.text.set(TextUtils.text(content == null ? "" : content));
        label.preferredWidth.set(Math.max(1f, w));
        label.preferredHeight.set(Math.max(0.2f, h));
        label.position.set(Point.of(x, y));
        label.horizontalMinScale.set(scale);
        label.horizontalMaxScale.set(scale);
        label.verticalMinScale.set(scale);
        label.verticalMaxScale.set(scale);
        label.color.set(DLColor.fromInt(argb));
    }

    private void clear() {
        rects.clear();
        name.text.set(null);
        next.text.set(null);
        doorNo.text.set(null);
        doorUnit.text.set(null);
        doorUnit2.text.set(null);
        stationId.text.set(null);
        for (BERLabel l : mapLabels) if (l != null) l.text.set(null);
    }

    /** 亮色底用黑字、暗色底用白字。 */
    private static int contrast(DLColor background) {
        int argb = background.getAsARGB();
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        return lum > 0.55 ? 0xFF101010 : 0xFFFFFFFF;
    }

    static CtdPsdSettings settingsOf(AdvancedDisplayBlockEntity blockEntity) {
        var s = blockEntity.getSettings();
        return s instanceof CtdPsdSettings psd ? psd : new CtdPsdSettings();
    }
}
