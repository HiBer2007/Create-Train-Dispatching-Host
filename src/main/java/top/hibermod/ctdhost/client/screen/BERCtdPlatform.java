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
 * 站台 PIDS。背景默认透明，文字白色。
 *
 * <pre>
 *   带 0  【1号线】 开往 体育西路              （线路色 + 灰白小字）
 *   带 1  本次  3 分钟                        （数字最大，红色）
 *   带 2  下次 11 分钟
 *   带 3  下一站 … · 发车 · 请站在安全线以内候车
 * </pre>
 *
 * 尺寸约定：3x2（48×32）时四带刚好铺满；2x2 及更矮时底栏改为滚动，
 * 所有文字都在 clippingArea 内，不会溢到方块外。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel lineLabel = new BERLabel();
    private final BERLabel header = new BERLabel();
    private final BERLabel caption = new BERLabel();
    private final BERLabel minutes = new BERLabel();
    private final BERLabel unit = new BERLabel();
    private final BERLabel nextRow = new BERLabel();
    private final BERLabel footer = new BERLabel();

    public BERCtdPlatform() {
        lineLabel.horizontalAlign.set(ETextAlignment.LEFT);
        lineLabel.color.set(DLColor.fromInt(0xFF42A5F5));
        header.horizontalAlign.set(ETextAlignment.LEFT);
        header.color.set(DLColor.fromInt(0xFFCFD8DC));
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
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        // 首次渲染时注册方块实体渲染器：那时 DeferredHolder 已经绑定。
        top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();
        lineLabel.render(graphics, light);
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

        // 1 格高：只放「线路 + 开往」与倒计时。
        if (h < 24f) {
            float sHead = Math.min(0.42f, w / 62f);
            float sBig = Math.min(0.92f, Math.min(w / 44f, h / 18f));
            float base = CtdLayout.baseline(1, h - 2, sBig);

            String line = settings.lineName();
            float x = 2;
            if (!line.isEmpty()) {
                lineLabel.clippingArea.set(area);
                lineLabel.text.set(TextUtils.text("【" + line + "】"));
                lineLabel.preferredWidth.set((float) Math.max(1, w * 0.55f));
                lineLabel.position.set(Point.of(x, base));
                setScale(lineLabel, sHead);
                lineLabel.color.set(lineColor(settings));
                x += CtdLayout.width("【" + line + "】", sHead) + 3;
            } else {
                lineLabel.text.set(null);
            }

            header.clippingArea.set(area);
            header.text.set(TextUtils.text("开往 " + dest));
            header.preferredWidth.set((float) Math.max(1, w - x - 1));
            header.position.set(Point.of(x, base));
            setScale(header, sHead);

            minutes.clippingArea.set(area);
            minutes.text.set(TextUtils.text(mins));
            minutes.preferredWidth.set((float) Math.max(1, w - 2));
            minutes.position.set(Point.of(2, base));
            setScale(minutes, sBig);

            float mx = 2 + CtdLayout.width(mins, sBig) + 2;
            unit.clippingArea.set(area);
            unit.text.set(TextUtils.text("分钟"));
            unit.preferredWidth.set((float) Math.max(1, w - mx - 1));
            unit.position.set(Point.of(mx, base));
            setScale(unit, sHead);

            caption.text.set(null);
            nextRow.text.set(null);
            footer.text.set(null);
            return;
        }

        // 四带：合计 0.78h，其余留白，3x2 刚好。
        float b0 = h * 0.12f;
        float b1 = h * 0.30f;
        float b2 = h * 0.16f;
        float b3 = h * 0.20f;

        float s0 = CtdLayout.fit(b0, 0.55f);
        float sSmall = CtdLayout.fit(b2, 0.70f);
        float sBig = CtdLayout.fit(b1, 1.20f);

        float base0 = CtdLayout.baseline(1, b0, s0);
        float base1 = CtdLayout.baseline(1 + b0, b1, sBig);
        float base2 = CtdLayout.baseline(1 + b0 + b1, b2, sSmall);

        String line = settings.lineName();
        float x0 = 2;
        if (!line.isEmpty()) {
            lineLabel.clippingArea.set(area);
            lineLabel.text.set(TextUtils.text("【" + line + "】"));
            lineLabel.preferredWidth.set((float) Math.max(1, w * 0.55f));
            lineLabel.position.set(Point.of(x0, base0));
            setScale(lineLabel, s0);
            lineLabel.color.set(lineColor(settings));
            x0 += CtdLayout.width("【" + line + "】", s0) + 3;
        } else {
            lineLabel.text.set(null);
        }

        header.clippingArea.set(area);
        header.text.set(TextUtils.text("开往 " + dest
            + (settings.platform().isEmpty() ? "" : "   " + settings.platform() + " 站台")));
        header.preferredWidth.set((float) Math.max(1, w - x0 - 1));
        header.position.set(Point.of(x0, base0));
        setScale(header, s0);

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

        nextRow.clippingArea.set(area);
        nextRow.text.set(TextUtils.text("下次   " + nextMins + " 分钟"));
        nextRow.preferredWidth.set((float) Math.max(1, w - 3));
        nextRow.position.set(Point.of(2, CtdLayout.baseline(1 + b0 + b1, b2, sSmall)));
        setScale(nextRow, sSmall);

        // 2x2（32px 高）及更矮才滚动；3x2 用缩放，保证整句可见。
        boolean marquee = h < 30f;
        footer.horizontalScrollMode.set(marquee ? EScrollMode.ALWAYS : EScrollMode.FLEX_FIT);
        footer.clippingArea.set(area);
        // 底栏只放一句短提示：3x2（48×32）宽度下刚好放得下，不再写「下一站 …」。
        footer.text.set(TextUtils.text("请站在安全线以内候车"));
        footer.preferredWidth.set((float) Math.max(1, w - 4));
        footer.horizontalMinScale.set(0.22f);
        footer.position.set(Point.of(2, CtdLayout.baseline(1 + b0 + b1 + b2, b3, CtdLayout.fit(b3, 0.55f))));
        setScale(footer, CtdLayout.fit(b3, 0.55f));
    }

    /** 线路色：用户设了就用用户的，否则按代号自动挑一个地铁常见的色。 */
    static DLColor lineColor(CtdPlatformSettings settings) {
        // 1) 用户在 GUI 里选的色号优先。
        if (settings.lineColor() != 0) return DLColor.fromInt(settings.lineColor());

        // 2) 否则按线路代号到 CTD 色号注册表里查（「11号线」→ ctd:line11）。
        String key = settings.lineName();
        if (key != null) {
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("([0-9]+)").matcher(key);
            if (m.find()) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(
                    "ctd:line" + m.group(1));
                int c = top.hibermod.ctdapi.display.LineColors.getOrDefault(id, 0);
                if (c != 0) return DLColor.fromInt(c);
            }
        }

        // 3) 兜底：按名字哈希取一个地铁常见的色。
        int hash = key == null ? 0 : key.hashCode();
        int[] palette = {
            0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047,
            0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFF6D4C41
        };
        return DLColor.fromInt(palette[Math.floorMod(hash, palette.length)]);
    }
    private static void setScale(BERLabel label, float s) {
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
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
