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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 国铁风格发车信息板。
 *
 * <p>版式照国铁车站的 LED 发车板：
 * <pre>
 *   车次    开点    终到站    状态
 *   G1234   08:15   北京南    检票中
 * </pre>
 * 深底、琥珀色字，表头更亮；行数由显示器高度决定，超出部分裁掉而不是溢出到屏幕外。
 */
public class BERCtdBoard implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private static final int ROW_H = 9;

    private final BERLabel header = new BERLabel();
    private final BERLabel[] rows = new BERLabel[12];

    public BERCtdBoard() {
        header.position.set(Point.of(2, 1));
        header.horizontalAlign.set(ETextAlignment.LEFT);
        header.color.set(DLColor.fromInt(0xFFFFE082));

        for (int i = 0; i < rows.length; i++) {
            rows[i] = new BERLabel();
            rows[i].position.set(Point.of(2, 1 + ROW_H * (i + 1)));
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
        Rectangle area = Rectangle.withSize(1, 1, w - 2, h - 2);

        float scale = Math.min(1.0f, Math.min(w / 120f, h / 60f));

        header.clippingArea.set(area);
        header.preferredWidth.set(w - 4);
        header.text.set(TextUtils.text("车次      开点      终到站        状态"));
        applyScale(header, scale);

        var stops = blockEntity.getStops();
        int capacity = Math.max(0, (int) ((h - 12) / (ROW_H * scale)));
        capacity = Math.min(capacity, rows.length);

        for (int i = 0; i < rows.length; i++) {
            BERLabel row = rows[i];
            row.clippingArea.set(area);
            row.preferredWidth.set(w - 4);
            applyScale(row, scale);
            if (i < capacity && stops != null && i < stops.size()) {
                var e = stops.get(i);
                String name = e.trainName() == null || e.trainName().isBlank() ? "—" : e.trainName();
                String dest = e.displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
                row.text.set(TextUtils.text(pad(name, 10) + pad("--:--", 10) + pad(dest, 14) + "正点"));
            } else {
                row.text.set(null);
            }
        }
    }

    private static void applyScale(BERLabel label, float scale) {
        label.horizontalMinScale.set(scale);
        label.horizontalMaxScale.set(scale);
        label.verticalMinScale.set(scale);
        label.verticalMaxScale.set(scale);
    }

    private static String pad(String s, int width) {
        String v = s == null ? "" : s;
        if (v.length() > width) v = v.substring(0, width);
        return v + " ".repeat(Math.max(0, width - v.length()));
    }
}