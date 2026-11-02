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
 * 车门外侧的终到信息牌（国铁风格）。
 *
 * <p>国铁车门外侧那块小牌就三行：<b>车次 / 终到站 / 开往</b>，
 * 深蓝底白字，站名最大。这里照这个排版。
 */
public class BERCtdDoorSign implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel trainNo = new BERLabel();
    private final BERLabel dest = new BERLabel();
    private final BERLabel towards = new BERLabel();

    public BERCtdDoorSign() {
        trainNo.position.set(Point.of(3, 1));
        trainNo.horizontalAlign.set(ETextAlignment.LEFT);
        trainNo.color.set(DLColor.fromInt(0xFFFFE082));

        dest.position.set(Point.of(3, 6));
        dest.horizontalAlign.set(ETextAlignment.LEFT);
        dest.color.set(DLColor.fromInt(0xFFFFFFFF));

        towards.position.set(Point.of(3, 1));
        towards.horizontalAlign.set(ETextAlignment.LEFT);
        towards.color.set(DLColor.fromInt(0xFFB0BEC5));
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        trainNo.render(graphics, light);
        dest.render(graphics, light);
        towards.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        float w = Math.max(1, blockEntity.getXSizeScaled()) * 16f;
        float h = Math.max(1, blockEntity.getYSizeScaled()) * 16f;
        Rectangle area = Rectangle.withSize(1, 1, w - 2, h - 2);

        float small = Math.min(0.7f, Math.min(w / 110f, h / 46f));
        float big = Math.min(1.0f, Math.min(w / 80f, h / 24f));

        trainNo.clippingArea.set(area);
        trainNo.preferredWidth.set(w - 4);
        trainNo.text.set(TextUtils.text(blockEntity.getTrainDisplayName()));
        scale(trainNo, small, 1);

        dest.clippingArea.set(area);
        dest.preferredWidth.set(w - 4);
        String finalStop = blockEntity.getFinalStop().map(x -> x.title()).orElse("—");
        dest.text.set(TextUtils.text(finalStop));
        dest.position.set(Point.of(3, 1 + 8 * small));
        scale(dest, big, 1 + 8 * small);

        towards.clippingArea.set(area);
        towards.preferredWidth.set(w - 4);
        towards.text.set(TextUtils.text("开往 " + finalStop));
        towards.position.set(Point.of(3, h - 6 * small - 1));
        scale(towards, small, h - 6 * small - 1);
    }

    private static void scale(BERLabel label, float s, float y) {
        label.horizontalMinScale.set(s);
        label.horizontalMaxScale.set(s);
        label.verticalMinScale.set(s);
        label.verticalMaxScale.set(s);
    }
}