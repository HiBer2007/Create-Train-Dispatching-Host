package top.hibermod.ctdhost.block;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;

/**
 * CTD 信号机的物品。
 *
 * <h2>为什么必须是 {@link TrackTargetingBlockItem}</h2>
 * 机械动力的**所有轨道设备**（列车站点、信号机、汽鸣铁道的道岔扳道机……）
 * 都用同一套逻辑：**拿着物品右键轨道**，轨道上会显示一块「板子」标出将要绑定的位置，
 * 放下去之后方块才和那条轨建立 {@code TrackTargetingBehaviour} 关系。
 *
 * <p>用普通的 {@code BlockItem} 只会把方块随便放在点击位置，
 * 不会产生轨道绑定——这就是先前「右键没反应、也绑不上轨道」的原因。
 *
 * <p>第三个参数是边点类型，信号机用 {@link EdgePointType#SIGNAL}。
 */
public class CtdSignalItem extends TrackTargetingBlockItem {

    public CtdSignalItem(net.minecraft.world.level.block.Block block,
                         net.minecraft.world.item.Item.Properties properties,
                         EdgePointType<?> type) {
        super(block, properties, type);
    }
}