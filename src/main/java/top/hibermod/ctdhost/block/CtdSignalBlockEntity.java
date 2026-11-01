package top.hibermod.ctdhost.block;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CTD 信号机的方块实体。
 *
 * 为什么必须有自己的 BE 类型：BlockEntityType 会校验 validBlocks，
 * 复用 Create 的 TRACK_SIGNAL 会在放置时报「Invalid block entity」。
 *
 * 其余全部继承：edgePoint（指向 SignalBoundary）、红石强制红、状态缓存照旧。
 * 状态只有一份，就在这个 BE 上。
 */
public class CtdSignalBlockEntity extends SignalBlockEntity {

    public CtdSignalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 给 BlockEntityType.Builder 用的工厂。
     *
     * 不直接在注册处写 lambda：那里引用 CtdBlocks.CTD_SIGNAL_BE
     * 会让 javac 报「初始化器中有错误」（静态初始化自引用）。
     * 把引用挪到这个静态方法里，注册处就只剩一个方法引用。
     */
    public static CtdSignalBlockEntity create(BlockPos pos, BlockState state) {
        return new CtdSignalBlockEntity(CtdBlocks.CTD_SIGNAL_BE.get(), pos, state);
    }
}