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
 * 车厢内 LCD 动态地图。三条带，互不重叠：
 *
 * <pre>
 *   带 A（0 – 30%）  下一站  Next Station        （小字）
 *   带 B（30 – 75%） 珠江新城  ▶                （大字）
 *   带 C（75 – 100%）开往 终点站                 （小字）
 * </pre>
 *
 * 左侧另有一条线别色块，纵向居中。
 */
public class BERCtdCarLcd implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel badge = new BERLabel();
    private final BERLabel nextSmall = new BERLabel();
    private final BERLabel stationName = new BERLabel();
    private final BERLabel arrow = new BERLabel();
    private final BERLabel dest = new BERLabel();

    public BERCtdCarLcd() {
        badge.horizontalAlign.set(ETextAlignment.CENTER);
        badge.color.set(DLColor.fromInt(0xFF80DEEA));
        nextSmall.horizontalAlign.set(ETextAlignment.LEFT);
        nextSmall.color.set(DLColor.fromInt(0xFFB0BEC5));
        stationName.horizontalAlign.set(ETextAlignment.LEFT);
        stationName.color.set(DLColor.fromInt(0xFFFFFFFF));
        arrow.horizontalAlign.set(ETextAlignment.LEFT);
        arrow.color.set(DLColor.fromInt(0xFFD32F2F));
        dest.horizontalAlign.set(ETextAlignment.LEFT);
        dest.color.set(DLColor.fromInt(0xFF9E9E9E));
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        badge.render(graphics, light);
        nextSmall.render(graphics, light);
        stationName.render(graphics, light);
        arrow.render(graphics, light);
        dest.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        float w = Math.max(1, blockEntity.getXSizeScaled()) * 16f;
        float h = Math.max(1, blockEntity.getYSizeScaled()) * 16f;
        Rectangle area = Rectangle.withSize(0, 0, (int) w, (int) h);

        float leftW = Math.max(7f, w * 0.17f);

        float bA = h * 0.30f;
        float bB = h * 0.45f;
        float bC = h * 0.25f;

        float sSmall = CtdLayout.fit(bA, 0.55f);
        float sBig = CtdLayout.fit(bB, 1.10f);

        String line = blockEntity.getTrainDisplayName();
        place(badge, area, leftW, 1, h - 2, sSmall, safe(line, "—"));
        badge.horizontalAlign.set(ETextAlignment.CENTER);

        String next = blockEntity.getNextStop().map(x -> x.title()).orElse("—");
        String finalStop = blockEntity.getFinalStop().map(x -> x.title()).orElse("—");

        place(nextSmall, area, w - leftW - 3, 1, bA, sSmall, "下一站  Next Station");
        nextSmall.position.set(Point.of(leftW + 2, CtdLayout.baseline(1, bA, sSmall)));

        place(stationName, area, w - leftW - 12, 1 + bA, bB, sBig, next);
        stationName.position.set(Point.of(leftW + 2, CtdLayout.baseline(1 + bA, bB, sBig)));

        arrow.clippingArea.set(area);
        arrow.text.set(TextUtils.text("▶"));
        arrow.preferredWidth.set(6f);
        arrow.position.set(Point.of(Math.max(leftW + 4, w - 8), CtdLayout.baseline(1 + bA, bB, sBig)));
        arrow.horizontalMaxScale.set(sBig);
        arrow.verticalMaxScale.set(sBig);
        arrow.horizontalMinScale.set(sBig);
        arrow.verticalMinScale.set(sBig);

        if (h >= 24f) {
            place(dest, area, w - leftW - 3, 1 + bA + bB, bC, sSmall, "开往 " + finalStop);
            dest.position.set(Point.of(leftW + 2, CtdLayout.baseline(1 + bA + bB, bC, sSmall)));
        } else {
            dest.text.set(null);
        }
    }

    private static String safe(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static void place(BERLabel label, Rectangle area, float w,
                              float bandTop, float bandHeight, float scale, String text) {
        label.clippingArea.set(area);
        label.text.set(TextUtils.text(text));
        label.preferredWidth.set((float) Math.max(1, w));
        label.position.set(Point.of(2, CtdLayout.baseline(bandTop, bandHeight, scale)));
        label.horizontalScale.set(de.mrjulsen.mcdragonlib.util.Pair.of(scale, scale));
        label.verticalScale.set(de.mrjulsen.mcdragonlib.util.Pair.of(scale, scale));
        label.horizontalMinScale.set(scale);
        label.horizontalMaxScale.set(scale);
        label.verticalMinScale.set(scale);
        label.verticalMaxScale.set(scale);
    }
}
