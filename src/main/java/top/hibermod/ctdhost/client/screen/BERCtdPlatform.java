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
 * 站台 PIDS，版式参照广铁 11 号线站台屏（去掉广告区）：
 *
 * <pre>
 *   ┌────────────────────────────────────────────┐
 *   │ 【11号线】 内环            开往 员村        │  ← 方向条（线路色）
 *   ├────────────────────────────────────────────┤
 *   │  本趟      2 分钟                          │
 *   │  下一趟   10 分钟                          │
 *   │  第三趟   18 分钟                          │
 *   ├────────────────────────────────────────────┤
 *   │  请注意站台间隙，小心慢行。                │  ← 滚动提示
 *   └────────────────────────────────────────────┘
 * </pre>
 *
 * 尺寸自适应：1 格高只留方向条与「本趟」；2 格高留两趟；3 格高及以上三趟。
 * 所有文字都在 clippingArea 内。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel lineLabel = new BERLabel();
    private final BERLabel direction = new BERLabel();
    private final BERLabel destination = new BERLabel();
    private final BERLabel[] rows = new BERLabel[3];
    private final BERLabel footer = new BERLabel();

    private static final String[] ROW_NAMES = {"本趟", "下一趟", "第三趟"};

    public BERCtdPlatform() {
        lineLabel.horizontalAlign.set(ETextAlignment.LEFT);
        lineLabel.color.set(DLColor.fromInt(0xFF42A5F5));
        direction.horizontalAlign.set(ETextAlignment.LEFT);
        direction.color.set(DLColor.fromInt(0xFFFFFFFF));
        destination.horizontalAlign.set(ETextAlignment.RIGHT);
        destination.color.set(DLColor.fromInt(0xFFECEFF1));
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new BERLabel();
            rows[i].horizontalAlign.set(ETextAlignment.LEFT);
            rows[i].color.set(DLColor.fromInt(i == 0 ? 0xFFFF5252 : 0xFFECEFF1));
        }
        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.color.set(DLColor.fromInt(0xFF9E9E9E));
        footer.horizontalScrollMode.set(EScrollMode.ALWAYS);
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();
        lineLabel.render(graphics, light);
        direction.render(graphics, light);
        destination.render(graphics, light);
        for (BERLabel row : rows) {
            if (row.text.get() != null) row.render(graphics, light);
        }
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
        String line = settings.lineName();
        String dest = stops.isEmpty() ? "—"
            : stops.get(0).displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);

        // ── 方向条：按 3x2（48×32）算，头 8px ──
        float headH = Math.max(7f, h * 0.25f);
        float sHead = Math.min(0.58f, headH * 0.62f / 9f);
        float baseHead = CtdLayout.baseline(0, headH, sHead);

        // 头里只放「【线路】开往 X」：3x2 宽度 48px，多了必然溢出。
        String shortDest = dest.length() > 7 ? dest.substring(0, 7) : dest;
        StringBuilder headText = new StringBuilder();
        if (!line.isEmpty()) headText.append("【").append(line).append("】");
        headText.append("开往 ").append(shortDest);

        direction.clippingArea.set(area);
        direction.text.set(TextUtils.text(headText.toString()));
        direction.preferredWidth.set((float) Math.max(1, w - 4));
        direction.position.set(Point.of(2, baseHead));
        setScale(direction, sHead);
        direction.color.set(DLColor.fromInt(0xFFECEFF1));
        direction.horizontalScrollMode.set(EScrollMode.FLEX_FIT);
        direction.verticalMinScale.set(0.2f);

        lineLabel.text.set(null);
        destination.text.set(null);

        // ── 三趟车 ──
        float footH = Math.max(5f, h * 0.16f);
        float bodyTop = headH;
        float bodyH = Math.max(4f, h - headH - footH);
        int count = h < 24f ? 1 : (h < 32f ? 2 : 3);
        float rowH = bodyH / count;
        float sRow = CtdLayout.fit(rowH, 1.05f);

        for (int i = 0; i < rows.length; i++) {
            BERLabel row = rows[i];
            if (i >= count || i >= stops.size()) {
                row.text.set(null);
                continue;
            }
            var e = stops.get(i);
            long ticks = e.realtime().isKnown()
                ? e.realtime().departureIn(now)
                : e.scheduled().departureIn(now);
            String mins = String.valueOf(Math.max(0, ticks / 20 / 60));
            row.clippingArea.set(area);
            row.text.set(TextUtils.text(ROW_NAMES[i] + "    " + mins + " 分钟"));
            row.preferredWidth.set((float) Math.max(1, w - 4));
            row.position.set(Point.of(2, CtdLayout.baseline(bodyTop + rowH * i, rowH, sRow)));
            setScale(row, sRow);
            row.color.set(DLColor.fromInt(i == 0 ? 0xFFFF5252 : 0xFFECEFF1));
        }

        // ── 底部提示（滚动，短句）──
        float sFoot = CtdLayout.fit(footH, 0.55f);
        footer.clippingArea.set(area);
        footer.text.set(TextUtils.text("请注意站台间隙，小心慢行。"));
        footer.preferredWidth.set((float) Math.max(1, w - 4));
        footer.horizontalMinScale.set(0.22f);
        footer.position.set(Point.of(2, CtdLayout.baseline(h - footH, footH, sFoot)));
        setScale(footer, sFoot);
        footer.horizontalScrollMode.set(EScrollMode.ALWAYS);
    }

    /** 线路色：用户选的优先，其次按代号查 CTD 色号表，最后按名字哈希兜底。 */
    static DLColor lineColor(CtdPlatformSettings settings) {
        if (settings.lineColor() != 0) return DLColor.fromInt(settings.lineColor());

        String key = settings.lineName();
        if (key != null) {
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("([0-9]+)").matcher(key);
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
