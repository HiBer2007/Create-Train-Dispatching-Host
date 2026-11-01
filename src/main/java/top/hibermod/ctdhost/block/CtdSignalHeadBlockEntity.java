package top.hibermod.ctdhost.block;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 灯头的方块实体。
 *
 * <p>它**只做一件事**：每 20 刻找到自己所属的信号机，把当前的语义 id 记下来。
 * 灯色不在这里算——那是渲染器的事，用 {@code SignalHeadLamps.colorsOf} 现算。
 *
 * <p>为什么还要存一份 `aspect`：渲染线程每帧都会问「现在什么颜色」，
 * 不能每帧都去走一遍方块查找。20 刻刷新一次足够，人眼看不出来。
 */
public class CtdSignalHeadBlockEntity extends BlockEntity {

    private static final int REFRESH_TICKS = 20;

    private String aspect = "";
    private boolean debug;
    private int ticks;

    public CtdSignalHeadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 客户端渲染器注册的挂载点。
     *
     * setLevel 在两端都会被调用，而且**一定早于第一次渲染**——
     * 事件总线、@Mod(dist)、mixin 静态初始化、Block#getTicker 都试过了，全都没触发。
     */
    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level.isClientSide) {
            top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();
        }
    }

    public static CtdSignalHeadBlockEntity create(BlockPos pos, BlockState state) {
        return new CtdSignalHeadBlockEntity(CtdBlocks.SIGNAL_HEAD_BE.get(), pos, state);
    }

    public String aspect() {
        return aspect;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (debug) return;                              // 调试模式：不覆盖手写的 Aspect
        if (++ticks < REFRESH_TICKS) return;
        ticks = 0;

        BlockPos signalPos = top.hibermod.ctdhost.execute.SignalHeadLamps.findSignal(level, pos);
        if (signalPos == null) {
            aspect = "";
            return;
        }

        BlockEntity be = level.getBlockEntity(signalPos);
        if (!(be instanceof SignalBlockEntity signal) || signal.edgePoint == null
                || signal.edgePoint.getEdgePoint() == null) {
            aspect = "";
            return;
        }

        String next = top.hibermod.ctdapi.status.SignalStates.get(signal.edgePoint.getEdgePoint().id);
        if (!next.equals(aspect)) {
            // 关键：只改字段不够，客户端不知道。必须标脏 + 发方块更新包，
            // 否则客户端 BE 里的 aspect 永远是空，渲染器直接 return，屏幕上一个黑盒子。
            aspect = next;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Aspect", aspect);
        tag.putBoolean("Debug", debug);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        aspect = tag.getString("Aspect");
        debug = tag.getBoolean("Debug");
    }
}
