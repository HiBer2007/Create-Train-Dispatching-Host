package top.hibermod.ctdhost.execute;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;

/**
 * 把「锁闭哪条股道」翻译成汽鸣铁道的道岔状态。
 *
 * <h2>为什么是反射</h2>
 * 汽鸣铁道是**可选依赖**：没装的时候整套调度必须照常工作，只是岔口退化为逻辑锁。
 * 编译期依赖它会让「不装它就跑不起来」。
 *
 * <h2>现实的约束</h2>
 * 它的道岔只有三档：{@code NORMAL} / {@code REVERSE_LEFT} / {@code REVERSE_RIGHT}，
 * 而且 {@code trySetSwitchState} 在道岔被别的列车锁住时会**拒绝**。
 * 拒绝不是异常，是正常结果——这时候我们保留逻辑锁，等它释放。
 */
final class PhysicalSwitches {

    private static final String SWITCH_TYPE_ID = "railways:switch";

    /** 反射到的句柄，第一次成功后缓存。 */
    private static boolean resolved;
    private static Class<?> switchClass;
    private static Method getTargetState;
    private static Method trySetSwitchState;
    private static Method getSwitchState;
    private static Object switchEdgePointType;

    private static boolean warned;

    private PhysicalSwitches() {}

    /** 物理道岔在这一侧可用吗（装了汽鸣铁道且能找到它的边点类型）。 */
    static boolean available() {
        resolve();
        return switchEdgePointType != null;
    }

    /**
     * 尝试把某岔口扳向指定股道。
     *
     * @param graph 该岔口所在的图
     * @param node  岔口节点
     * @param want  期望通往的那条边
     * @return 真的扳动了返回 true；没有对应道岔、方向无效、被锁住都返回 false
     */
    static boolean throwTo(TrackGraph graph, Rail.NodeId node, Rail.EdgeId want) {
        resolve();
        if (switchEdgePointType == null || graph == null) return false;

        try {
            Collection<?> switches = pointsOf(graph);
            if (switches == null || switches.isEmpty()) return false;

            // 期望的股道由它两端的节点之一表示：道岔的「出口」就是某个节点
            TrackNodeLocation targetA = toLocation(want.first());
            TrackNodeLocation targetB = toLocation(want.second());

            for (Object sw : switches) {
                if (sw == null || !switchClass.isInstance(sw)) continue;

                Object state = null;
                for (TrackNodeLocation loc : new TrackNodeLocation[] { targetA, targetB }) {
                    if (loc == null) continue;
                    Object candidate = getTargetState.invoke(sw, loc);
                    if (candidate != null) {
                        state = candidate;
                        break;
                    }
                }
                if (state == null) continue;              // 这架道岔不通向那条股道

                Object current = getSwitchState.invoke(sw);
                if (state.equals(current)) return true;   // 已经是这个位置

                Object ok = trySetSwitchState.invoke(sw, state);
                if (Boolean.TRUE.equals(ok)) return true;

                // 被别的列车锁住：不是错误，保持逻辑锁即可
                return false;
            }
        } catch (Throwable t) {
            warnOnce("physical switch failed: " + t);
        }
        return false;
    }

    private static Collection<?> pointsOf(TrackGraph graph) {
        try {
            Method getPoints = TrackGraph.class.getMethod("getPoints",
                com.simibubi.create.content.trains.graph.EdgePointType.class);
            return (Collection<?>) getPoints.invoke(graph, switchEdgePointType);
        } catch (Throwable t) {
            return null;
        }
    }

    private static TrackNodeLocation toLocation(Rail.NodeId id) {
        if (id == null) return null;
        // TrackNodeLocation 的构造是 (double x, double y, double z)，内部按半格存
        TrackNodeLocation loc = new TrackNodeLocation(
            id.x2() / 2.0, id.y2() / 2.0, id.z2() / 2.0).in(id.dimension());
        loc.yOffsetPixels = id.yOffsetPixels();
        return loc;
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;

        try {
            Class<?> edgePointType = Class.forName(
                "com.simibubi.create.content.trains.graph.EdgePointType");
            @SuppressWarnings("unchecked")
            Map<Object, Object> types =
                (Map<Object, Object>) edgePointType.getField("TYPES").get(null);

            Object found = null;
            for (var entry : new HashMap<>(types).entrySet()) {
                if (String.valueOf(entry.getKey()).equals(SWITCH_TYPE_ID)) {
                    found = entry.getValue();
                    break;
                }
            }
            if (found == null) return;                    // 没装汽鸣铁道

            switchEdgePointType = found;
            switchClass = Class.forName("com.railwayteam.railways.content.switches.TrackSwitch");
            getTargetState = switchClass.getMethod("getTargetState", TrackNodeLocation.class);
            trySetSwitchState = switchClass.getMethod("trySetSwitchState",
                Class.forName("com.railwayteam.railways.content.switches.TrackSwitchBlock$SwitchState"));
            getSwitchState = switchClass.getMethod("getSwitchState");
        } catch (Throwable t) {
            // 没有汽鸣铁道是正常情况，不报错也不必再试
            switchEdgePointType = null;
        }
    }

    private static void warnOnce(String message) {
        if (warned) return;
        warned = true;
        System.out.println("[ctdhost] " + message);
    }
}
