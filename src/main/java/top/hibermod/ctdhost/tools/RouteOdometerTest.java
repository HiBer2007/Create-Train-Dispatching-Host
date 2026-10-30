package top.hibermod.ctdhost.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdhost.execute.RouteApplier;
import top.hibermod.ctdhost.topology.TopologyView;
import top.hibermod.ctdhost.telemetry.Odometers;

/**
 * C4b / C4 验收：进路分配与里程累加。
 *
 * <h2>为什么这两项合在一起测</h2>
 * 它们都只依赖 API 类型（{@link Topology}、{@link Command.RouteRequest}、
 * {@link Telemetry.RouteState}），不需要世界、不需要 Create。
 * 而在此之前，这两项能力只有「能编译」和「跑起来没崩」——
 * 按 `PLAN.md` 的验收标准，那只能算 🟡。
 *
 * <h2>进路要验的是「算对没算对」</h2>
 * 剩余长度决定列车敢不敢往前走；岔口集合决定会不会两列车抢同一条股道。
 * 这两件事算错的后果都不是报错，而是行车事故。
 */
public final class RouteOdometerTest {

    private RouteOdometerTest() {}

    private static final ResourceKey<Level> DIM = Level.OVERWORLD;

    public static void main(String[] args) {
        int failures = 0;

        failures += routeTests();
        failures += odometerTests();

        System.out.println();
        if (failures > 0) {
            System.out.println("进路/里程验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("进路/里程验收通过：剩余长度、岔口合并、撤销、id 稳定性、里程累计与清理");
    }

    // ── 进路 ──────────────────────────────────────────────────────────────

    private static int routeTests() {
        int f = 0;

        Rail.NodeId a = node(0, 64, 0);
        Rail.NodeId b = node(8, 64, 0);
        Rail.NodeId c = node(16, 64, 0);

        Rail.EdgeId ab = new Rail.EdgeId(a, b);
        Rail.EdgeId bc = new Rail.EdgeId(b, c);

        Topology view = TopologyView.builder()
            .version(1)
            .edge(new Topology.Edge(ab, 8.0, false, false, "create:standard", true))
            .edge(new Topology.Edge(bc, 8.0, false, false, "create:standard", true))
            .build();

        RouteApplier applier = new RouteApplier();
        UUID train = UUID.randomUUID();

        // ① 两条边：剩余长度 = 8 + 8，岔口 = 共用的 b
        Command.RouteRequest req = new Command.RouteRequest(
            c, List.of(ab, bc), Command.RoutePriority.PASSENGER, "test");

        applier.apply(Map.of(train, req), view);

        Telemetry.RouteState st = applier.stateOf(train);
        f += check("进路已分配", st != null);
        if (st == null) return f;

        f += check("剩余长度 = 两条边之和", Math.abs(st.remaining() - 16.0) < 1e-9,
            "remaining=" + st.remaining());
        f += check("岔口 = 两条边共用的节点", st.lockedSwitches().size() == 1
            && st.lockedSwitches().get(0).equals(b), String.valueOf(st.lockedSwitches()));
        f += check("区段模型未建（设计如此）", st.lockedSections().isEmpty(),
            String.valueOf(st.lockedSections()));
        f += check("未提交状态", !st.committed());

        long firstRouteId = st.routeId().value();

        // ② 同一份申请不重新分配 id
        applier.apply(Map.of(train, req), view);
        Telemetry.RouteState again = applier.stateOf(train);
        f += check("相同申请不换 routeId", again != null && again.routeId().value() == firstRouteId,
            "before=" + firstRouteId + " after=" + (again == null ? "null" : again.routeId().value()));

        // ③ 路径里有一条未知边：只累加认得的那条，不抛异常
        Rail.EdgeId unknown = new Rail.EdgeId(b, node(24, 64, 0));
        Command.RouteRequest partial = new Command.RouteRequest(
            c, List.of(bc, unknown), Command.RoutePriority.PASSENGER, "partial");
        applier.apply(Map.of(train, partial), view);

        Telemetry.RouteState st2 = applier.stateOf(train);
        f += check("未知边被忽略，剩余长度只算已知的",
            st2 != null && Math.abs(st2.remaining() - 8.0) < 1e-9,
            "remaining=" + (st2 == null ? "null" : st2.remaining()));

        // ④ 本帧没有申请：进路撤销
        applier.apply(Map.of(), view);
        f += check("不申请即撤销", applier.stateOf(train) == null && applier.size() == 0,
            "size=" + applier.size());

        // ⑤ 空路径不崩
        Command.RouteRequest empty = new Command.RouteRequest(
            c, List.of(), Command.RoutePriority.PASSENGER, "empty");
        applier.apply(Map.of(train, empty), view);
        Telemetry.RouteState st3 = applier.stateOf(train);
        f += check("空路径剩余长度为 0", st3 != null && Math.abs(st3.remaining()) < 1e-9);
        f += check("空路径没有岔口", st3 != null && st3.lockedSwitches().isEmpty());

        // ⑥ 拓扑为 null 时不崩（启动早期可能出现）
        RouteApplier applier2 = new RouteApplier();
        applier2.apply(Map.of(train, req), null);
        f += check("拓扑为空时不崩", applier2.stateOf(train) != null);

        return f;
    }

    // ── 里程 ──────────────────────────────────────────────────────────────

    private static int odometerTests() {
        int f = 0;

        UUID train = UUID.randomUUID();

        double after1 = Odometers.advance(train, 0.5);
        f += check("首次累加等于本帧位移", Math.abs(after1 - 0.5) < 1e-9, "got " + after1);

        double after2 = Odometers.advance(train, 0.25);
        f += check("累加是累计值", Math.abs(after2 - 0.75) < 1e-9, "got " + after2);

        double back = Odometers.advance(train, -1.0);
        f += check("倒车使里程减少（有符号）", Math.abs(back + 0.25) < 1e-9, "got " + back);

        // 多列车互不影响
        UUID other = UUID.randomUUID();
        Odometers.advance(other, 3.0);
        f += check("多列车各自独立",
            Math.abs(Odometers.advance(train, 0.0) + 0.25) < 1e-9, "size=" + Odometers.size());

        // prune 清掉已消失的列车
        int before = Odometers.size();
        Odometers.prune(java.util.Set.of(train));
        f += check("prune 清掉已消失的列车",
            Odometers.size() == 1 && before >= 2,
            "before=" + before + " after=" + Odometers.size());

        Odometers.prune(java.util.Set.of());
        f += check("prune 空集合清空全部", Odometers.size() == 0, "size=" + Odometers.size());

        return f;
    }

    // ── 小工具 ────────────────────────────────────────────────────────────

    private static Rail.NodeId node(int x, int y, int z) {
        return new Rail.NodeId(DIM, x, y, z, 0);
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }

    @SuppressWarnings("unused")
    private static List<Rail.EdgeId> edges(Rail.EdgeId... e) {
        List<Rail.EdgeId> l = new ArrayList<>();
        for (Rail.EdgeId x : e) l.add(x);
        return l;
    }

    @SuppressWarnings("unused")
    private static Map<UUID, Command.RouteRequest> one(UUID id, Command.RouteRequest r) {
        Map<UUID, Command.RouteRequest> m = new HashMap<>();
        m.put(id, r);
        return m;
    }
}
