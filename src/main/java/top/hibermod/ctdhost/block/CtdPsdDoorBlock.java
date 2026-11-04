package top.hibermod.ctdhost.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * CTD 屏蔽门。整樘门由一个方块承载（`dynamicShape`），因此不需要多方块结构。
 *
 * <h2>厚度：沿法线把一格均分四份</h2>
 * <pre>
 *   z: 0 ─ 1/4 ─ 1/2 ─ 3/4 ─ 1
 *       固定          滑动        固定
 * </pre>
 *
 * <h2>动画</h2>
 * 开门时两扇滑动门各**横向平移一整格**，滑进相邻固定门那格；
 * `progress` 0→1 由方块实体按速度推进，渲染与碰撞箱都读它。
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

    public CtdPsdDoorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(TYPE, Type.SLIDING));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
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



    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return getCollisionShape(state, level, pos, ctx);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        float h = 2f;

        if (state.getValue(TYPE) == Type.FIXED) {
            // 固定门贴边：z 0–0.25
            return Shapes.box(0f, 0f, 0f, 1f, h, 0.25f);
        }

        float progress = 0f;
        if (level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity be) {
            progress = be.progress();
        }
        float half = 0.5f;
        float left = 0.5f - half - progress;          // 左扇左移一格
        float right = 0.5f + half + progress;         // 右扇右移一格
        VoxelShape a = Shapes.box(left, 0f, 0.25f, left + half, h, 0.5f);
        VoxelShape b = Shapes.box(right - half, 0f, 0.5f, right, h, 0.75f);
        return Shapes.or(a, b);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection().getOpposite();

        // 体积检查：门高 2 格，滑动门还占左右各一格（沿门面方向）
        if (!level.getBlockState(pos).canBeReplaced(ctx) || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        Direction side = facing.getClockWise();
        boolean sliding = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        if (sliding) {
            BlockPos right = pos.relative(side);
            if (!level.getBlockState(right).canBeReplaced(ctx)
                || !level.getBlockState(right.above()).canBeReplaced(ctx)) {
                return null;
            }
        }
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(TYPE, sliding ? Type.SLIDING : Type.FIXED);
    }
}
