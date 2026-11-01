package top.hibermod.ctdhost.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * 灯头方块：地面 / 高杆 / 吊顶 / 墙壁四种形态共用这一份逻辑。
 *
 * 区别只在模型与安装位置（由 blockstate 的 facing 与各自注册的模型决定）。
 * 灯色不在这里存——它每 N 刻去下方（或侧面）的 ctd_signal 读，
 * 保持「状态只有一份」。
 */
public class CtdSignalHeadBlock extends HorizontalDirectionalBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final MapCodec<CtdSignalHeadBlock> CODEC = simpleCodec(CtdSignalHeadBlock::new);

    public CtdSignalHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public static Properties defaultProperties() {
        return Properties.of()
            .mapColor(net.minecraft.world.level.material.MapColor.METAL)
            .strength(1.0f)
            .noOcclusion();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
