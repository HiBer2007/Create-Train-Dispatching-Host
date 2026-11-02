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

    /**
     * 灯色编码，**存在方块状态里**。
     *
     * 为什么不用方块实体字段：BE 的字段要自己写 getUpdateTag/发包才会同步，
     * 而这个会话里那条路试了三次都没通（客户端读到的永远是空串）。
     * 方块状态由游戏自己同步，天然可靠。
     * 0=灭 1=绿 2=黄 3=红 4=白 5=蓝 6=红+黄 7=白+绿
     */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty ASPECT =
        net.minecraft.world.level.block.state.properties.IntegerProperty.create("aspect", 0, 7);

    /**
     * 四种安装形态。灯头的几何与朝向由 facing 决定，
     * mount 只记录「它装在什么地方」，供放置校验与后续逻辑使用。
     */
    public enum Mount implements net.minecraft.util.StringRepresentable {
        GROUND("ground"),
        MAST("mast"),
        CEILING("ceiling"),
        WALL("wall");

        private final String name;
        Mount(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Mount> MOUNT =
        net.minecraft.world.level.block.state.properties.EnumProperty.create("mount", Mount.class);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final MapCodec<CtdSignalHeadBlock> CODEC = simpleCodec(CtdSignalHeadBlock::new);

    public CtdSignalHeadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(ASPECT, 0)
            .setValue(MOUNT, Mount.GROUND));
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
        builder.add(FACING, ASPECT, MOUNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 按周围方块判定安装形态：
        //   下方是信号机      → GROUND（地面）
        //   下方是金属梁      → MAST  （高杆，梁可叠多节）
        //   上方是信号机      → CEILING（吊顶，隧道）
        //   侧面是信号机      → WALL  （挂壁，隧道）
        var level = context.getLevel();
        var pos = context.getClickedPos();
        Mount mount = Mount.GROUND;

        if (isSignal(level, pos.below())) {
            mount = Mount.GROUND;
        } else if (isGirder(level, pos.below())) {
            mount = Mount.MAST;
        } else if (isSignal(level, pos.above())) {
            mount = Mount.CEILING;
        } else {
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (isSignal(level, pos.relative(d))) { mount = Mount.WALL; break; }
            }
        }

        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(MOUNT, mount);
    }

    /**
     * 灯头不能悬空：必须依附在信号机方块或金属梁上。
     *
     * <p>四种形态的依附关系不同：地面/高杆往**下**依附，吊顶往**上**，挂壁往**侧面**。
     * 这里不强制「必须是哪一种」，只要求「至少有个能挂的东西」，
     * 免得把本来合理的摆法（比如先放灯头再放梁）挡掉。
     */
    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        if (isSignal(level, pos.below())) return true;          // 地面
        if (isGirder(level, pos.below())) return true;          // 高杆
        if (isSignal(level, pos.above())) return true;          // 吊顶
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (isSignal(level, pos.relative(d))) return true;  // 挂壁
        }
        return false;
    }

    private static boolean isSignal(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CtdSignalBlock;
    }

    /** 金属梁用注册名判断，绕开 Registrate 的泛型。 */
    private static boolean isGirder(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return id.getPath().equals("metal_girder");
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
        if (level.isClientSide) {
            // 客户端：顺手把渲染器注册上（幂等）。不依赖任何事件总线。
            top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof CtdSignalHeadBlockEntity head) {
                head.serverTick(lvl, pos, st);
            }
        };
    }
}
