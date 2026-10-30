package top.hibermod.ctdhost.telemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 每列车的累计里程（格）。
 *
 * <h2>为什么需要它</h2>
 * 移动闭塞的核心量是「前车离我多远」。Create 的 {@code TravellingPoint} 只给
 * 「我在哪条边、边上第几格」，两个在不同边上的车无法直接相减——
 * 一条边一个坐标系，跨边就没有可比性。
 *
 * <h2>怎么算</h2>
 * 每帧把本帧位移（{@code train.speed}，Create 的单位就是「每 tick 前进多少格」）
 * 累加到该车的计数器上。前进为正、倒退为负，所以倒车时里程会减少，
 * 「前车里程 − 本车里程」这个差值在倒车时仍然成立。
 *
 * <h2>它的边界在哪</h2>
 * 这是**沿列车自己走过的路**累计的量，不是线路上的绝对坐标。
 * 两列车只有在同一条线、按同一方向行驶时，差值才等于间距；
 * 分叉到不同股道之后，差值没有物理意义。
 * 真正的位置化里程需要「从线路原点沿进路累计」，那要等进路模型落地。
 * 在那之前，宁可给一个边界清楚的近似量，也不要给一个看起来精确的错数。
 */
final class Odometers {

    private static final Map<UUID, Double> VALUE = new HashMap<>();

    private Odometers() {}

    /** 累加一帧位移，返回该车的新里程。 */
    static double advance(UUID trainId, double speedThisTick) {
        double next = VALUE.getOrDefault(trainId, 0.0) + speedThisTick;
        VALUE.put(trainId, next);
        return next;
    }

    /** 清掉已经消失的列车，避免长时间运行后无限增长。 */
    static void prune(Set<UUID> alive) {
        VALUE.keySet().retainAll(alive);
    }

    static int size() {
        return VALUE.size();
    }
}
