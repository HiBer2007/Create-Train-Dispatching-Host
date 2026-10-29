package top.hibermod.ctdhost.execute;

import java.util.List;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackEdge;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.status.Restrictions;
import top.hibermod.ctdhost.topology.StableIds;

/**
 * 把**线路限制**落到列车上。
 *
 * <h2>为什么不放进 SpeedApplier</h2>
 * {@code SpeedApplier} 处理的是「内核给的指令」——那是可选的、来自脚本的。
 * 限速是**线路属性**：即使一个内核都没装、一个脚本都没写，施工限速也必须生效。
 * 两者混在一起，就会有人把限速策略写进内核里，然后发现「没装内核时车在施工区飞驰」。
 *
 * <h2>单位</h2>
 * Create 的速度是**格/刻**，1 格/刻 = 72 km/h。
 * 用户填的是 km/h，这里换算；反过来写会让限速差 72 倍，而且看起来很「正常」。
 */
public final class RestrictionsApplier {

    /** 一格/刻 = 20 格/秒 = 72 km/h。 */
    public static final double KMH_PER_UNIT = 72.0;

    private RestrictionsApplier() {}

    /** km/h → 格/刻。 */
    public static double toUnits(double kmh) {
        return kmh / KMH_PER_UNIT;
    }

    /** 格/刻 → km/h。 */
    public static double toKmh(double units) {
        return units * KMH_PER_UNIT;
    }

    /** 这条边当前生效的最严限速（格/刻）；没有限制返回 {@link Double#MAX_VALUE}。 */
    public static double speedLimitOf(Rail.EdgeId edge, long gameTime) {
        if (edge == null) return Double.MAX_VALUE;

        double limit = Double.MAX_VALUE;
        for (Restrictions.Restriction r : Restrictions.active(gameTime)) {
            if (!r.edge().equals(edge)) continue;
            if (!r.hasSpeedLimit()) continue;
            limit = Math.min(limit, r.speedLimit());
        }
        return limit;
    }

    /** 这条边当前是否要求鸣笛。 */
    public static boolean hornRequired(Rail.EdgeId edge, long gameTime) {
        if (edge == null) return false;
        for (Restrictions.Restriction r : Restrictions.active(gameTime)) {
            if (r.edge().equals(edge) && r.kind() == Restrictions.Kind.HORN) return true;
        }
        return false;
    }

    /**
     * 列车当前所在边的标识。
     *
     * <p>取**头车车头**的那条边：限速看的是车头将要走的这段路，
     * 而不是中间某节车厢此刻压在哪根轨上。
     */
    public static Rail.EdgeId edgeOf(Train train) {
        if (train == null || train.carriages.isEmpty()) return null;
        TrackEdge edge = train.carriages.get(0).getLeadingPoint().edge;
        if (edge == null || edge.node1 == null || edge.node2 == null) return null;
        return StableIds.edge(edge.node1, edge.node2);
    }

    /**
     * 每帧调用：把限速压到目标速度上。
     *
     * @return 本帧被限制的列车数
     */
    public static int apply(List<Train> trains, long gameTime) {
        int restricted = 0;
        for (Train train : trains) {
            double limit = speedLimitOf(edgeOf(train), gameTime);
            if (limit == Double.MAX_VALUE) continue;
            if (train.targetSpeed > limit) {
                train.targetSpeed = Math.max(0.0, limit);
                restricted++;
            }
        }
        return restricted;
    }
}
