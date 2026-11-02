package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扣车指示：绑定「车站 ID + 站台 ID」，显示该站台当前车次是否被扣车。
 *
 * <p>数据自己取（{@code getStops()}），不需要服务端推送——这是 CRN 显示器的原生方式。
 */
public class BERCtdHold implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel label = new BERLabel();

    public BERCtdHold() {
        label.position.set(Point.of(3, 6));
        label.horizontalAlign.set(ETextAlignment.CENTER);
        label.horizontalScale.set(de.mrjulsen.mcdragonlib.util.Pair.of(0.7f, 0.7f));
        label.verticalScale.set(de.mrjulsen.mcdragonlib.util.Pair.of(0.7f, 0.7f));
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        label.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        Rectangle area = Rectangle.withSize(3, 3,
            blockEntity.getXSizeScaled() * 16 - 6,
            blockEntity.getYSizeScaled() * 16 - 6);
        label.clippingArea.set(area);
        label.preferredWidth.set((float) (blockEntity.getXSizeScaled() * 16 - 6));

        // 扣车标记挂在 BE 上（mixin 加的字段），随 BE 一起同步到达客户端。
        String hold = blockEntity instanceof CtdHoldAccess acc ? acc.ctdgetHold() : "";
        if (!hold.isEmpty()) {
            label.text.set(Component.literal("扣车 · " + hold));
            label.color.set(DLColor.fromInt(0xFFFF453A));
            return;
        }

        var stops = blockEntity.getStops();
        if (stops != null && !stops.isEmpty()) {
            var first = stops.get(0);
            label.text.set(Component.literal("扣车 · " + first.displayName(
                de.mrjulsen.crn.api.core.CallDirection.DEPARTURE)));
            label.color.set(DLColor.fromInt(0xFFFF453A));        // 红
        } else {
            label.text.set(Component.literal("扣车 · 无"));
            label.color.set(DLColor.fromInt(0xFF888888));        // 灰
        }
    }
}
