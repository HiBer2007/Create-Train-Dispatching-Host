package top.hibermod.ctdhost.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

/**
 * 给 Create 的滑动门加一个「信号模式」标记。
 *
 * <h2>为什么要这个标记</h2>
 * Create 的门有两条打开路径：**红石**（{@code neighborChanged} 里按
 * {@code isDoorPowered} 直接置 OPEN）和**手动**（{@code useWithoutItem}）。
 * 一旦进了信号模式，这两条都必须失效——否则「只有信号系统能调度它」
 * 只是一句愿望：有人在旁边放个拉杆就能把门开掉。
 *
 * <p>实现上不新增方块：行为挂在门自己的 {@code SmartBlockEntity} 上
 * （{@code SlidingDoorBlockEntity#addBehaviours} 是空的，正好挂）。
 * 这样存档、同步、破坏都跟着门走，不需要额外的登记表。
 *
 * <h2>谁来开关它</h2>
 * 宿主：{@link DoorService} 每若干刻算一次「这个站台现在能不能开门」，
 * 再对范围内的门调用 {@code SlidingDoorBlock#setOpen}。
 */
public class SignalDoorBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<SignalDoorBehaviour> TYPE = new BehaviourType<>();

    private boolean signalControlled;

    public SignalDoorBehaviour(SmartBlockEntity be) {
        super(be);
    }

    public boolean isSignalControlled() {
        return signalControlled;
    }

    /** 切换信号模式；返回切换后的状态。 */
    public boolean toggle() {
        signalControlled = !signalControlled;
        // 不用 notifyUpdate()：它在 SmartBlockEntity 的继承链上解析会撞到
        // Ponder 的 VirtualBlockEntity 接口（编译期报「无法应用」）。
        // 直接做它内部那两件事，语义一样且没有歧义。
        blockEntity.setChanged();
        if (blockEntity.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());
        }
        return signalControlled;
    }

    public void set(boolean value) {
        if (signalControlled == value) return;
        signalControlled = value;
        // 不用 notifyUpdate()：它在 SmartBlockEntity 的继承链上解析会撞到
        // Ponder 的 VirtualBlockEntity 接口（编译期报「无法应用」）。
        // 直接做它内部那两件事，语义一样且没有歧义。
        blockEntity.setChanged();
        if (blockEntity.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());
        }
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        nbt.putBoolean("CtdSignalControlled", signalControlled);
        super.write(nbt, registries, clientPacket);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        signalControlled = nbt.getBoolean("CtdSignalControlled");
        super.read(nbt, registries, clientPacket);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    /** 取某个位置上的标记；没有（不是 Create 门）返回 {@code null}。 */
    public static SignalDoorBehaviour at(Level level, BlockPos pos) {
        if (level == null) return null;
        try {
            return BlockEntityBehaviour.get(level, pos, TYPE);
        } catch (Throwable t) {
            return null;
        }
    }
}
