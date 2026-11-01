package top.hibermod.ctdhost.execute;

import java.util.List;

import com.simibubi.create.Create;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdhost.Failures;

/**
 * 把「没装自动道岔机的岔口走固定方向」**真正执行**。
 *
 * <h2>为什么需要它</h2>
 * 光在 {@link SwitchApplier} 里拒绝指令，只保证「调度不会去扳它」；
 * 但岔口本身停在哪条股道是**上一次**留下的。如果它停在侧线，
 * 而规则说「有直行就直行」，那么车会走进侧线——规则说了却没人执行。
 *
 * <p>所以这里每 40 刻对每个未装机的岔口做一次：算出固定方向，
 * 如果物理道岔不在那儿，就扳过去。
 *
 * <h2>装了机器的岔口不碰</h2>
 * 那些归信号系统管，由 {@link SwitchApplier} 的锁驱动。
 * 两套逻辑同时动同一个岔口会互相打架。
 */
public final class SwitchEnforcement {

    private SwitchEnforcement() {}

    /** 每多少刻执行一次。40 刻 = 2 秒。 */
    public static final int INTERVAL_TICKS = 40;

    private static long lastRun = -1L;
    private static int lastAttempted;
    private static int lastThrown;

    /** 每 {@value #INTERVAL_TICKS} 刻跑一次；返回这次扳动了几个。 */
    public static int tick(Topology topology, long gameTime) {
        if (topology == null) return 0;
        if (lastRun >= 0 && gameTime - lastRun < INTERVAL_TICKS) return 0;
        lastRun = gameTime;

        if (!PhysicalSwitches.available()) {
            lastAttempted = 0;
            lastThrown = 0;
            return 0;
        }

        int attempted = 0;
        int thrown = 0;

        for (var node : topology.nodes()) {
            if (!node.isSwitch()) continue;
            if (SwitchMachines.hasMachine(node.id())) continue;   // 归信号系统管

            List<Rail.EdgeId> branches = new java.util.ArrayList<>();
            for (var edge : topology.edgesFrom(node.id())) {
                branches.add(edge.id());
            }
            if (branches.isEmpty()) continue;

            Rail.EdgeId want = FixedRoute.pick(node.id(), branches, topology);
            if (want == null) continue;

            attempted++;
            try {
                for (var graph : Create.RAILWAYS.trackNetworks.values()) {
                    if (PhysicalSwitches.throwTo(graph, node.id(), want)) {
                        thrown++;
                        break;
                    }
                }
            } catch (Throwable t) {
                Failures.caught("固定方向：扳 " + node.id().encode(), t);
            }
        }

        lastAttempted = attempted;
        lastThrown = thrown;
        return thrown;
    }

    public static String counters() {
        return "fixedAttempted=" + lastAttempted + " fixedThrown=" + lastThrown;
    }

    public static void reset() {
        lastRun = -1L;
        lastAttempted = 0;
        lastThrown = 0;
    }
}
