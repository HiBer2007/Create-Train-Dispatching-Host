package top.hibermod.ctdhost.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import top.hibermod.ctdhost.block.CtdBlocks;

/**
 * 屏蔽门方块实体：门号 / 车站号 / 站台号 / 开合速度 / 滑动进度。
 *
 * <p>开合由调度下发的 {@code DoorStates} 驱动，进度按「速度」匀速推进，
 * 渲染器据 {@link #progress()} 平移两扇门。
 */
public class CtdPsdDoorBlockEntity extends BlockEntity {

    private String station = "";
    private String platform = "";
    private String doorId = "1";
    /** 每秒滑动多少个方块（0.5 = 两秒走完）。 */
    private float speed = 1.0f;
    /** 0 = 全关，1 = 全开。 */
    private float progress;
    private boolean open;

    /** 门高（格），决定形状与渲染高度；由放置时的撑高逻辑写入。 */
    private float height = 2f;
    /** 门宽（格）：固定门 1，滑动门 2 或 3。 */
    private float width = 2f;

    /** 调试模式：用红石信号开关门，不读调度系统。 */
    private boolean testMode = false;

    public CtdPsdDoorBlockEntity(BlockPos pos, BlockState state) {
        super(CtdBlocks.PSD_DOOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CtdPsdDoorBlockEntity be) {
        if (level.isClientSide) return;

        boolean shouldOpen;
        if (be.testMode) {
            // 调试模式：红石信号控制，方便在没有调度系统时验证动画与碰撞
            shouldOpen = level.hasNeighborSignal(pos);
        } else {
            try {
                shouldOpen = top.hibermod.ctdapi.status.DoorStates
                    .isOpen(be.station, be.platform);
            } catch (Throwable t) {
                shouldOpen = false;
            }
        }

        if (shouldOpen != be.open) {
            be.open = shouldOpen;
            be.setChanged();
        }

        float step = be.speed / 20f;
        float target = be.open ? 1f : 0f;
        if (Math.abs(be.progress - target) > 0.001f) {
            be.progress += Math.signum(target - be.progress) * Math.min(step, Math.abs(target - be.progress));
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public float progress() {
        return progress;
    }

    public String station() { return station; }
    public void setStation(String v) { station = v == null ? "" : v; setChanged(); }

    public String platform() { return platform; }
    public void setPlatform(String v) { platform = v == null ? "" : v; setChanged(); }

    public String doorId() { return doorId; }
    public void setDoorId(String v) { doorId = v == null ? "" : v; setChanged(); }

    public float speed() { return speed; }
    public void setSpeed(float v) { speed = Math.max(0.1f, Math.min(8f, v)); setChanged(); }

    public boolean testMode() { return testMode; }
    public void setTestMode(boolean v) { testMode = v; setChanged(); }

    public float height() { return height <= 0 ? 2f : height; }
    public void setHeight(float v) { height = Math.max(1.5f, Math.min(3f, v)); setChanged(); }

    public float width() { return width < 1f ? 1f : Math.min(3f, width); }
    public void setWidth(float v) { width = Math.max(1f, Math.min(3f, v)); setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Station", station);
        tag.putString("Platform", platform);
        tag.putString("DoorId", doorId);
        tag.putFloat("Speed", speed);
        tag.putFloat("Progress", progress);
        tag.putBoolean("Open", open);
        tag.putFloat("Height", height);
        tag.putFloat("Width", width);
        tag.putBoolean("TestMode", testMode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        station = tag.getString("Station");
        platform = tag.getString("Platform");
        doorId = tag.getString("DoorId");
        speed = tag.contains("Speed") ? tag.getFloat("Speed") : 1.0f;
        progress = tag.getFloat("Progress");
        open = tag.getBoolean("Open");
        height = tag.contains("Height") ? tag.getFloat("Height") : 2f;
        width = tag.contains("Width") ? tag.getFloat("Width") : 2f;
        testMode = tag.getBoolean("TestMode");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
