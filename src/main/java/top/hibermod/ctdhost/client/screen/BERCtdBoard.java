package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 发车信息板（铁路车站的 LED 板式）。
 *
 * <pre>
 *   车次     开点    终到           状态
 *   G1234    08:15   北京南         检票中
 * </pre>
 *
 * 行高由可用高度除以行数算出来，每行单独一条带，绝不互相压盖；
 * 文字宽度不足时按列宽裁剪。
 */
public class BERCtdBoard implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private static final String[] HEAD = {"车次", "开点", "终到", "状态"};
    private static final int MAX_ROWS = 8;

    private final BERLabel header = new BERLabel();
    private final BERLabel[] rows = new BERLabel[MAX_ROWS];

    public BERCtdBoard() {
        header.horizontalAlign.set(ETextAlignment.LEFT);
        header.color.set(DLColor.fromInt(0xFFFFD54F));
        for (int i = 0; i < MAX_ROWS; i++) {
            rows[i] = new BERLabel();
            rows[i].horizontalAlign.set(ETextAlignment.LEFT);
            rows[i].color.set(DLColor.fromInt(0xFFFFB300));
        }
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        header.render(graphics, light);
        for (BERLabel row : rows) {
            if (row.text.get() != null) row.render(graphics, light);
        }
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        float w = Math.max(1, blockEntity.getXSizeScaled()) * 16f;
        float h = Math.max(1, blockEntity.getYSizeScaled()) * 16f;
        Rectangle area = Rectangle.withSize(0, 0, (int) w, (int) h);

        var stops = blockEntity.getStops();
        int dataRows = stops == null ? 0 : Math.min(stops.size(), MAX_ROWS);

        // 行数 = 数据行 + 表头；行高均分可用高度（至少 7px 才放得下字）
        int totalRows = Math.max(2, dataRows + 1);
        float rowH = Math.max(7f, (h - 2) / totalRows);
        float scale = Math.min(0.75f, rowH * 0.62f / 9f);

        place(header, area, w, 1, rowH, scale, join(HEAD));
        for (int i = 0; i < MAX_ROWS; i++) {
            BERLabel row = rows[i];
            if (i >= dataRows) { row.text.set(null); continue; }
            var e = stops.get(i);
            String name = safe(e.trainName(), "—");
            String dest = safe(e.displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE), "—");
            String status = e.isCancelled() ? "停运" : "正点";
            place(row, area, w, 1 + rowH * (i + 1), rowH, scale,
                join(new String[]{name, "--:--", dest, status}));
        }
    }

    private static String join(String[] cols) {
        StringBuilder sb = new StringBuilder();
        int[] width = {10, 8, 16, 8};
        for (int i = 0; i < cols.length; i++) {
            String v = safe(cols[i], "");
            if (v.length() > width[i]) v = v.substring(0, width[i]);
            sb.append(v);
            int pad = width[i] - v.length();
            for (int k = 0; k < pad; k++) sb.append(' ');
        }
        return sb.toString();
    }

    private static String safe(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static void place(BERLabel label, Rectangle area, float w,
                              float bandTop, float bandHeight, float scale, String text) {
        label.clippingArea.set(area);
        label.text.set(TextUtils.text(text));
        label.preferredWidth.set((float) Math.max(1, w - 2));
        label.position.set(Point.of(1, Math.max(0, CtdLayout.baseline(bandTop, bandHeight, scale))));
        label.horizontalMinScale.set(scale);
        label.horizontalMaxScale.set(scale);
        label.verticalMinScale.set(scale);
        label.verticalMaxScale.set(scale);
    }
}
