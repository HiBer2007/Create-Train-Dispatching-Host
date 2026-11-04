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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * CTD 屏蔽门。七种规格各是一个方块 ID，但**每一格都是一个真实方块**：
 * 放置时会把整樘门占的格子全部填上同一个方块，主格 {@code MAIN=true}。
 *
 * <p>这样每一格都有自己的碰撞与选中框，不会出现「一半点不中、一半能穿进去」。
 *
 * <pre>
 *   z: 0 ─ 1/4 ─ 1/2 ─ 3/4 ─ 1
 *       固定          滑动        固定
 * </pre>
 */
public class CtdPsdDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CtdPsdDoorBlock> CODEC = simpleCodec(CtdPsdDoorBlock::new);

    /** 主格：负责存数据、画门扇、响应扳手。 */
    public static final BooleanProperty MAIN = BooleanProperty.create("main");

    private final boolean sliding;
    private final float width;
    private final float height;

    public CtdPsdDoorBlock(Properties props) {
        this(props, false, 1f, 2f);
    }

    public CtdPsdDoorBlock(Properties props, boolean sliding, float width, float height) {
        super(props);
        this.sliding = sliding;
        this.width = width;
        this.height = height;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(MAIN, true));
    }

    public boolean isSliding() {
        return sliding;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    /** 这樘门占几格（宽）。 */
    public int cells() {
        return sliding ? Math.max(1, (int) Math.ceil(width)) : 1;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MAIN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // 每一格都有实体：渲染时每格只画自己那一扇
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

    // ── 形状 ─────────────────────────────────────────────────────────

    private static VoxelShape rotate(BlockState state, double x0, double y0, double z0,
                                     double x1, double y1, double z1) {
        Direction facing = state.getValue(FACING);
        double ax0 = x0, ax1 = x1, az0 = z0, az1 = z1;
        switch (facing) {
            case EAST -> {
                ax0 = 1 - z1; ax1 = 1 - z0;
                az0 = x0; az1 = x1;
            }
            case SOUTH -> {
                ax0 = 1 - x1; ax1 = 1 - x0;
                az0 = 1 - z1; az1 = 1 - z0;
            }
            case WEST -> {
                ax0 = z0; ax1 = z1;
                az0 = 1 - x1; az1 = 1 - x0;
            }
            default -> { }
        }
        return Shapes.box(ax0, y0, az0, ax1, y1, az1);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return getCollisionShape(state, level, pos, ctx);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        if (!sliding) {
            // 固定门：贴方块一边、1/4 厚
            return rotate(state, 0, 0, 0, 1, height, 0.25);
        }

        // 滑动门：每一格只占自己那格的截面；门开了这一格就空出来
        Direction side0 = state.getValue(FACING).getClockWise();
        BlockPos mainPos = state.getValue(MAIN) ? pos : findMain(level, pos, state, side0);
        boolean open = level.getBlockEntity(mainPos) instanceof CtdPsdDoorBlockEntity be
            && be.progress() > 0.001f;
        if (open) {
            return Shapes.empty();
        }
        return rotate(state, 0, 0, 0.25, 1, height, 0.5);    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        Direction side = facing.getClockWise();

        for (int i = 0; i < cells(); i++) {
            BlockPos at = pos.relative(side, i);
            if (!level.getBlockState(at).canBeReplaced(ctx)
                || !level.getBlockState(at.above()).canBeReplaced(ctx)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(MAIN, true);
    }

    /** 这樘门占几格（高）。1.5 格也要占满 2 格，否则最上面一格是空气、没有碰撞。 */
    public int rows() {
        return Math.max(1, (int) Math.ceil(height));
    }

    /** 放好后把整樘门占的其余格子填上同一个方块（MAIN=false）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity be) {
            be.setWidth(width);
            be.setHeight(height);
        }

        Direction side = state.getValue(FACING).getClockWise();
        for (int row = 0; row < rows(); row++) {
            for (int i = 0; i < cells(); i++) {
                if (row == 0 && i == 0) continue;   // 主格
                BlockPos at = pos.offset(0, row, 0).relative(side, i);
                if (!level.getBlockState(at).canBeReplaced()) continue;
                level.setBlock(at, state.setValue(MAIN, false), 3);
            }
        }
    }

    /** 破坏任意一格 → 整樘门一起消失。 */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean moved) {
        if (level.isClientSide || state.is(newState.getBlock())) {
            return;
        }
        Direction side = state.getValue(FACING).getClockWise();
        boolean main = state.getValue(MAIN);
        BlockPos origin = main ? pos : findMain(level, pos, state, side);
        if (origin == null) return;

        BlockState originState = level.getBlockState(origin);
        if (!(originState.getBlock() instanceof CtdPsdDoorBlock block)) return;

        Direction os = originState.getValue(FACING).getClockWise();
        // 整组一起消失：横向 cells 格、纵向 rows 格
        for (int row = 0; row < block.rows(); row++) {
            for (int i = 0; i < block.cells(); i++) {
                BlockPos at = origin.offset(0, row, 0).relative(os, i);
                if (at.equals(pos)) continue;
                if (level.getBlockState(at).is(state.getBlock())) {
                    level.setBlock(at,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private static BlockPos findMain(BlockGetter level, BlockPos pos, BlockState state, Direction side) {
        if (state.hasProperty(MAIN) && state.getValue(MAIN)) return pos;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(at);
                    if (s.getBlock() == state.getBlock()
                        && s.hasProperty(MAIN) && s.getValue(MAIN)) {
                        return at;
                    }
                }
            }
        }
        return pos;
    }

    /** 扳手右键：打开配置界面（反射调用客户端类）。 */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {

        // 固定门不受调度控制，也不需要配置界面
        if (!sliding) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(stack.getItem()).getPath();
        if (!path.endsWith("wrench")) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        Direction side = state.getValue(FACING).getClockWise();
        BlockPos target = state.getValue(MAIN) ? pos : findMain(level, pos, state, side);

        if (level.isClientSide
                && level.getBlockEntity(target) instanceof CtdPsdDoorBlockEntity be) {
            try {
                Class.forName("top.hibermod.ctdhost.client.screen.CtdDoorClientHook")
                    .getMethod("open", BlockPos.class, String.class, String.class,
                        String.class, float.class, float.class, boolean.class)
                    .invoke(null, target, be.station(), be.platform(), be.doorId(),
                        be.speed(), be.height(), be.testMode());
            } catch (Throwable ignored) {
            }
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
}
