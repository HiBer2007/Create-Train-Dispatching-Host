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
 * 站台 PIDS。背景默认透明（露出方块纹理），文字白色。
 *
 * <pre>
 *   带 0   开往 体育西路                        （灰白小字）
 *   带 1   本次  3 分钟                         （数字最大，红色）
 *   带 2   下次 11 分钟
 *   带 3   下一站 … · 发车 · 请站在安全线以内候车
 * </pre>
 *
 * 带 1 里「本次」「数字」「分钟」共用同一条<b>基线</b>，因此三者的底部对齐，
 * 不会出现「分钟」掉到下一行的情况。
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
        header.color.set(DLColor.fromInt(0xFFB0BEC5));
        caption.horizontalAlign.set(ETextAlignment.LEFT);
        caption.color.set(DLColor.fromInt(0xFFECEFF1));
        minutes.horizontalAlign.set(ETextAlignment.LEFT);
        minutes.color.set(DLColor.fromInt(0xFFFF5252));
        unit.horizontalAlign.set(ETextAlignment.LEFT);
        unit.color.set(DLColor.fromInt(0xFFECEFF1));
        nextRow.horizontalAlign.set(ETextAlignment.LEFT);
        nextRow.color.set(DLColor.fromInt(0xFFFFFFFF));
        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.color.set(DLColor.fromInt(0xFF9E9E9E));
        footer.horizontalScrollMode.set(EScrollMode.FLEX_FIT);
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

        String dest = first == null ? "—"
            : first.displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
        String mins = "--";
        if (first != null) {
            long ticks = first.realtime().isKnown()
                ? first.realtime().departureIn(now)
                : first.scheduled().departureIn(now);
            mins = String.valueOf(Math.max(0, ticks / 20 / 60));
        }
        String nextMins = "--";
        if (stops.size() > 1) {
            var second = stops.get(1);
            long t2 = second.realtime().isKnown()
                ? second.realtime().departureIn(now)
                : second.scheduled().departureIn(now);
            nextMins = String.valueOf(Math.max(0, t2 / 20 / 60));
        }

        // 1 格高：只放「开往 X」与倒计时。
        if (h < 24f) {
            float sHead = Math.min(0.45f, w / 60f);
            float sBig = Math.min(0.95f, Math.min(w / 42f, h / 17f));
            float base = CtdLayout.baseline(1, h - 2, sBig);
            place(header, area, w, 1, h * 0.32f, sHead, "开往 " + dest, false);
            minutes.clippingArea.set(area);
            minutes.text.set(TextUtils.text(mins));
            minutes.preferredWidth.set((float) Math.max(1, w - 2));
            minutes.position.set(Point.of(2, base));
            minutes.horizontalMinScale.set(sBig);
            minutes.horizontalMaxScale.set(sBig);
            minutes.verticalMinScale.set(sBig);
            minutes.verticalMaxScale.set(sBig);
            float mx = 2 + CtdLayout.width(mins, sBig) + 2;
            unit.clippingArea.set(area);
            unit.text.set(TextUtils.text("分钟"));
            unit.preferredWidth.set((float) Math.max(1, w - mx - 1));
            unit.position.set(Point.of(mx, base));
            unit.horizontalMinScale.set(sHead);
            unit.horizontalMaxScale.set(sHead);
            unit.verticalMinScale.set(sHead);
            unit.verticalMaxScale.set(sHead);
            caption.text.set(null);
            nextRow.text.set(null);
            footer.text.set(null);
            return;
        }

        float b0 = h * 0.15f;
        float b1 = h * 0.32f;
        float b2 = h * 0.18f;
        float b3 = h * 0.14f;

        float s0 = CtdLayout.fit(b0, 0.58f);
        float sSmall = CtdLayout.fit(b2, 0.72f);
        float sBig = CtdLayout.fit(b1, 1.25f);

        float base0 = CtdLayout.baseline(1, b0, s0);
        float base1 = CtdLayout.baseline(1 + b0, b1, sBig);
        float base2 = CtdLayout.baseline(1 + b0 + b1, b2, sSmall);
        float base3 = CtdLayout.baseline(1 + b0 + b1 + b2, b3, CtdLayout.fit(b3, 0.52f));

        place(header, area, w, 1, b0, s0, "开往 " + dest
            + (settings.platform().isEmpty() ? "" : "    " + settings.platform() + " 站台"), false);

        // 本次 + 数字 + 分钟，三者同基线
        caption.clippingArea.set(area);
        caption.text.set(TextUtils.text("本次"));
        caption.preferredWidth.set((float) Math.max(1, w - 2));
        caption.position.set(Point.of(2, base1));
        setScale(caption, sSmall);

        float numberX = 2 + CtdLayout.width("本次", sSmall) + 4;
        minutes.clippingArea.set(area);
        minutes.text.set(TextUtils.text(mins));
        minutes.preferredWidth.set((float) Math.max(1, w - numberX - 1));
        minutes.position.set(Point.of(numberX, base1));
        setScale(minutes, sBig);

        float unitX = numberX + CtdLayout.width(mins, sBig) + 3;
        unit.clippingArea.set(area);
        unit.text.set(TextUtils.text("分钟"));
        unit.preferredWidth.set((float) Math.max(1, w - unitX - 1));
        unit.position.set(Point.of(unitX, base1));
        setScale(unit, sSmall);

        place(nextRow, area, w, 1 + b0 + b1, b2, sSmall, "下次   " + nextMins + " 分钟", false);

        place(footer, area, w, 1 + b0 + b1 + b2, b3, CtdLayout.fit(b3, 0.52f),
            "下一站 " + dest + "  ·  " + contentLabel(settings.content()) + "  ·  请站在安全线以内候车", false);
        footer.position.set(Point.of(2, base3));
    }

    private static void setScale(BERLabel label, float s) {
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
    }

    private static void place(BERLabel label, Rectangle area, float w,
                              float bandTop, float bandHeight, float scale, String text, boolean unused) {
        label.clippingArea.set(area);
        label.text.set(TextUtils.text(text));
        label.preferredWidth.set((float) Math.max(1, w - 3));
        label.position.set(Point.of(2, CtdLayout.baseline(bandTop, bandHeight, scale)));
        setScale(label, scale);
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