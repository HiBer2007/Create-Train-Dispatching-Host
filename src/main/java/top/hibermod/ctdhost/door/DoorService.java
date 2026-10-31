package top.hibermod.ctdhost.door;

import java.util.List;

import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import top.hibermod.ctdhost.Failures;

/**
 * 车门的接管：每若干刻算一次「哪些站台现在可以开门」，然后执行。
 *
 * <h2>判定规则（可解释、可复现）</h2>
 * 一个站台允许开门，当且仅当：
 * <ol>
 *   <li>有一列车停在站台附近（车厢中心距离站台方块 ≤ {@value #TRAIN_RADIUS} 格）；</li>
 *   <li>该车速度接近 0（≤ {@value #STOPPED_SPEED} 格/刻）；</li>
 *   <li>该车没有脱轨、没有被暂停在「装配中」状态。</li>
 * </ol>
 *
 * <p>这三条都能从 {@code Train} 直接读到，不依赖任何渲染状态，
 * 所以可以在服务端判定、可以用命令复现。
 *
 * <h2>它做什么、不做什么</h2>
 * <ul>
 *   <li><b>做</b>：把结论写进 {@link DoorPermissions}；对范围内的 Create 滑动门调用
 *       {@code setOpen}；对信号模式的门解除红石/手动控制（见 {@code SlidingDoorBlockMixin}）。</li>
 *   <li><b>不做</b>：决定列车该不该停、该不该走——那是信号系统与内核的事。
 *       这里只是把「车停稳了」翻译成「门可以开」。</li>
 * </ul>
 */
public final class DoorService {

    private DoorService() {}

    /** 车离站台多远算「在站台旁」。 */
    public static final double TRAIN_RADIUS = 12.0;

    /** 速度低于这个值算停稳（格/刻）。 */
    public static final double STOPPED_SPEED = 0.01;

    /** 门离站台方块多远算「这个站台的门」。 */
    public static final int DOOR_RADIUS = 10;

    /** 每多少刻算一次。20 刻 = 1 秒，够用且不贵。 */
    public static final int INTERVAL_TICKS = 20;

    // 注意：不能用 Long.MIN_VALUE 当初值——gameTime - Long.MIN_VALUE 会溢出成负数，
    // 于是 `gameTime - lastRun < 周期` 永远成立，整个服务一次都不跑（实测踩过）。
    private static long lastRun = -1L;
    private static int lastScanned, lastCandidates, lastSkippedSignal, lastSkippedSame;
    private static int lastAllowedCount;
    private static int lastDoorCount;

    /** 每 {@value #INTERVAL_TICKS} 刻跑一次。 */
    public static void tick(ServerLevel level, long gameTime) {
        if (lastRun >= 0 && gameTime - lastRun < INTERVAL_TICKS) return;
        lastRun = gameTime;

        int allowedStations = 0;
        // 计数按「一轮」累加，不按「一个站」重置——否则看到的永远是最后一个站。
        lastScanned = 0; lastCandidates = 0; lastSkippedSignal = 0; lastSkippedSame = 0;
        int touchedDoors = 0;

        try {
            for (var graph : Create.RAILWAYS.trackNetworks.values()) {
                for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                    BlockPos stationPos = station.getBlockEntityPos();
                    if (stationPos == null) continue;

                    // 先记自动判定，再取「显式优先」的最终结论——
                    // 顺序反了会让 /ctd host doors open 变成一句空话（实测踩过）。
                    boolean stopped = isTrainStoppedAt(level, stationPos);
                    DoorPermissions.setAuto(stationPos, stopped, gameTime);
                    boolean allowed = DoorPermissions.allowed(stationPos, stopped);

                    if (allowed) allowedStations++;
                    touchedDoors += applyToDoors(level, stationPos, allowed);
                }
            }
        } catch (Throwable t) {
            Failures.caught("车门接管：判定阶段", t);
        }

