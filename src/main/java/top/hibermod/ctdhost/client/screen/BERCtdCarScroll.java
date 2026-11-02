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
 * 车厢内走字屏（广州地铁车门上方的单行 LED）。
 *
 * <p>内容按广州地铁的播报顺序写：<b>下一站 → 开门方向 → 终点站</b>，
 * 一行滚过去。走字屏本来就是滚动的，所以这里用 {@code EScrollMode.ALWAYS}。
 */
public class BERCtdCarScroll implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel line = new BERLabel();

    public BERCtdCarScroll() {
        line.position.set(Point.of(2, 1));
        line.horizontalAlign.set(ETextAlignment.LEFT);
        line.color.set(DLColor.fromInt(0xFFFF7043));   // LED 橙红
        line.horizontalScrollMode.set(EScrollMode.ALWAYS);
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        line.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        float w = Math.max(1, blockEntity.getXSizeScaled()) * 16f;
        float h = Math.max(1, blockEntity.getYSizeScaled()) * 16f;
        Rectangle area = Rectangle.withSize(0, 0, (int) w, (int) h);

        line.clippingArea.set(area);
        line.preferredWidth.set(w - 4);

        float s = Math.min(0.9f, (h - 4f) * 0.62f / 9f);
        line.position.set(Point.of(2, CtdLayout.baseline(1, h - 2, s)));
        line.horizontalMinScale.set(s);
        line.horizontalMaxScale.set(s);
        line.verticalMinScale.set(s);
        line.verticalMaxScale.set(s);

        String next = blockEntity.getNextStop().map(x -> x.title()).orElse("—");
        String finalStop = blockEntity.getFinalStop().map(x -> x.title()).orElse("");
        line.text.set(TextUtils.text(
            "下一站：" + next + "，请从" + (blockEntity.isWaitingAtStation() ? "左侧" : "右侧")
                + "车门下车。本次列车开往" + finalStop + "。"));
    }
}
