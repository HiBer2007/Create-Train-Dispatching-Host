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
        CtdPsdDoorBlockEntity be = level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity d
            ? d : null;
        float h = be == null ? 2f : be.height();

        if (state.getValue(TYPE) == Type.FIXED) {
            // 固定门：贴方块边缘那一层（z 0–0.25），宽 1 格
            return Shapes.box(0f, 0f, 0f, 1f, h, 0.25f);
        }

        float progress = be == null ? 0f : be.progress();
        float half = (be == null ? 2f : be.width()) / 2f;   // 单扇宽度（格）
        float slide = half * progress;

        float left = -slide;
        float right = half + slide;
        VoxelShape a = Shapes.box(left, 0f, 0.25f, left + half, h, 0.5f);
        VoxelShape b = Shapes.box(right, 0f, 0.5f, right + half, h, 0.75f);
        return Shapes.or(a, b);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** 扳手右键：打开屏蔽门配置界面（只在客户端执行，服务端不加载客户端类）。 */
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
            // 反射调用，避免专用服务器在类加载阶段解析客户端 Screen
            try {
                Class.forName("top.hibermod.ctdhost.client.screen.CtdDoorClientHook")
                    .getMethod("open", BlockPos.class, String.class, String.class,
                        String.class, float.class)
                    .invoke(null, pos, be.station(), be.platform(), be.doorId(), be.speed());
            } catch (Throwable ignored) {
            }
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        boolean sliding = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        int cells = sliding ? 2 : 1;      // 占几格宽

        // 体积检查：高度按 2 格算，宽度占 cells 格（沿门面方向）
        Direction side = facing.getClockWise();
        for (int i = 0; i < cells; i++) {
            BlockPos at = pos.relative(side, i);
            if (!level.getBlockState(at).canBeReplaced(ctx)
                || !level.getBlockState(at.above()).canBeReplaced(ctx)) {
                return null;
            }
        }

        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(TYPE, sliding ? Type.SLIDING : Type.FIXED);
    }

    /** 放好后把宽度/高度写进方块实体（形状与渲染都读它）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof CtdPsdDoorBlockEntity be) {
            be.setWidth(state.getValue(TYPE) == Type.FIXED ? 1f : 2f);
            be.setHeight(2f);
        }
    }
}
