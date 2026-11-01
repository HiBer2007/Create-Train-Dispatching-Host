package top.hibermod.ctdhost.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * 自动道岔机：给一个岔口「装上信号系统的权限」。
 *
 * <h2>为什么需要它</h2>
 * 没有它的时候，岔口只能按固定方向走：**有直行就直行，否则始终左转**。
 * 这是「谁都没管这个岔口」时唯一安全的默认——列车不会因为没人调度而卡死，
 * 也不会因为两条股道都「可用」而随机选一条。
 *
 * <p>装上它之后，这个岔口才允许信号系统自动扳动。
 * 反过来说：**没有机器的岔口，调度下的锁岔指令会被拒绝**，
 * 而不是被静默忽略——静默忽略会让内核以为进路已经排好。
 */
public class SwitchMachineBlock extends BaseEntityBlock {

    /**
     * 方块属性：金属、需要镐子、放进物品栏。
     *
     * <p>名字不能叫 `properties()`——{@code Block} 已经有一个同名实例方法，
     * 静态方法会把它遮住，编译器报「无法从静态上下文引用」。
     */
    public static Properties defaultProperties() {
        return Properties.of()
            .strength(2.0f)
            .requiresCorrectToolForDrops()
            .sound(net.minecraft.world.level.block.SoundType.METAL);
    }

    /** 1.21.1 的方块要一个 codec（序列化用）；这里没有自定义状态，用最简单的。 */
    public static final com.mojang.serialization.MapCodec<SwitchMachineBlock> CODEC =
        simpleCodec(SwitchMachineBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public SwitchMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwitchMachineBlockEntity(pos, state);
    }

    /** 放下去之后自己找最近的岔口；找不到就保持「未绑定」，命令里能看到。 */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide) return;

        if (level.getBlockEntity(pos) instanceof SwitchMachineBlockEntity be) {
            be.bindNearestJunction();
        }
    }
}
