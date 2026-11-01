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
 * 发车指示器的渲染：**带颜色的**倒计时/正计时。
 *
 * <h2>颜色规则</h2>
 * 早点绿、晚点红、正点白——这正是原生辉光管做不到的：
 * 辉光管的文字与颜色互斥，而这里两者可以同时有。
 *
 * <h2>这一版的数据</h2>
 * 偏差暂时是占位（`--:--`，中性色）。实时偏差要把宿主的数据同步到客户端，
 * 那是下一步；先把「类型能选、BER 会跑、颜色能变」这条管线打通。
 */
public class BERCtdDeparture implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel clock = new BERLabel();
    private final BERLabel caption = new BERLabel();

    public BERCtdDeparture() {
        clock.position.set(Point.of(3, 3.2f));
        clock.horizontalMinScale.set(0.5f);
        clock.horizontalMaxScale.set(1.2f);
        clock.verticalMinScale.set(0.9f);
        clock.verticalMaxScale.set(1.2f);
        clock.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);

        caption.position.set(Point.of(3, 12.2f));
        caption.horizontalMinScale.set(0.4f);
        caption.horizontalMaxScale.set(0.7f);
        caption.verticalMinScale.set(0.6f);
        caption.verticalMaxScale.set(0.7f);
        caption.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state,
                     AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent) {
        CtdPlatformSettings settings = BERCtdPlatform.safeSettings(blockEntity);

        // 实时车次直接取自显示器方块实体：CRN 已经把车次算好放在 getStops() 里，
        // 不需要我们再做一次服务端→客户端同步。
        java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops = blockEntity.getStops();

        if (stops.isEmpty()) {
            clock.text.set(TextUtils.text("--:--"));
        } else {
            var entry = stops.get(0);
            long deviation = entry.deviation(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
            clock.text.set(TextUtils.text(formatDeviation((int) deviation)));
            clock.color.set(colorForDeviation((int) deviation));
        }
        String destination = stops.isEmpty() ? ""
            : stops.get(0).displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
        String prefix = settings.platform().isEmpty() ? "距正点" : "站台 " + settings.platform();
        caption.text.set(TextUtils.text(destination.isEmpty() ? prefix : prefix + " · " + destination));
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        clock.render(graphics, light);
        caption.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        Rectangle area = Rectangle.withSize(3, 3,
            blockEntity.getXSizeScaled() * 16 - 6,
            blockEntity.getYSizeScaled() * 16 - 6);

        clock.clippingArea.set(area);
        clock.preferredWidth.set(blockEntity.getXSizeScaled() * 16 - 3 - clock.x.get());
        clock.horizontalAlign.set(ETextAlignment.CENTER);
        clock.color.set(lampColor());

        caption.clippingArea.set(area);
        caption.preferredWidth.set(blockEntity.getXSizeScaled() * 16 - 3 - caption.x.get());
        caption.horizontalAlign.set(ETextAlignment.CENTER);
        caption.color.set(DLColor.fromInt(0xFFBBBBBB));
    }

    /** 偏差格式化成 {@code +1:23} / {@code -0:45} / {@code 0:00}。 */
    static String formatDeviation(int ticks) {
        int totalSeconds = Math.abs(ticks) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String sign = ticks > 0 ? "+" : ticks < 0 ? "-" : "";
        return sign + minutes + ":" + String.format("%02d", seconds);
    }

    /** 早点绿、晚点红、正点白、未知中性。 */
    static DLColor lampColor() {
        return DLColor.fromInt(0xFFCCCCCC);
    }

    /** 供测试与后续接数据用：偏差（tick）→ 颜色。 */
    public static DLColor colorForDeviation(int deviationTicks) {
        if (deviationTicks < 0) return DLColor.fromInt(0xFF33BB33);
        if (deviationTicks > 0) return DLColor.fromInt(0xFFCC3333);
        return DLColor.fromInt(0xFFEEEEEE);
    }
}