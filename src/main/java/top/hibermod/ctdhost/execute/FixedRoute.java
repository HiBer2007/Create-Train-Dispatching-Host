package top.hibermod.ctdhost.execute;

import java.util.ArrayList;
import java.util.List;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;

/**
 * 没装自动道岔机的岔口，按**固定方向**走：有直行就直行，否则始终左转。
 *
 * <h2>为什么要有这条规则</h2>
 * 岔口如果没有被任何调度系统接管，必须有一个**确定的**默认。
 * 三种可能的默认里：
 * <ul>
 *   <li>「随便挑一条」——两列车可能被判为可以同时走；</li>
 *   <li>「全部禁止」——世界直接卡死，玩家不知道为什么；</li>
 *   <li><b>「固定方向」</b>——行为可预测，且和现实里没装转辙机的道岔一致。</li>
 * </ul>
 *
 * <h2>「直行」怎么算</h2>
 * 从岔口出发，每条股道有一个方向向量。取**夹角最接近 180°** 的两条：
 * 它们构成一条穿过岔口的直通路。没有这样的组合（夹角都不够大）时，
 * 取「最靠左」的那条——把第一条股道逆时针转 90°，再找与它最接近的方向。
 *
' * <p>「左」需要参照系，而没被管的岔口**没有来车方向**可用。这里的定义是：
 * 取**排序后的第一条股道**作为参照（排序键是 {@link Rail.EdgeId#encode()}，
 * 与上游给的顺序无关），把它逆时针转 90°，选与它最接近的那条。
 *
 * <p>这是一个**约定**，不是几何上的「左」——但它有一个重要性质：
 * **同一张图每次算出的结果一样**。对「没人管的岔口」来说，
 * 可预测比「符合直觉」更重要：列车不会因为重启而换股道。'
 *
 * <p>全部计算只用坐标，不碰世界状态——所以可以离线测。
 */
public final class FixedRoute {

    private FixedRoute() {}

    /** 判定「直行」的最小夹角余弦（-1 = 完全相反）。0.5 ≈ 夹角 ≥120°。 */
    public static final double STRAIGHT_COS = -0.5;

    /**
     * 挑一条固定股道。
     *
     * @param junction  岔口节点
     * @param branches  从它出发的股道（顺序会被规范化，保证可复现）
     * @param nodes     节点 id → 节点（用来取坐标）
     * @return 选中的股道；没有股道时返回 {@code null}
     */
    public static Rail.EdgeId pick(Rail.NodeId junction, List<Rail.EdgeId> branches,
                                   Topology topology) {
        if (junction == null || branches == null || branches.isEmpty()) return null;
        if (branches.size() == 1) return branches.get(0);

        // 规范化顺序：按编码排序。否则「第一条」取决于上游给的顺序，
        // 同一张图两次算出不同的「左」。
        List<Rail.EdgeId> sorted = new ArrayList<>(branches);
        sorted.sort((a, b) -> a.encode().compareTo(b.encode()));

        List<double[]> dirs = new ArrayList<>(sorted.size());
        for (Rail.EdgeId edge : sorted) {
            double[] dir = directionOf(junction, edge, topology);
            if (dir == null) return sorted.get(0);
            dirs.add(dir);
        }

        // ① 直行：两两夹角最接近 180°
        int bestA = -1;
        int bestB = -1;
        double bestDot = 1.0;
        for (int i = 0; i < dirs.size(); i++) {
            for (int j = i + 1; j < dirs.size(); j++) {
                double dot = dot(dirs.get(i), dirs.get(j));
                if (dot < bestDot) {
                    bestDot = dot;
                    bestA = i;
                    bestB = j;
                }
            }
        }

        if (bestA >= 0 && bestDot <= STRAIGHT_COS) {
            // 两条都算「直行」；取与第一条股道更接近的那条，保证稳定
            return sorted.get(bestA);
        }

        // ② 没有直行：取最靠左的
        double[] reference = dirs.get(0);
        double[] left = leftOf(reference);

        int bestIndex = 0;
        double bestAlignment = -2.0;
        for (int i = 1; i < dirs.size(); i++) {
            double alignment = dot(dirs.get(i), left);
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                bestIndex = i;
            }
        }

        return sorted.get(bestIndex);
    }

    /** 从岔口指向股道另一端的方向（已归一化）；跨维度或取不到坐标时返回 {@code null}。 */
    public static double[] directionOf(Rail.NodeId junction, Rail.EdgeId edge, Topology topology) {
        if (edge == null) return null;

        Rail.NodeId other = edge.first().equals(junction) ? edge.second()
            : edge.second().equals(junction) ? edge.first() : null;
        if (other == null) return null;

        double dx = other.x() - junction.x();
        double dy = other.y() - junction.y();
        double dz = other.z() - junction.z();

        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) return null;

        return new double[] { dx / length, dy / length, dz / length };
    }

    /** 绕 Y 轴逆时针转 90°：{@code (x,z) → (z,-x)}。 */
    public static double[] leftOf(double[] dir) {
        return new double[] { dir[2], dir[1], -dir[0] };
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** 一句话说明这个岔口为什么这么走（命令输出用）。 */
    public static String describe(Rail.NodeId junction, List<Rail.EdgeId> branches, Topology topology) {
        if (branches == null || branches.isEmpty()) return "没有股道";

        Rail.EdgeId picked = pick(junction, branches, topology);
        if (picked == null) return "没有股道";

        // 重算一次原因，供人核对
        List<Rail.EdgeId> sorted = new ArrayList<>(branches);
        sorted.sort((a, b) -> a.encode().compareTo(b.encode()));
        if (sorted.size() == 1) return "只有一条股道";

        List<double[]> dirs = new ArrayList<>();
        for (Rail.EdgeId edge : sorted) {
            double[] dir = directionOf(junction, edge, topology);
            if (dir == null) return "取不到坐标";
            dirs.add(dir);
        }

        double bestDot = 1.0;
        for (int i = 0; i < dirs.size(); i++) {
            for (int j = i + 1; j < dirs.size(); j++) {
                bestDot = Math.min(bestDot, dot(dirs.get(i), dirs.get(j)));
            }
        }

        return (bestDot <= STRAIGHT_COS ? "直行" : "左转（无直行）")
            + " → " + picked.encode();
    }
}
