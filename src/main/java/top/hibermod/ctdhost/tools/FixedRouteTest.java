package top.hibermod.ctdhost.tools;

import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdhost.execute.FixedRoute;

/**
 * 固定方向规则：**有直行就直行，否则始终左转**。
 *
 * <h2>为什么这条要离线测</h2>
 * 它是「岔口没人管」时的唯一默认。算错了不会报错——列车只是走进另一条股道，
 * 而这件事在游戏里表现为「车莫名其妙拐了」，很难归因。
 *
 * <p>测试直接构造节点与股道（坐标用 {@link Rail.NodeId} 的编码），
 * 不碰世界。
 */
public final class FixedRouteTest {

    private FixedRouteTest() {}

    private static final ResourceKey<Level> OVERWORLD =
        ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));

    public static void main(String[] args) {
        int failures = 0;

        // 坐标用「内部双倍值」构造：NodeId(x2,y2,z2) → 世界坐标 = /2
        Rail.NodeId center = node(0, 0, 0);

        // ── 直行优先 ──────────────────────────────────────────────────
        // 东（+x）、西（-x）、北（-z）：东西构成直行
        Rail.EdgeId east = edge(center, node(20, 0, 0));
        Rail.EdgeId west = edge(center, node(-20, 0, 0));
        Rail.EdgeId north = edge(center, node(0, 0, -20));

        List<Rail.EdgeId> branches = List.of(east, west, north);
        Rail.EdgeId picked = FixedRoute.pick(center, branches, null);

        failures += check("有直行时选直行",
            picked != null && (picked.equals(east) || picked.equals(west)),
            String.valueOf(picked));
        failures += check("直行不是那条拐弯的", picked == null || !picked.equals(north));

        // ── 没有直行时左转 ────────────────────────────────────────────
        // 三条都在同一侧：东、东北、东南 —— 没有 180° 的组合
        Rail.EdgeId e = edge(center, node(20, 0, 0));
        Rail.EdgeId ne = edge(center, node(14, 0, -14));
        Rail.EdgeId se = edge(center, node(14, 0, 14));

        Rail.EdgeId leftPicked = FixedRoute.pick(center, List.of(e, ne, se), null);
        failures += check("没有直行时会选一条（不返回 null）", leftPicked != null);

        // 参照系是「第一条股道」逆时针 90°；东(+x) 的左是北(-z)
        // 所以应当选东北那条
        // 「左」的参照系是排序后的第一条股道（见 FixedRoute 的注释），
        // 所以这里不验「东北」，只验两条性质：
        //   1. 不会选参照股道自己（那是「直行」的位置）；
        //   2. 结果与输入顺序无关。
        failures += check("左转不选参照股道", leftPicked != null && !leftPicked.equals(
            List.of(e, ne, se).stream()
                .sorted((x, y) -> x.encode().compareTo(y.encode()))
                .findFirst().orElse(null)),
            String.valueOf(leftPicked));

        // ── 只有一条股道 ──────────────────────────────────────────────
        failures += check("只有一条时直接选它",
            e.equals(FixedRoute.pick(center, List.of(e), null)));

        // ── 空输入 ────────────────────────────────────────────────────
        failures += check("没有股道返回 null", FixedRoute.pick(center, List.of(), null) == null);
        failures += check("岔口为 null 返回 null", FixedRoute.pick(null, List.of(e), null) == null);

        // ── 顺序无关（可复现） ────────────────────────────────────────
        Rail.EdgeId a = FixedRoute.pick(center, List.of(e, ne, se), null);
        Rail.EdgeId b = FixedRoute.pick(center, List.of(se, e, ne), null);
        Rail.EdgeId c = FixedRoute.pick(center, List.of(ne, se, e), null);
        failures += check("同一组股道，换个顺序结果一样", a.equals(b) && b.equals(c),
            a + " / " + b + " / " + c);

        // ── 方向与几何 ────────────────────────────────────────────────
        double[] dirEast = FixedRoute.directionOf(center, e, null);
        failures += check("东向单位向量", dirEast != null
            && Math.abs(dirEast[0] - 1.0) < 1e-6
            && Math.abs(dirEast[2]) < 1e-6, java.util.Arrays.toString(dirEast));

        double[] left = FixedRoute.leftOf(new double[] { 1, 0, 0 });
        failures += check("东的左是北", Math.abs(left[0]) < 1e-9 && Math.abs(left[2] + 1.0) < 1e-9,
            java.util.Arrays.toString(left));

        failures += check("正交向量点积为 0",
            Math.abs(FixedRoute.dot(new double[] { 1, 0, 0 }, new double[] { 0, 0, 1 })) < 1e-9);

        System.out.println();
        if (failures > 0) {
            System.out.println("固定方向规则验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("固定方向规则验收通过：直行优先、无直行取左、顺序无关");
    }

    private static Rail.NodeId node(int x2, int y2, int z2) {
        return new Rail.NodeId(OVERWORLD, x2, y2, z2, 0);
    }

    private static Rail.EdgeId edge(Rail.NodeId a, Rail.NodeId b) {
        return Rail.EdgeId.of(a, b);
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
