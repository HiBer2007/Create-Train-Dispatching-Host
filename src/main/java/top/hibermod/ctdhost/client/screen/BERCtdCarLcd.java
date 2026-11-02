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
 * 车厢内 LCD 动态地图（广州地铁风格）。
 *
 * <pre>
 *   下一站
 *   体育西路          ← 大字
 *   开往 广州东站 · 左侧开门
 * </pre>
 *
 * 广州地铁车厢 LCD 的要点：站点名最大、下一站最醒目，
 * 终点站与开门方向放在下面一行小字，右上角有个小箭头。
 */
public class BERCtdCarLcd implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel nextLabel = new BERLabel();
    private final BERLabel nextStation = new BERLabel();
    private final BERLabel bottom = new BERLabel();

    public BERCtdCarLcd() {
        nextLabel.position.set(Point.of(3, 2));
        nextLabel.horizontalAlign.set(ETextAlignment.LEFT);
        nextLabel.color.set(DLColor.fromInt(0xFFB3E5FC));

        nextStation.position.set(Point.of(3, 8));
        nextStation.horizontalAlign.set(ETextAlignment.LEFT);
        nextStation.color.set(DLColor.fromInt(0xFFFFFFFF));

        bottom.position.set(Point.of(3, 2));
        bottom.horizontalAlign.set(ETextAlignment.LEFT);
        bottom.color.set(DLColor.fromInt(0xFF90CAF9));
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        nextLabel.render(graphics, light);
        nextStation.render(graphics, light);
        bottom.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        float w = Math.max(1, blockEntity.getXSizeScaled()) * 16f;
        float h = Math.max(1, blockEntity.getYSizeScaled()) * 16f;
        Rectangle area = Rectangle.withSize(1, 1, w - 2, h - 2);

        float big = Math.min(1.0f, Math.min(w / 90f, h / 26f));
        float small = Math.min(0.8f, Math.min(w / 150f, h / 60f));

        nextLabel.clippingArea.set(area);
        nextLabel.preferredWidth.set(w - 4);
        nextLabel.position.set(Point.of(3, 1));
        nextLabel.text.set(TextUtils.text("下一站"));
        scale(nextLabel, small);

        nextStation.clippingArea.set(area);
        nextStation.preferredWidth.set(w - 4);
        nextStation.position.set(Point.of(3, 1 + 9 * small));
        String next = blockEntity.getNextStop().map(s -> s.title()).orElse("—");
        nextStation.text.set(TextUtils.text(next));
        scale(nextStation, big);

        bottom.clippingArea.set(area);
        bottom.preferredWidth.set(w - 4);
        bottom.position.set(Point.of(3, h - 6 * small - 1));
        String finalStop = blockEntity.getFinalStop().map(s -> s.title()).orElse("");
        String side = blockEntity.isWaitingAtStation() ? "开门" : "运行中";
        bottom.text.set(TextUtils.text("开往 " + finalStop + " · " + side));
        scale(bottom, small);
    }

    private static void scale(BERLabel label, float s) {
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
    }
}