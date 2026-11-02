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
 * 站台 PIDS（参照实拍排版）。
 *
 * <pre>
 *   开往 体育西路                       ← 顶栏，小字
 *   本次    3 分钟                      ← 大号数字
 *   下次   11 分钟
 *   下一站 珠江新城                     ← 底栏，小字
 * </pre>
 *
 * 视觉要点：顶栏与底栏是小字，中间那行的「分钟数」最大；数字用红色强调，
 * 所有文字都裁在面板内，不会溢出到方块外面。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel header = new BERLabel();
    private final BERLabel caption = new BERLabel();
    private final BERLabel minutes = new BERLabel();
    private final BERLabel unit = new BERLabel();
    private final BERLabel nextRow = new BERLabel();
    private final BERLabel footer = new BERLabel();

    public BERCtdPlatform() {
        header.position.set(Point.of(2, 1));
        header.horizontalAlign.set(ETextAlignment.LEFT);
        header.color.set(DLColor.fromInt(0xFF546E7A));

        caption.position.set(Point.of(2, 9));
        caption.horizontalAlign.set(ETextAlignment.LEFT);
        caption.color.set(DLColor.fromInt(0xFF37474F));

        minutes.position.set(Point.of(2, 8));
        minutes.horizontalAlign.set(ETextAlignment.LEFT);
        minutes.color.set(DLColor.fromInt(0xFFD32F2F));

        unit.position.set(Point.of(18, 10));
        unit.horizontalAlign.set(ETextAlignment.LEFT);
        unit.color.set(DLColor.fromInt(0xFF37474F));

        nextRow.position.set(Point.of(2, 18));
        nextRow.horizontalAlign.set(ETextAlignment.LEFT);
        nextRow.color.set(DLColor.fromInt(0xFF37474F));

        footer.position.set(Point.of(2, 22));
        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.color.set(DLColor.fromInt(0xFF90A4AE));
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
    public void tick(Level level, BlockPos pos, BlockState state,
                     AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent) {
        // 文字内容全在 update 里算（那里能拿到尺寸），这里只兜底。
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
        Rectangle area = Rectangle.withSize(1, 1, w - 2, h - 2);

        float small = Math.min(0.6f, Math.min(w / 160f, h / 60f));
        float big = Math.min(1.6f, Math.min(w / 55f, h / 16f));

        java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops = blockEntity.getStops();
        var entry = stops.isEmpty() ? null : stops.get(0);

        String dest = entry == null ? "—"
            : entry.displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
        header.text.set(TextUtils.text("开往 " + dest
            + (settings.platform().isEmpty() ? "" : "    " + settings.platform() + " 站台")));
        place(header, area, w, h, 2, Math.max(1, h * 0.06f), small);

        String mins = "--";
        if (entry != null) {
            long ticks = entry.realtime().isKnown()
                ? entry.realtime().departureIn(now)
                : entry.scheduled().departureIn(now);
            if (ticks <= 0) {
                mins = "0";
            } else {
                mins = String.valueOf(Math.max(0, ticks / 20 / 60));
            }
        }
        minutes.text.set(TextUtils.text(mins));
        place(minutes, area, w, h, 2, Math.max(6, h * 0.28f), big);
        minutes.color.set(DLColor.fromInt(0xFFD32F2F));

        unit.text.set(TextUtils.text("分钟"));
        place(unit, area, w, h, Math.max(20, w * 0.42f), Math.max(10, h * 0.38f), small);

        caption.text.set(TextUtils.text("本次"));
        place(caption, area, w, h, 2, Math.max(6, h * 0.28f), small);

        String next = "--";
        if (stops.size() > 1) {
            var second = stops.get(1);
            long t2 = second.realtime().isKnown()
                ? second.realtime().departureIn(now)
                : second.scheduled().departureIn(now);
            next = String.valueOf(Math.max(0, t2 / 20 / 60));
        }
        nextRow.text.set(TextUtils.text("下次   " + next + " 分钟"));
        place(nextRow, area, w, h, 2, Math.max(14, h * 0.62f), small);

        footer.text.set(TextUtils.text("下一站 " + dest + "    ·    "
            + contentLabel(settings.content()) + "    ·    请站在安全线以内候车"));
        place(footer, area, w, h, 2, Math.max(18, h * 0.82f), small);
    }

    private static void place(BERLabel label, Rectangle area, float w, float h,
                              float x, float y, float scale) {
        label.clippingArea.set(area);
        label.preferredWidth.set(Math.max(1, w - x - 1));
        label.position.set(Point.of(x, Math.min(y, Math.max(0, h - 4))));
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