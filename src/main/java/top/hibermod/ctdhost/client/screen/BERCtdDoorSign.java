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
 * 车门外侧的终到牌。三条带，自上而下：车次 / 终到站（最大）/ 开往。
 * 基线由 {@link CtdLayout#baseline} 计算，不会顶出面板。
 */
public class BERCtdDoorSign implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel trainNo = new BERLabel();
    private final BERLabel dest = new BERLabel();
    private final BERLabel towards = new BERLabel();

    public BERCtdDoorSign() {
        trainNo.horizontalAlign.set(ETextAlignment.LEFT);
        trainNo.color.set(DLColor.fromInt(0xFFFFD54F));
        dest.horizontalAlign.set(ETextAlignment.LEFT);
        dest.color.set(DLColor.fromInt(0xFFFFFFFF));
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
        Rectangle area = Rectangle.withSize(0, 0, (int) w, (int) h);

        float b0 = h * 0.22f;
        float b1 = h * 0.42f;
        float b2 = h * 0.26f;

        float s0 = CtdLayout.fit(b0, 0.60f);
        float s1 = CtdLayout.fit(b1, 1.10f);
        float s2 = CtdLayout.fit(b2, 0.55f);

        String finalStop = blockEntity.getFinalStop().map(x -> x.title()).orElse("—");

        place(trainNo, area, w, 1, b0, s0, safe(blockEntity.getTrainDisplayName(), "—"));
        place(dest, area, w, 1 + b0, b1, s1, finalStop);
        place(towards, area, w, 1 + b0 + b1, b2, s2, "开往 " + finalStop);
    }

    private static String safe(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private static void place(BERLabel label, Rectangle area, float w,
                              float bandTop, float bandHeight, float scale, String text) {
        label.clippingArea.set(area);
        label.text.set(TextUtils.text(text));
        label.preferredWidth.set((float) Math.max(1, w - 2));
        label.position.set(Point.of(2, CtdLayout.baseline(bandTop, bandHeight, scale)));
        label.horizontalMinScale.set(scale);
        label.horizontalMaxScale.set(scale);
        label.verticalMinScale.set(scale);
        label.verticalMaxScale.set(scale);
    }
}