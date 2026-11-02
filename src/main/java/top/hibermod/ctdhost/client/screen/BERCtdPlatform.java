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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 站台 PIDS。深色背景，自上而下四条带，互不重叠：
 *
 * <pre>
 *   带 0  开往 体育西路                        （小字）
 *   带 1  本次       3 分钟                    （数字最大）
 *   带 2  下次      11 分钟
 *   带 3  下一站 …  ·  请站在安全线以内候车     （滚动小字）
 * </pre>
 *
 * 字号按「带高 × 0.62 / 9」计算（MC 字体行高 9px），因此每一行只会占据自己那条带，
 * 且所有文字都裁在面板内。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel header = new BERLabel();
    private final BERLabel caption = new BERLabel();
    private final BERLabel minutes = new BERLabel();
    private final BERLabel unit = new BERLabel();
    private final BERLabel nextRow = new BERLabel();
    private final BERLabel footer = new BERLabel();

    public BERCtdPlatform() {
        header.horizontalAlign.set(ETextAlignment.LEFT);
        header.color.set(DLColor.fromInt(0xFF607D8B));
        caption.horizontalAlign.set(ETextAlignment.LEFT);
        caption.color.set(DLColor.fromInt(0xFF546E7A));
        minutes.horizontalAlign.set(ETextAlignment.LEFT);
        minutes.color.set(DLColor.fromInt(0xFFFF5252));
        unit.horizontalAlign.set(ETextAlignment.LEFT);
        unit.color.set(DLColor.fromInt(0xFF546E7A));
        nextRow.horizontalAlign.set(ETextAlignment.LEFT);
        nextRow.color.set(DLColor.fromInt(0xFF263238));
        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.color.set(DLColor.fromInt(0xFF78909C));
        footer.horizontalScrollMode.set(EScrollMode.ALWAYS);
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        header.render(graphics, light);
        caption.render(graphics, light);
        minutes.render(graphics, light);
        unit.render(graphics, light);
        nextRow.render(graphics, light);
        footer.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        CtdPlatformSettings settings = safeSettings(blockEntity);
        Level lvl = blockEntity.getLevel();
        long now = lvl == null ? 0 : lvl.getGameTime();

        float w = Math.max(1, blockEntity.getXSizeScaled()) * 16f;
        float h = Math.max(1, blockEntity.getYSizeScaled()) * 16f;
        Rectangle area = Rectangle.withSize(0, 0, (int) w, (int) h);

        var stops = blockEntity.getStops();
        var first = stops.isEmpty() ? null : stops.get(0);

        // 1 格高（16px）放不下四条带，走紧凑版：只显示「开往 X」与倒计时。
        boolean compact = h < 24f;
        if (compact) {
            Rectangle full = Rectangle.withSize(0, 0, (int) w, (int) h);
            var firstStop = stops.isEmpty() ? null : stops.get(0);
            String d = firstStop == null ? "—"
                : firstStop.displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
            String m = "--";
            if (firstStop != null) {
                long t = firstStop.realtime().isKnown()
                    ? firstStop.realtime().departureIn(now)
                    : firstStop.scheduled().departureIn(now);
                m = String.valueOf(Math.max(0, t / 20 / 60));
            }
            float smallScale = Math.min(0.45f, w / 60f);
            float bigScale = Math.min(1.0f, Math.min(w / 40f, h / 16f));
            place(header, full, w, 1, smallScale, "开往 " + d);
            minutes.clippingArea.set(full);
            minutes.text.set(TextUtils.text(m));
            minutes.preferredWidth.set((float) Math.max(1, w - 3));
            minutes.position.set(Point.of(2, Math.max(5, h * 0.38f)));
            minutes.horizontalMinScale.set(bigScale);
            minutes.horizontalMaxScale.set(bigScale);
            minutes.verticalMinScale.set(bigScale);
            minutes.verticalMaxScale.set(bigScale);
            unit.clippingArea.set(full);
            unit.text.set(TextUtils.text("分钟"));
            unit.preferredWidth.set((float) Math.max(1, w - 3));
            unit.position.set(Point.of(4 + m.length() * 8f * bigScale, Math.max(7, h * 0.46f)));
            unit.horizontalMinScale.set(smallScale);
            unit.horizontalMaxScale.set(smallScale);
            unit.verticalMinScale.set(smallScale);
            unit.verticalMaxScale.set(smallScale);
            caption.text.set(null);
            nextRow.text.set(null);
            footer.text.set(null);
            return;
        }

        // 四条带的高度（占比）
        float b0 = h * 0.15f;
        float b1 = h * 0.32f;
        float b2 = h * 0.18f;
        float b3 = h * 0.14f;

        float y0 = 1;
        float y1 = y0 + b0;
        float y2 = y1 + b1;
        float y3 = y2 + b2;

        float s0 = CtdLayout.fit(b0, 0.55f);
        float s1 = CtdLayout.fit(b1, 1.30f);
        float s2 = CtdLayout.fit(b2, 0.70f);
        float s3 = CtdLayout.fit(b3, 0.50f);

        String dest = first == null ? "—"
            : first.displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
        place(header, area, w, CtdLayout.baseline(y0, b0, s0), s0, "开往 " + dest
            + (settings.platform().isEmpty() ? "" : "   " + settings.platform() + " 站台"));

        place(caption, area, w, CtdLayout.baseline(y1, b1 * 0.42f, s2), s2, "本次");

        String mins = "--";
        if (first != null) {
            long ticks = first.realtime().isKnown()
                ? first.realtime().departureIn(now)
                : first.scheduled().departureIn(now);
            mins = String.valueOf(Math.max(0, ticks / 20 / 60));
        }
        place(minutes, area, w, CtdLayout.baseline(y1 + b1 * 0.35f, b1 * 0.65f, s1), s1, mins);
        // 「分钟」紧贴在数字右侧，宽度用数字实际宽度估（1 位 ≈ 8*s1，2 位 ≈ 16*s1）
        float numW = CtdLayout.width(mins, s1) + 3;
        place(unit, area, w, CtdLayout.baseline(y1 + b1 * 0.45f, b1 * 0.55f, s2), s2, "分钟");
        unit.position.set(Point.of(4 + numW, unit.y.get()));

        String next = "--";
        if (stops.size() > 1) {
            var second = stops.get(1);
            long t2 = second.realtime().isKnown()
                ? second.realtime().departureIn(now)
                : second.scheduled().departureIn(now);
            next = String.valueOf(Math.max(0, t2 / 20 / 60));
        }
        place(nextRow, area, w, CtdLayout.baseline(y2, b2, s2), s2, "下次   " + next + " 分钟");

        place(footer, area, w, CtdLayout.baseline(y3, b3, s3), s3, "下一站 " + dest + "  ·  " + contentLabel(settings.content()) + "  ·  请站在安全线以内候车");
    }

    /** 带高 → 字号：字体行高按 9px 计，留 38% 余量。 */
    private static float fit(float bandHeight, float max) {
        return Math.max(0.25f, Math.min(max, bandHeight * 0.62f / 9f));
    }

    private static void place(BERLabel label, Rectangle area, float w, float y,
                              float scale, String text) {
        label.clippingArea.set(area);
        label.text.set(TextUtils.text(text));
        label.preferredWidth.set((float) Math.max(1, w - 3));
        label.position.set(Point.of(2, Math.max(0, y)));
        label.horizontalMinScale.set(scale);
        label.horizontalMaxScale.set(scale);
        label.verticalMinScale.set(scale);
        label.verticalMaxScale.set(scale);
    }

    static String contentLabel(CtdPlatformSettings.Content content) {
        return switch (content) {
            case DEPARTURES -> "发车";
            case ARRIVALS -> "到达";
            case BOTH -> "到发";
        };
    }

    static String shorten(String s) {
        return s.length() > 18 ? s.substring(0, 18) : s;
    }

    static CtdPlatformSettings safeSettings(AdvancedDisplayBlockEntity blockEntity) {
        var s = blockEntity.getSettings();
        return s instanceof CtdPlatformSettings ctd ? ctd : new CtdPlatformSettings();
    }
}
