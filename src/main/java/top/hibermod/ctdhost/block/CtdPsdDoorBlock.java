package top.hibermod.ctdhost.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import top.hibermod.ctdhost.block.CtdPsdDoorBlockEntity;

/**
 * CTD 屏蔽门。一个方块承载整樘门的体积，形状可以超出 1 格，
 * 因此不需要多方块结构，放置时检查整块体积是否被占用。
 *
 * <p>固定门与滑动门的区别只在 {@link Type}：
 * 固定门占两侧、厚 1/4；滑动门占中间、在第二平面滑动。
 */
public class CtdPsdDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CtdPsdDoorBlock> CODEC = simpleCodec(CtdPsdDoorBlock::new);

    public enum Type implements net.minecraft.util.StringRepresentable {
        FIXED("fixed"), SLIDING("sliding");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Type> TYPE = EnumProperty.create("type", Type.class);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public CtdPsdDoorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(TYPE, Type.SLIDING)
            .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE, OPEN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CtdPsdDoorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof CtdPsdDoorBlockEntity door) {
                CtdPsdDoorBlockEntity.tick(lvl, pos, st, door);
            }
        };
    }

    // ── 形状：整体体积，宽度按类型与高度自动推断 ─────────────────────

    /** 门宽（沿门面方向）：固定门 1 格，滑动门 2 格；高度由 BE 的高度值决定。 */
    public static int width(BlockState state, BlockEntity be) {
        if (state.getValue(TYPE) == Type.FIXED) return 1;
        if (be instanceof CtdPsdDoorBlockEntity d) {
            return Math.max(2, Math.min(3, d.height()));
        }
        return 2;
    }

    private static VoxelShape shapeOf(BlockState state, BlockEntity be) {
        int span = width(state, be);
        int tall = be instanceof CtdPsdDoorBlockEntity d ? Math.max(2, Math.min(3, d.height())) : 2;
        boolean fixed = state.getValue(TYPE) == Type.FIXED;
        // 厚度 1/4：固定门贴外侧，滑动门贴内侧第二平面
        float z0 = fixed ? 0f : 0.25f;
        float z1 = fixed ? 0.25f : 0.5f;
        float x0 = fixed ? 0f : 0.5f - span * 0.25f;
        float x1 = fixed ? span * 0.25f : 0.5f + span * 0.25f;
        return Shapes.box(x0, 0, z0, Math.min(1f, x1), tall, z1);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return shapeOf(state, level.getBlockEntity(pos));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        return shapeOf(state, level.getBlockEntity(pos));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    /** 放置前检查整块体积是否空着，放不下就不创建。 */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        return false;
    }
}