        lastAllowedCount = allowedStations;
        lastDoorCount = touchedDoors;
    }

    /** 有没有一列车停在这个站台旁边。 */
    public static boolean isTrainStoppedAt(ServerLevel level, BlockPos stationPos) {
        for (var train : Create.RAILWAYS.trains.values()) {
            if (train.carriages.isEmpty()) continue;

            for (var carriage : train.carriages) {
                var entity = carriage.anyAvailableEntity();
                if (entity == null) continue;
                if (entity.level() != level) continue;
                // 判定交给纯规则：这里只负责取数。
                double distanceSq = entity.position().distanceToSqr(
                    stationPos.getX() + 0.5, stationPos.getY() + 0.5, stationPos.getZ() + 0.5);
                if (DoorRules.canOpenAt(distanceSq, train.speed, train.derailed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 把结论落到这个站台附近的门上。
     *
     * <p>只动 {@code SlidingDoorBlock}（Create 的门）；红石门由门控制器输出红石，
     * 不在这里直接改方块——直接改别人的方块状态是破坏性的。
     *
     * @return 实际被设置过的门数量
     */
    private static int applyToDoors(ServerLevel level, BlockPos stationPos, boolean allowed) {
        int changed = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                stationPos.offset(-DOOR_RADIUS, -4, -DOOR_RADIUS),
                stationPos.offset(DOOR_RADIUS, 4, DOOR_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            lastScanned++;
            if (!(state.getBlock() instanceof SlidingDoorBlock door)) continue;
            lastCandidates++;

            // 信号模式的门才由我们接管；其余门保持 Create 原有行为。
            SignalDoorBehaviour behaviour = SignalDoorBehaviour.at(level, pos);
            if (behaviour == null || !behaviour.isSignalControlled()) {
                lastSkippedSignal++;
                continue;
            }

            boolean open = SlidingDoorBlockEntity.isOpen(state);
            if (open == allowed) {
                lastSkippedSame++;
                continue;
            }

            try {
                door.setOpen(null, level, state, pos.immutable(), allowed);
                changed++;
            } catch (Throwable t) {
                Failures.caught("车门接管：开门 " + pos, t);
            }
        }

        return changed;
    }

    /** 上一次扫描的滑门数 / 候选数 / 被跳过数（诊断用）。 */
    public static String counters() {
        return "scanned=" + lastScanned + " candidates=" + lastCandidates
            + " skippedNotSignal=" + lastSkippedSignal + " skippedSame=" + lastSkippedSame;
    }

    /** 上一次判定允许开门的站台数（诊断用）。 */
    public static int lastAllowedStations() {
        return lastAllowedCount;
    }

    /** 上一次判定实际动过的门数（诊断用）。 */
    public static int lastTouchedDoors() {
        return lastDoorCount;
    }

    public static void reset() {
        lastRun = -1L;
        lastAllowedCount = 0;
        lastDoorCount = 0;
        DoorPermissions.clear();
    }

    /** 供 mixin 用：把某个站台的判定结果转成布尔。 */
    public static boolean allowedAt(BlockPos stationPos, boolean fallback) {
        return DoorPermissions.allowed(stationPos, fallback);
    }

    /** 列出当前所有门的信号模式状态（命令用）。 */
    public static List<String> describe(ServerLevel level, BlockPos stationPos) {
        List<String> out = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                stationPos.offset(-DOOR_RADIUS, -4, -DOOR_RADIUS),
                stationPos.offset(DOOR_RADIUS, 4, DOOR_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SlidingDoorBlock)) continue;

            SignalDoorBehaviour behaviour = SignalDoorBehaviour.at(level, pos);
            boolean signal = behaviour != null && behaviour.isSignalControlled();
            out.add(pos.toShortString() + " signalMode=" + signal
                + " open=" + SlidingDoorBlockEntity.isOpen(state));
        }
        return out;
    }

    /** 只是为了让 IDE 的未使用告警闭嘴：Block 类型在扫描里用过。 */
    private static final Class<?> BLOCK_TYPE = Block.class;
}
