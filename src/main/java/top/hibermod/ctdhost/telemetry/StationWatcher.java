package top.hibermod.ctdhost.telemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;

import top.hibermod.ctdapi.operation.OperationFeed;
import top.hibermod.ctdapi.operation.TrainAtStation;

/**
 * 车站相位观察器：把「列车接近 / 停稳 / 离站」翻译成运营事件。
 *
 * <h2>为什么放在宿主</h2>
 * 只有宿主每帧看得到列车对象（{@code Create.RAILWAYS.trains}）。
 * 但它不该知道「广播怎么说」——所以只往契约层
 * （{@link OperationFeed#trainAtStation}）发一条结构化事件，
 * 文案由脚本决定。
 *
 * <h2>为什么只报变化</h2>
 * 按 tick 报会一秒几十条，广播变成噪音；按站报一次才是运营语义。
 * 所以这里记每列车「上一次报的相位」，相同就不重复发。
 */
public final class StationWatcher {

    /** 进入接近区段的距离（格）。 */
    private static final double APPROACH_RADIUS = 64;

    /** 低于这个速度算停稳（每 tick 位移）。 */
    private static final double STOPPED_SPEED = 0.01;

    /** 高于这个速度算已起步。 */
    private static final double MOVING_SPEED = 0.05;

    /** 每列车上一次报过的相位与车站。 */
    private static final Map<UUID, StationKey> LAST = new HashMap<>();

    private StationWatcher() {}

    private record StationKey(UUID station, TrainAtStation.Phase phase) {}

    /** 每帧调用。 */
    public static void tick(long gameTime) {
        LAST.keySet().removeIf(id -> !Create.RAILWAYS.trains.containsKey(id));

        for (Train train : Create.RAILWAYS.trains.values()) {
            GlobalStation destination = train.navigation == null ? null : train.navigation.destination;
            if (destination == null) {
                // 没目的地就清记录：下一站会重新报
                LAST.remove(train.id);
                continue;
            }

            double distance = train.navigation.distanceToDestination;
            TrainAtStation.Phase phase = decide(train, distance);
            if (phase == null) continue;

            StationKey key = new StationKey(destination.id, phase);
            StationKey previous = LAST.get(train.id);
            if (key.equals(previous)) continue;        // 只报变化
            LAST.put(train.id, key);

            OperationFeed.trainAtStation(new TrainAtStation(
                train.id,
                train.name == null ? "" : train.name.getString(),
                destination.id,
                destination.name,
                phase,
                gameTime,
                0));
        }
    }

    private static TrainAtStation.Phase decide(Train train, double distance) {
        StationKey previous = LAST.get(train.id);

        // 已经停稳过、现在又在动 → 离站
        if (previous != null && previous.phase == TrainAtStation.Phase.STOPPED
                && train.speed > MOVING_SPEED) {
            return TrainAtStation.Phase.DEPARTING;
        }
        if (train.speed <= STOPPED_SPEED && distance < 8) {
            return TrainAtStation.Phase.STOPPED;
        }
        if (distance < APPROACH_RADIUS) {
            return TrainAtStation.Phase.APPROACHING;
        }
        return null;
    }

    /** 自检用：当前记着几列车。 */
    public static int tracked() {
        return LAST.size();
    }

    public static void reset() {
        LAST.clear();
    }
}
