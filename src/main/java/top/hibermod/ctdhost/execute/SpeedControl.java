package top.hibermod.ctdhost.execute;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.simibubi.create.content.trains.entity.Train;

/**
 * 内核这一帧要求的目标速度，**等到 Create 寻路之后再施加**。
 *
 * <h2>为什么不是直接写 train.targetSpeed</h2>
 * 调度 pass 跑在 {@code GlobalRailwayManager.tick} 的 HEAD，而
 * {@code Navigation.tick()} 在之后的列车 tick 里会把 {@code targetSpeed}
 * 覆盖成它自己的结果。早先直接写入，等于每帧写一次、当帧被盖一次——
 * 遥测里能看到目标速度，车却永远不动。
 *
 * <p>所以这里只登记，真正写入交给 {@code NavigationMixin} 在
 * {@code Navigation.tick} 的 TAIL 完成。
 *
 * <h2>登记值的语义</h2>
 * 它已经是「内核之间取最小、按授权换算上限、再受车辆最高速约束」后的结果。
 * 施加时不再做任何决策，只负责落到 {@code Train} 上。
 */
public final class SpeedControl {

    private static final Map<UUID, Double> PENDING = new ConcurrentHashMap<>();
    private static long applied;

    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger("ctdhost-speed");

    private SpeedControl() {}

    /** 内核要求的本帧目标速度（已收敛）。 */
    public static void request(UUID trainId, double targetSpeed) {
        if (trainId == null) return;
        PENDING.put(trainId, Math.max(0.0, targetSpeed));
    }

    /** 在 Create 寻路之后调用：把登记值写进列车。 */
    public static void applyTo(Train train) {
        if (train == null) return;

        Double want = PENDING.get(train.id);
        if (want == null) return;                 // 这一帧没有内核说话：完全不干预

        if (train.manualTick || train.derailed) return;   // 手动/脱轨时 Create 说了算

        train.targetSpeed = Math.min(want, train.maxSpeed());
        train.approachTargetSpeed(1);

        if ((++applied % 200) == 0) {
            System.out.println("[speed] applied=" + applied + " pending=" + PENDING.size()
                + " want=" + want + " target=" + train.targetSpeed + " speed=" + train.speed);
        }
    }

    /** 这个列车这一帧有没有内核要求的速度。 */
    public static boolean hasRequest(java.util.UUID trainId) {
        return trainId != null && PENDING.containsKey(trainId);
    }

    public static void clear() {
        PENDING.clear();
    }

    public static int size() {
        return PENDING.size();
    }
}
