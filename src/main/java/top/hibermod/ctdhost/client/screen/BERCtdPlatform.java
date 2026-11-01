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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 站台屏的渲染：把「车站 / 站台 / 显示内容」画到 CRN 的高级显示器上。
 *
 * <h2>这一版画什么</h2>
 * 画的是**设置本身**（车站号、站台号、内容类型），不是实时车次。
 * 实时车次需要把宿主的数据同步到客户端，那是下一步。
 *
 * <p>之所以先画设置：它让「类型注册成功、BER 被调用、颜色可用」这三件事
 * 能被一眼确认。**先把管线打通，再灌数据**——反过来会在渲染没跑起来时
 * 就去调试数据，分不清是哪一层的问题。
 *
 * <h2>颜色</h2>
 * 用 {@link DLColor}，这正是原生辉光管做不到的部分：
 * 辉光管的文字与颜色互斥，而高级显示器两者可以同时有。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel header = new BERLabel();
    private final BERLabel body = new BERLabel();

    public BERCtdPlatform() {
        header.position.set(Point.of(3, 3.2f));
        header.horizontalMinScale.set(0.5f);
        header.horizontalMaxScale.set(0.9f);
        header.verticalMinScale.set(0.75f);
        header.verticalMaxScale.set(0.9f);
        header.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);

        body.position.set(Point.of(3, 12.2f));
        body.horizontalMinScale.set(0.5f);
        body.horizontalMaxScale.set(0.9f);
        body.verticalMinScale.set(0.75f);
        body.verticalMaxScale.set(0.9f);
        body.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state,
                     AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent) {
        CtdPlatformSettings settings = getDisplaySettings(blockEntity);

        String stationText = settings.station().isEmpty() ? "(未设车站)" : shorten(settings.station());
        String platformText = settings.platform().isEmpty() ? "" : " 站台 " + settings.platform();
        header.text.set(TextUtils.text(stationText + platformText));

        // 实时车次：CRN 已经把车次算好放在 getStops() 里，直接用。
        java.util.List<de.mrjulsen.crn.api.core.snapshot.BoardEntry> stops = blockEntity.getStops();
        if (stops.isEmpty()) {
            body.text.set(Component.literal(contentLabel(settings.content()) + " · 暂无车次"));
        } else {
            var entry = stops.get(0);
            long deviation = entry.deviation(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
            int minutes = (int) Math.abs(deviation) / 20 / 60;
            String when = deviation > 0 ? ("晚 " + minutes + " 分")
                : deviation < 0 ? ("早 " + minutes + " 分") : "正点";
            body.text.set(Component.literal(
                entry.displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE) + " · " + when));
        }
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        header.render(graphics, light);
        body.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        CtdPlatformSettings settings = getDisplaySettings(blockEntity);

        Rectangle area = Rectangle.withSize(3, 3,
            blockEntity.getXSizeScaled() * 16 - 6,
            blockEntity.getYSizeScaled() * 16 - 6);

        header.clippingArea.set(area);
        header.preferredWidth.set(blockEntity.getXSizeScaled() * 16 - 3 - header.x.get());
        header.horizontalAlign.set(ETextAlignment.LEFT);
        header.color.set(DLColor.fromInt(0xFFEEEEEE));

        body.clippingArea.set(area);
        body.preferredWidth.set(blockEntity.getXSizeScaled() * 16 - 3 - body.x.get());
        body.horizontalAlign.set(ETextAlignment.LEFT);

        // 颜色编码：发车=绿，到达=蓝，双向=黄。
        // 这不是「好看」，而是让人一眼看出这块屏在看哪个方向。
        body.color.set(switch (settings.content()) {
            case DEPARTURES -> DLColor.fromInt(0xFF44CC44);
            case ARRIVALS -> DLColor.fromInt(0xFF4488FF);
            case BOTH -> DLColor.fromInt(0xFFDDCC33);
        });
    }

    /** 显示内容的文字（中文在这里硬编码：这是内部调试文本，不进 lang 文件）。 */
    static String contentLabel(CtdPlatformSettings.Content content) {
        return switch (content) {
            case DEPARTURES -> "发车";
            case ARRIVALS -> "到达";
            case BOTH -> "发车 / 到达";
        };
    }

    /** UUID 太长，显示器上放不下，取前 8 位。 */
    static String shorten(String value) {
        return value.length() <= 8 ? value : value.substring(0, 8);
    }
}
