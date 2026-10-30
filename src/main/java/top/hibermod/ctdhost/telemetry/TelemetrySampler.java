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
        return sample(train, authority, energy, null);
    }

    /** 带进路状态的采样。 */
    public static Telemetry.TrainState sample(Train train, double authority,
                                              EnergyProvider.EnergySample energy,
                                              Telemetry.RouteState route) {
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
            top.hibermod.ctdhost.telemetry.Odometers.advance(train.id, train.speed),
            route,
            authority,
            energy == null ? EnergyProvider.EnergySample.unknown() : energy,
            signalSystemsOf(train));
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
    /**
     * 这列车装了哪些信号系统的车载设备。
     *
     * <p>判据是**构造物里实际存在的方块**，与 ctdscript 侧同一份事实。
     * 这里按方块 id 判断而不是引用那边的类：宿主不该编译期依赖脚本运行时，
     * 否则两个模组无法单独安装。
     */
    private static java.util.Set<net.minecraft.resources.ResourceLocation> signalSystemsOf(
            Train train) {
        java.util.Set<net.minecraft.resources.ResourceLocation> out = new java.util.HashSet<>();
        if (train.carriages == null) return out;

        for (Carriage carriage : train.carriages) {
            var entity = carriage.anyAvailableEntity();
            if (entity == null || entity.getContraption() == null) continue;

            for (var info : entity.getContraption().getBlocks().values()) {
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(info.state().getBlock());
                if ("ctdscript:onboard_cbtc_atp".equals(id.toString())) {
                    out.add(net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath("ctd", "cbtc"));
                }
            }
        }
        return out;
    }
    /** 清掉已经不存在的列车的里程记录。 */
    public static void prune(java.util.Set<java.util.UUID> aliveTrainIds) {
        top.hibermod.ctdhost.telemetry.Odometers.prune(aliveTrainIds);
    }
}