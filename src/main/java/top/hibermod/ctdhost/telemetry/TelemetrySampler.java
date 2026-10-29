package top.hibermod.ctdhost.telemetry;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;

import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.interop.EnergyProvider;
import top.hibermod.ctdhost.topology.StableIds;

/**
 * {@code Train} → {@link Telemetry.TrainState}。
 *
 * <h2>位置从哪来</h2>
 * {@code Carriage.leadingBogey().leading()} 拿到一个 {@link TravellingPoint}，
 * 它的 {@code node1 / node2 / position} 就是「在边上、距 node1 有多远」。
 * 这是 Create 里唯一权威的位置表达——不要用实体的世界坐标反推，
 * 那条路在弯道和坡道上会偏。
 *
 * <h2>已知缺口（写清楚，不装作有）</h2>
 * <ul>
 *   <li>{@code odometer}：{@code Train} 没有累计里程字段，先给 0。
 *       要真里程得自己按 {@code speed} 累加，那属于宿主状态，不是采样。</li>
 *   <li>{@code footprint}：逐节车厢的占用区间需要遍历每个 bogey 并处理跨边，
 *       当前留空。影响的是站台容纳判断，不影响闭塞。</li>
 *   <li>{@code route}：需要读 {@code Train.navigation} 的 {@code currentPath}，
 *       待 C5 一起处理。</li>
 * </ul>
 */
public final class TelemetrySampler {

    private TelemetrySampler() {}

    /**
     * 采样一列车。
     *
     * @param authority 宿主上一帧施加的授权；没有则传 {@link Double#MAX_VALUE}
     * @param energy    能源联动方给的样本；没有则传 {@link EnergyProvider.EnergySample#unknown()}
     */
    public static Telemetry.TrainState sample(Train train, double authority,
                                              EnergyProvider.EnergySample energy) {
        Rail.EdgeId leadingEdge = null;
        double leadingDistance = 0;
        Rail.EdgeId trailingEdge = null;
        double trailingDistance = 0;

        List<Carriage> carriages = train.carriages;
        if (carriages != null && !carriages.isEmpty()) {
            TravellingPoint front = leadingPoint(carriages.get(0));
            if (front != null) {
                leadingEdge = edgeOf(front);
                leadingDistance = front.position;
            }
            TravellingPoint back = trailingPoint(carriages.get(carriages.size() - 1));
            if (back != null) {
                trailingEdge = edgeOf(back);
                trailingDistance = back.position;
            }
        }

        return new Telemetry.TrainState(
            train.id,
            train.name == null ? Component.literal("train") : train.name,
            train.getTotalLength(),
            leadingEdge,
            leadingDistance,
            trailingEdge,
            trailingDistance,
            List.of(),                         // footprint：见类注释
            train.speed,
            train.targetSpeed,
            train.acceleration(),
            train.maxSpeed(),
            train.maxTurnSpeed(),
            carriages == null ? 0 : carriages.size(),
            train.doubleEnded,
            train.manualTick,
            train.derailed,
            0.0,                               // odometer：见类注释
            null,                              // route：待 C5
            authority,
            energy == null ? EnergyProvider.EnergySample.unknown() : energy,
            java.util.Set.of());
    }

    @Nullable
    private static TravellingPoint leadingPoint(Carriage carriage) {
        CarriageBogey bogey = carriage.leadingBogey();
        return bogey == null ? null : bogey.leading();
    }

    @Nullable
    private static TravellingPoint trailingPoint(Carriage carriage) {
        CarriageBogey bogey = carriage.trailingBogey();
        return bogey == null ? null : bogey.trailing();
    }

    @Nullable
    private static Rail.EdgeId edgeOf(TravellingPoint point) {
        if (point == null || point.node1 == null || point.node2 == null) return null;
        return StableIds.edge(point.node1, point.node2);
    }

    /** 便捷重载：不带授权与能源。 */
    public static Telemetry.TrainState sample(Train train) {
        return sample(train, Double.MAX_VALUE, EnergyProvider.EnergySample.unknown());
    }

    /** 一列车的稳定 id。 */
    public static UUID idOf(Train train) {
        return train.id;
    }
}
