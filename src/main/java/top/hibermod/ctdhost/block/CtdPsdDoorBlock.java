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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * CTD 屏蔽门。固定门与滑动门是**两个独立的方块**，共用这一套逻辑：
 *
 * <h2>厚度（沿法线把一格均分四份）</h2>
 * <pre>
 *   z: 0 ─ 1/4 ─ 1/2 ─ 3/4 ─ 1
 *       固定          滑动        固定
 * </pre>
 *
 * <h2>体积</h2>
 * 固定门占 1 格宽 × 1/4 厚；滑动门占 N 格宽（2 或 3）× 中间两格厚，
 * **不包含两侧固定门所在的方块**。
 */
public class CtdPsdDoorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<CtdPsdDoorBlock> CODEC =
        simpleCodec(CtdPsdDoorBlock::new);

    public enum Kind implements net.minecraft.util.StringRepresentable {
        FIXED("fixed"), SLIDING("sliding");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    /** 门高（格 × 2）：3 = 1.5 格，4 = 2 格，6 = 3 格。 */
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 3, 6);

    private final boolean sliding;

    public CtdPsdDoorBlock(Properties props) {
        this(props, false);
    }

    public CtdPsdDoorBlock(Properties props, boolean sliding) {
        super(props);
        this.sliding = sliding;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HEIGHT, 4));
    }

    public boolean isSliding() {
        return sliding;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEIGHT);
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

    public static float heightBlocks(BlockState state) {
        return state.getValue(HEIGHT) / 2f;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext ctx) {
        return getCollisionShape(state, level, pos, ctx);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext ctx) {
        float h = heightBlocks(state);

        if (!sliding) {
            // 固定门：贴方块外侧边缘那一层（z 0–0.25）
            return Shapes.box(0f, 0f, 0f, 1f, h, 0.25f);
        }

        CtdPsdDoorBlockEntity be = level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity d
            ? d : null;
        float progress = be == null ? 0f : be.progress();
        float leaf = (be == null ? 2f : be.width()) / 2f;   // 单扇宽（格）
        float slide = leaf * progress;

        float left = -slide;
        float right = leaf + slide;
        VoxelShape a = Shapes.box(left, 0f, 0.25f, left + leaf, h, 0.5f);
        VoxelShape b = Shapes.box(right, 0f, 0.5f, right + leaf, h, 0.75f);
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
        int cells = sliding ? 2 : 1;

        Direction side = facing.getClockWise();
        for (int i = 0; i < cells; i++) {
            BlockPos at = pos.relative(side, i);
            if (!level.getBlockState(at).canBeReplaced(ctx)
                || !level.getBlockState(at.above()).canBeReplaced(ctx)) {
                return null;
            }
        }

        return defaultBlockState().setValue(FACING, facing).setValue(HEIGHT, 4);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity be) {
            be.setWidth(sliding ? 2f : 1f);
            be.setHeight(heightBlocks(state));
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
