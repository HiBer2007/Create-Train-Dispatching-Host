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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * CTD 屏蔽门。七种规格各是一个独立方块（固定 3 种 + 滑动 4 种），
 * 尺寸由方块实例自带，不用方块状态表达。
 *
 * <pre>
 *   z: 0 ─ 1/4 ─ 1/2 ─ 3/4 ─ 1
 *       固定          滑动        固定
 * </pre>
 *
 * 碰撞箱按 {@code FACING} 旋转，四个朝向都正确。
 */
public class CtdPsdDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CtdPsdDoorBlock> CODEC = simpleCodec(CtdPsdDoorBlock::new);


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
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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

    /** 局部坐标（朝北）的盒子按朝向旋转成世界坐标。 */
    private static VoxelShape rotate(BlockState state, double x0, double y0, double z0,
                                     double x1, double y1, double z1) {
        Direction facing = state.getValue(FACING);
        double ax0 = x0, ax1 = x1, az0 = z0, az1 = z1;
        switch (facing) {
            case EAST -> {
                double n0 = 1 - z1, n1 = 1 - z0;
                az0 = x0; az1 = x1;
                ax0 = n0; ax1 = n1;
            }
            case SOUTH -> {
                ax0 = 1 - x1; ax1 = 1 - x0;
                az0 = 1 - z1; az1 = 1 - z0;
            }
            case WEST -> {
                double n0 = z0, n1 = z1;
                ax0 = 1 - n1; ax1 = 1 - n0;
                az0 = x0; az1 = x1;
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
            // 固定门：贴方块一边、1/4 厚的板
            return rotate(state, 0, 0, 0, 1, height, 0.25);
        }

        CtdPsdDoorBlockEntity be = level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity d
            ? d : null;
        float progress = be == null ? 0f : be.progress();
        float slide = width * progress;

        // 整樘滑动门就是一整块 1/4 厚的板，开门时整块平移进相邻固定门那格
        return rotate(state, -slide, 0, 0.25, width - slide, height, 0.5);
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
        int cells = sliding ? Math.max(1, (int) Math.ceil(width)) : 1;

        Direction side = facing.getClockWise();
        for (int i = 0; i < cells; i++) {
            BlockPos at = pos.relative(side, i);
            if (!level.getBlockState(at).canBeReplaced(ctx)
                || !level.getBlockState(at.above()).canBeReplaced(ctx)) {
                return null;
            }
        }

        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity be) {
            be.setWidth(width);
            be.setHeight(height);
        }
    }

    /** 扳手右键：打开配置界面（反射调用客户端类，避免专用服务器解析 Screen）。 */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {

        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(stack.getItem()).getPath();
        if (!path.endsWith("wrench")) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide && level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity be) {
            try {
                Class.forName("top.hibermod.ctdhost.client.screen.CtdDoorClientHook")
                    .getMethod("open", BlockPos.class, String.class, String.class,
                        String.class, float.class, float.class)
                    .invoke(null, pos, be.station(), be.platform(), be.doorId(),
                        be.speed(), be.height());
            } catch (Throwable ignored) {
            }
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
}
