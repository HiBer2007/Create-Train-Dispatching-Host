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

    private final List<Rect> rects = new ArrayList<>();

    private final BERLabel name = new BERLabel();
    private final BERLabel next = new BERLabel();
    private final BERLabel doorNo = new BERLabel();
    private final BERLabel doorUnit = new BERLabel();
    private final BERLabel stationId = new BERLabel();
    private final BERLabel[] mapLabels = new BERLabel[24];

    public BERCtdPsd() {
        setup(name, ETextAlignment.CENTER, 0xFF000000);
        setup(next, ETextAlignment.LEFT, 0xFF000000);
        setup(doorNo, ETextAlignment.CENTER, 0xFF000000);
        setup(doorUnit, ETextAlignment.CENTER, 0xFF000000);
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
        for (BERLabel l : labels()) {
            if (l != null && l.text.get() != null) l.render(graphics);
        }
    }

    private BERLabel[] labels() {
        BERLabel[] all = new BERLabel[5 + mapLabels.length];
        all[0] = name;
        all[1] = next;
        all[2] = doorNo;
        all[3] = doorUnit;
        all[4] = stationId;
        System.arraycopy(mapLabels, 0, all, 5, mapLabels.length);
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

        // 4x1 或（3x1 且选了线路图）：画线路图
        if (bw >= 4 || (bw == 3 && s.showMap())) {
            drawMap(clip, s, line, w, h);
            // 底部色带
            rects.add(new Rect(0, h - 3f, 0f, w, 3f, line));
            return;
        }

        // 站名版式（2x1 / 3x1 无图）
        float bottomBar = 3f;
        float contentBottom = h - bottomBar;

        name.horizontalAlign.set(ETextAlignment.CENTER);
        put(name, clip, w * 0.06f, 2.2f, w * 0.68f, 6f, bw >= 3 ? 0.62f : 0.52f,
            s.stationName(), 0xFF000000);

        next.horizontalAlign.set(ETextAlignment.LEFT);
        put(next, clip, 2f, contentBottom - 5.2f, w * 0.62f, 4f, 0.22f,
            "下一站：" + s.nextStation() + "  ←", 0xFF000000);

        // 右侧：线路色圆章 + 门号 + 「站台」 + 车站ID
        float badge = bw >= 3 ? 6f : 5.5f;
        float bx = w - badge - 2.5f;
        rects.add(new Rect(bx, 1.5f, 0.01f, badge, badge * 0.6f, line));
        rects.add(new Rect(bx - 0.6f, 2.3f, 0.01f, badge + 1.2f, badge * 0.35f, line));

        put(doorNo, clip, bx - 0.6f, 1.8f, badge + 1.2f, badge * 0.7f, 0.34f, s.doorNumber(), onLine);

        doorUnit.horizontalAlign.set(ETextAlignment.CENTER);
        put(doorUnit, clip, bx - 1f, 1.5f + badge * 0.6f + 0.3f, badge + 2f, 3f, 0.16f,
            "站台", 0xFF000000);

        stationId.horizontalAlign.set(ETextAlignment.CENTER);
        put(stationId, clip, bx - 1f, contentBottom - 3f, badge + 2f, 3f, 0.14f,
            s.stationId(), 0xFF000000);

        // 指示灯：两枚方块，左「开启」右「关闭」，亮的那枚表示当前状态
        if (s.showIndicator()) {
            float cx = w * 0.5f - 3f;
            float cy = h - 2.6f;
            rects.add(new Rect(cx, cy, 0.02f, 2f, 2f,
                DLColor.fromInt(open ? 0xFF2ECC40 : 0xFF3A3A3A)));
            rects.add(new Rect(cx + 3f, cy, 0.02f, 2f, 2f,
                DLColor.fromInt(open ? 0xFF3A3A3A : 0xFFD32F2F)));
        }

        rects.add(new Rect(0, h - bottomBar, 0f, w, bottomBar, line));
    }

    /** 线路图：一条横向线 + 站点圆点 + 站名（环线上下两排）。 */
    private void drawMap(Rectangle clip, CtdPsdSettings s, DLColor line, float w, float h) {
        List<String> names = splitStations(s.mapStations());
        boolean loop = names.size() >= 2
            && names.get(0).equals(names.get(names.size() - 1));

        float top = 6f;
        float bottom = h - 7f;
        float left = 7f;
        float right = w - 7f;

        // 线条
        rects.add(new Rect(left, top, 0.01f, right - left, 0.9f, line));
        rects.add(new Rect(left, bottom, 0.01f, right - left, 0.9f, line));
        if (loop) {
            rects.add(new Rect(left - 0.9f, top, 0.01f, 0.9f, bottom - top, line));
            rects.add(new Rect(right, top, 0.01f, 0.9f, bottom - top, line));
        }

        int n = Math.max(2, names.size());
        int perRow = loop ? (n + 1) / 2 : n;

        for (int i = 0; i < n && i < mapLabels.length; i++) {
            boolean onTop = !loop || i < perRow;
            float t = onTop ? (float) i / Math.max(1, perRow - 1)
                : (float) (i - perRow) / Math.max(1, n - perRow - 1);
            float x = left + (right - left) * t;
            float y = onTop ? top : bottom;

            rects.add(new Rect(x - 0.5f, y - 0.5f, 0.02f, 1.6f, 1.6f, line));

            BERLabel l = mapLabels[i];
            if (l == null) {
                l = new BERLabel();
                setup(l, ETextAlignment.CENTER, 0xFF000000);
                mapLabels[i] = l;
            }
            l.horizontalAlign.set(ETextAlignment.CENTER);
            put(l, clip, x - 6f, onTop ? y - 3.6f : y + 1.6f, 12f, 3f, 0.10f, names.get(i),
                0xFF000000);
        }
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
