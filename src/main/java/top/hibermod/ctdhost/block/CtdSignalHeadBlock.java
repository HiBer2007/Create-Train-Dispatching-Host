package top.hibermod.ctdhost.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * 灯头方块：地面 / 高杆 / 吊顶 / 墙壁四种形态共用这一份逻辑。
 *
 * <p>它必须实现 {@link EntityBlock}——灯头要「每秒去问一次信号机现在什么颜色」，
 * 那是方块实体的活。第一版只继承了 {@code HorizontalDirectionalBlock}，
 * 结果 {@code /data get block} 报「The target block is not a block entity」。
 */
public class CtdSignalHeadBlock extends HorizontalDirectionalBlock implements EntityBlock {

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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CtdSignalHeadBlockEntity.create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
'        if (level.isClientSide) {
            // 客户端：顺手把渲染器注册上（幂等）。不依赖任何事件总线。
            top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();
            return null;
        }'
        return (lvl, pos, st, be) -> {
            if (be instanceof CtdSignalHeadBlockEntity head) {
                head.serverTick(lvl, pos, st);
            }
        };
    }
}