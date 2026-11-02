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
 * 车厢内 LCD 动态地图（照实拍排版）。
 *
 * <pre>
 *   ┌──────┬──────────────────────────────┬────────┐
 *   │ 2号线 │  下一站  Next Station         │        │
 *   │  ▉   │  珠江新城  ▶                  │ 终点   │
 *   └──────┴──────────────────────────────┴────────┘
 * </pre>
 *
 * 左边是线别色块，中间「下一站」小字 + 站名大字 + 箭头，右下角是终点站。
 */
public class BERCtdCarLcd implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel badge = new BERLabel();
    private final BERLabel nextSmall = new BERLabel();
    private final BERLabel stationName = new BERLabel();
    private final BERLabel arrow = new BERLabel();
    private final BERLabel dest = new BERLabel();

    public BERCtdCarLcd() {
        badge.horizontalAlign.set(ETextAlignment.CENTER);
        badge.color.set(DLColor.fromInt(0xFFFFFFFF));

        nextSmall.horizontalAlign.set(ETextAlignment.LEFT);
        nextSmall.color.set(DLColor.fromInt(0xFF78909C));

        stationName.horizontalAlign.set(ETextAlignment.LEFT);
        stationName.color.set(DLColor.fromInt(0xFF1A1A1A));

        arrow.horizontalAlign.set(ETextAlignment.LEFT);
        arrow.color.set(DLColor.fromInt(0xFFD32F2F));

        dest.horizontalAlign.set(ETextAlignment.RIGHT);
        dest.color.set(DLColor.fromInt(0xFF546E7A));
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
        Rectangle area = Rectangle.withSize(1, 1, w - 2, h - 2);

        float small = Math.min(0.55f, Math.min(w / 170f, h / 40f));
        float big = Math.min(1.1f, Math.min(w / 80f, h / 18f));

        // 左侧线别色块
        String line = blockEntity.getTrainDisplayName();
        badge.text.set(TextUtils.text(line == null || line.isBlank() ? "—" : line));
        badge.clippingArea.set(Rectangle.withSize(1, 1, (int) Math.max(6, w * 0.16f), (int) (h - 2)));
        badge.preferredWidth.set((float) Math.max(4, w * 0.16f));
        badge.position.set(Point.of(1, Math.max(1, h * 0.35f)));
        setScale(badge, small);

        String next = blockEntity.getNextStop().map(x -> x.title()).orElse("—");

        nextSmall.text.set(TextUtils.text("下一站  Next Station"));
        nextSmall.clippingArea.set(area);
        nextSmall.preferredWidth.set((float) Math.max(4, w - Math.max(6, w * 0.16f) - 4));
        nextSmall.position.set(Point.of(Math.max(6, w * 0.16f) + 2, Math.max(2, h * 0.16f)));
        setScale(nextSmall, small);

        stationName.text.set(TextUtils.text(next));
        stationName.clippingArea.set(area);
        stationName.preferredWidth.set((float) Math.max(4, w - Math.max(6, w * 0.16f) - 12));
        stationName.position.set(Point.of(Math.max(6, w * 0.16f) + 2, Math.max(6, h * 0.38f)));
        setScale(stationName, big);

        arrow.text.set(TextUtils.text("▶"));
        arrow.clippingArea.set(area);
        arrow.preferredWidth.set(4.0f);
        arrow.position.set(Point.of(Math.max(8, w - 10), Math.max(6, h * 0.38f)));
        setScale(arrow, big);

        String finalStop = blockEntity.getFinalStop().map(x -> x.title()).orElse("");
        dest.text.set(TextUtils.text("开往 " + finalStop));
        dest.clippingArea.set(area);
        dest.preferredWidth.set((float) Math.max(4, w * 0.5f));
        dest.position.set(Point.of(Math.max(w * 0.45f, 4), Math.max(10, h - 6 * small - 1)));
        setScale(dest, small);
    }

    private static void setScale(BERLabel label, float s) {
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
    }
}
