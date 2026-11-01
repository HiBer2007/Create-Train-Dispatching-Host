package top.hibermod.ctdhost.block;

import com.simibubi.create.content.trains.signal.SignalBlock;

import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * CTD 自己的信号机本体。
 *
 * <h2>为什么继承 {@link SignalBlock}</h2>
 * 跨越模组（`refs/KuaYue`）就是这么做的：`SimpleSignalBlockEntity extends SignalBlockEntity`。
 * 继承之后 {@code TrackTargetingBehaviour}、{@code SignalBoundary}、红石强制红、
 * 交叉信号的链式解析全部照旧，我们只换外观与渲染。
 *
 * <p>另起一套信号系统要重写这些东西，而且会和 Create 的行车判定打架——
 * 那正是「魔改辉光管」那条路失败的原因：状态归属有两份。
 *
 * <h2>方块实体</h2>
 * 目前**直接复用 Create 的 `TRACK_SIGNAL`**（`SignalBlock` 已经实现了
 * {@code IBE<SignalBlockEntity>}，这里不再覆盖 `getBlockEntityType()`）。
 * 等灯头需要自己的状态机时再换成自定义 BE——那时才有必要引入新类型。
 *
 * <h2>状态放在哪</h2>
 * **只放在这里**（准确说是它持有的 {@code edgePoint} 上）。
 * 四种灯头与金属梁都不存状态，只是往下来读——这样不会出现两份状态对不上。
 */
public class CtdSignalBlock extends SignalBlock {

    public CtdSignalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** 与 Create 的信号机同样的方块属性（金属、不遮挡）。 */
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.METAL)
            .strength(1.5f)
            .noOcclusion();
    }
}
