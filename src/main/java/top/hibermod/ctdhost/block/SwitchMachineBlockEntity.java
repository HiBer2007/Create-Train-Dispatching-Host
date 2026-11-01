package top.hibermod.ctdhost.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdhost.execute.SwitchMachines;

/**
 * 自动道岔机的方块实体：记住「我管的是哪个岔口」。
 *
 * <h2>绑定是显式的，不是每次现算</h2>
 * 放置时找一次最近的岔口并写进 NBT。之后即使玩家在旁边又铺了轨道，
 * 这台机器管的还是原来那个岔口——**自动重新绑定会让「我管的是哪个岔口」
 * 随地形变化而漂移**，而调度的进路是按岔口 id 排的。
 *
 * <p>找不到岔口时保持未绑定，命令里显示 `unbound`，不会假装管着什么。
 */
public class SwitchMachineBlockEntity extends BlockEntity {

    /** 绑定半径：机器离岔口多远之内算「这个岔口的」。 */
    public static final double BIND_RADIUS = 8.0;

    private String nodeId = "";

    public SwitchMachineBlockEntity(BlockPos pos, BlockState state) {
        super(CtdBlocks.SWITCH_MACHINE_BE.get(), pos, state);
    }

    public Rail.NodeId node() {
        return nodeId.isEmpty() ? null : Rail.NodeId.tryDecode(nodeId);
    }

    public String nodeId() {
        return nodeId;
    }

    /** 找最近的岔口并绑定；返回是否绑定成功。 */
    public boolean bindNearestJunction() {
        if (level == null || level.isClientSide) return false;

        var topo = top.hibermod.ctdhost.HostRuntime.get().mirror() == null
            ? null : top.hibermod.ctdhost.HostRuntime.get().mirror().current();
        if (topo == null) return false;

        Rail.NodeId best = null;
        double bestDistance = BIND_RADIUS * BIND_RADIUS;

        for (var node : topo.nodes()) {
            if (!node.isSwitch()) continue;

            double dx = node.id().x() - (worldPosition.getX() + 0.5);
            double dy = node.id().y() - (worldPosition.getY() + 0.5);
            double dz = node.id().z() - (worldPosition.getZ() + 0.5);
            double d = dx * dx + dy * dy + dz * dz;

            if (d < bestDistance) {
                bestDistance = d;
                best = node.id();
            }
        }

        if (best == null) return false;

        nodeId = best.encode();
        setChanged();
        SwitchMachines.register(nodeId, worldPosition);
        return true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && !nodeId.isEmpty()) {
            SwitchMachines.register(nodeId, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!nodeId.isEmpty()) {
            SwitchMachines.unregister(nodeId, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Junction", nodeId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nodeId = tag.getString("Junction");
    }
}
