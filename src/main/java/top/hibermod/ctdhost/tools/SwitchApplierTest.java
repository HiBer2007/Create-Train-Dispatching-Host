package top.hibermod.ctdhost.tools;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdhost.execute.SwitchApplier;

/**
 * C7 验收：道岔**逻辑锁**。
 *
 * <h2>为什么要单独测</h2>
 * 道岔两条路：逻辑锁（永远可用）与物理扳动（要装汽鸣铁道）。
 * 物理那条在离线环境测不了，于是「道岔翻译」这个能力一直只有代码、没有证据。
 * 而逻辑锁是**进路冲突检查的基础**——它错了，两列车会被排进同一股道，
 * 而且现场看起来完全正常。
 *
 * <p>这里不碰 Create：`SwitchApplier.apply` 只吃 {@link Command.SwitchCommand}
 * 与纯数据 id，正好可以在没有世界的环境里验证。
 */
public final class SwitchApplierTest {

    private SwitchApplierTest() {}

    public static void main(String[] args) {
        int failures = 0;

        SwitchApplier applier = new SwitchApplier();

        Rail.NodeId node = node(0, 64, 0);
        Rail.EdgeId north = edge(node, node(0, 64, 8));
        Rail.EdgeId east = edge(node, node(8, 64, 0));

        // ① 未锁时：没有任何限制
        failures += check("未锁时 lockOf 为空", applier.lockOf(node).isEmpty());
        failures += check("未锁时不拦截任何股道",
            !applier.blocks(node, north) && !applier.blocks(node, east));
        failures += check("未锁时 holder 为 0", applier.holderOf(node) == 0L);

        // ② 锁到 north
        int applied = applier.apply(
            List.of(new Command.SwitchCommand(node, Topology.SwitchPosition.NORMAL, north, 42L)),
            Map.of());
        failures += check("apply 计入生效条数", applied == 1, "applied=" + applied);
        failures += check("锁到了指定股道",
            applier.lockOf(node).map(north::equals).orElse(false),
            String.valueOf(applier.lockOf(node)));
        failures += check("holder 记录下来", applier.holderOf(node) == 42L,
            "holder=" + applier.holderOf(node));

        // ③ 冲突判定：同股道放行，别的股道拦下
        failures += check("同股道不算冲突", !applier.blocks(node, north));
        failures += check("异股道判定冲突", applier.blocks(node, east));

        // ④ 解锁
        applier.apply(
            List.of(new Command.SwitchCommand(node, Topology.SwitchPosition.UNKNOWN, null, 0L)),
            Map.of());
        failures += check("解锁后锁消失", applier.lockOf(node).isEmpty());
        failures += check("解锁后 holder 清零", applier.holderOf(node) == 0L);
        failures += check("解锁后不再拦任何股道",
            !applier.blocks(node, north) && !applier.blocks(node, east));

        // ④b 冲突时**拒绝覆盖**（而不是后来者赢）
        //
        // 早先 apply 是无条件 put：两个内核抢同一个岔口时后写的赢，
        // 而先写的那列车已经按旧股道排了进路——结果是**两列车同时被判为可以走**。
        Rail.NodeId contested = node(64, 64, 0);
        Rail.EdgeId lineA = edge(contested, node(72, 64, 0));
        Rail.EdgeId lineB = edge(contested, node(64, 64, 8));

        applier.apply(List.of(
            new Command.SwitchCommand(contested, Topology.SwitchPosition.NORMAL, lineA, 7L)),
            Map.of());

        long rejectedBefore = applier.rejected();
        applier.apply(List.of(
            new Command.SwitchCommand(contested, Topology.SwitchPosition.REVERSE_LEFT, lineB, 9L)),
            Map.of());

        failures += check("异持有人抢同一岔口被拒绝",
            applier.rejected() == rejectedBefore + 1,
            "rejected=" + applier.rejected());
        failures += check("被拒绝时不覆盖原锁",
            applier.lockOf(contested).map(lineA::equals).orElse(false),
            String.valueOf(applier.lockOf(contested)));
        failures += check("被拒绝时原持有人不变",
            applier.holderOf(contested) == 7L,
            "holder=" + applier.holderOf(contested));

        // 同一持有人改锁是允许的（它自己的进路变道）
        applier.apply(List.of(
            new Command.SwitchCommand(contested, Topology.SwitchPosition.REVERSE_LEFT, lineB, 7L)),
            Map.of());
        failures += check("同一持有人可以改锁",
            applier.lockOf(contested).map(lineB::equals).orElse(false),
            String.valueOf(applier.lockOf(contested)));

        // 清掉这一区的锁，别影响后面的计数
        applier.apply(List.of(
            new Command.SwitchCommand(contested, Topology.SwitchPosition.UNKNOWN, null, 0L)),
            Map.of());

        // ⑤ 多岔口互不干扰
        Rail.NodeId other = node(32, 64, 0);
        Rail.EdgeId otherBranch = edge(other, node(40, 64, 0));

        applier.apply(List.of(
            new Command.SwitchCommand(node, Topology.SwitchPosition.NORMAL, north, 1L),
            new Command.SwitchCommand(other, Topology.SwitchPosition.REVERSE_LEFT, otherBranch, 2L)),
            Map.of());

        failures += check("两把锁同时存在", applier.locks().size() == 2,
            "locks=" + applier.locks().size());
        failures += check("各自的 holder 不串",
            applier.holderOf(node) == 1L && applier.holderOf(other) == 2L);
        failures += check("锁 A 不影响 B 的股道", !applier.blocks(other, otherBranch));

        // ⑥ 同一持有人重复上锁：覆盖，不增长；异持有人：拒绝
        //
        // 早先这里断言的是「后一次上锁覆盖前一次」——那正是我刚废掉的行为。
        // 异持有人能覆盖，等于两列车同时被判为可以走。
        applier.apply(
            List.of(new Command.SwitchCommand(node, Topology.SwitchPosition.NORMAL, east, 1L)),
            Map.of());
        failures += check("同一持有人改锁不增加条目", applier.locks().size() == 2,
            "locks=" + applier.locks().size());
        failures += check("同一持有人可以改到自己要的股道",
            applier.lockOf(node).map(east::equals).orElse(false));

        int beforeReject = applier.locks().size();
        applier.apply(
            List.of(new Command.SwitchCommand(node, Topology.SwitchPosition.REVERSE_LEFT, north, 99L)),
            Map.of());
        failures += check("异持有人改锁被拒绝（锁没变）",
            applier.lockOf(node).map(east::equals).orElse(false)
                && applier.locks().size() == beforeReject,
            "locks=" + applier.locks().size());
        System.out.println();
        if (failures > 0) {
            System.out.println("道岔逻辑锁验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("道岔逻辑锁验收通过：上锁/解锁/冲突判定/多岔口互不干扰");
    }

    // ── 小工具 ────────────────────────────────────────────────────────────

    private static final ResourceKey<Level> DIM = Level.OVERWORLD;

    private static Rail.NodeId node(int x, int y, int z) {
        return new Rail.NodeId(DIM, x, y, z, 0);
    }

    private static Rail.EdgeId edge(Rail.NodeId a, Rail.NodeId b) {
        return new Rail.EdgeId(a, b);
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }

    @SuppressWarnings("unused")
    private static UUID anyId() {
        return UUID.randomUUID();
    }

    @SuppressWarnings("unused")
    private static ResourceLocation rl(String s) {
        return ResourceLocation.parse(s);
    }
}
