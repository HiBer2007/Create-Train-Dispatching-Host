package top.hibermod.ctdhost.tools;

import java.util.UUID;

import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;

import top.hibermod.ctdhost.execute.SignalOverrides;

/**
 * 灯色意图的规矩。
 *
 * <h2>为什么这几条必须测</h2>
 * 信号灯是安全相关的显示：写错了不会崩，只会**让司机看到错的灯**。
 * 而它的语义比「一个字段」复杂——两面独立、有有效期、只有两面都写了才允许接管。
 * 任何一条想错，表现都是「灯看起来在动，但不是因为我们的逻辑」。
 *
 * <h2>关键边界</h2>
 * <ul>
 *   <li><b>只写一面不能接管</b>：否则另一面停在上一帧的值，出现「一半我方、一半陈旧」的灯；</li>
 *   <li><b>过期必须真的失效</b>：内核崩了之后灯永久停在最后一帧，比不接管危险；</li>
 *   <li><b>后写覆盖前写，但只覆盖那面</b>：两面互不串味。</li>
 * </ul>
 */
public final class SignalOverridesTest {

    private SignalOverridesTest() {}

    public static void main(String[] args) {
        int failures = 0;

        SignalOverrides.clear();

        UUID id = UUID.randomUUID();

        // ── 空状态 ──────────────────────────────────────────────────────
        failures += check("没有意图时不接管", !SignalOverrides.shouldTakeOver(id, 100L));
        failures += check("查不到返回 null", SignalOverrides.get(id) == null);

        // ── 只写一面：不允许接管 ────────────────────────────────────────
        SignalOverrides.set(id, true, SignalState.RED, 100L);

        failures += check("只写 FIRST 时 Entry 不完整",
            !SignalOverrides.get(id).complete());
        failures += check("只写一面不接管（避免半新半旧）",
            !SignalOverrides.shouldTakeOver(id, 100L),
            "只写一面就接管会让另一面停在旧值");

        // ── 两面都写：可以接管 ──────────────────────────────────────────
        SignalOverrides.set(id, false, SignalState.GREEN, 100L);

        failures += check("两面都写后 Entry 完整", SignalOverrides.get(id).complete());
        failures += check("两面都写且新鲜 → 接管", SignalOverrides.shouldTakeOver(id, 100L));
        failures += check("两面各自保留自己的值",
            SignalOverrides.get(id).first() == SignalState.RED
                && SignalOverrides.get(id).second() == SignalState.GREEN);

        // ── 后写只覆盖那一面 ────────────────────────────────────────────
        SignalOverrides.set(id, true, SignalState.YELLOW, 110L);

        failures += check("更新一面不影响另一面",
            SignalOverrides.get(id).first() == SignalState.YELLOW
                && SignalOverrides.get(id).second() == SignalState.GREEN);
        failures += check("时间戳随最后一次写更新",
            SignalOverrides.get(id).gameTime() == 110L);

        // ── 过期 ────────────────────────────────────────────────────────
        long justInside = 110L + SignalOverrides.TTL_TICKS;
        long justOutside = 110L + SignalOverrides.TTL_TICKS + 1;

        failures += check("正好在 TTL 内仍然接管",
            SignalOverrides.shouldTakeOver(id, justInside),
            "边界应当包含（<=）");
        failures += check("超过 TTL 后不接管",
            !SignalOverrides.shouldTakeOver(id, justOutside),
            "不过期的话内核崩了灯会永久停在最后一帧");

        // ── 过期条目不阻止 prune ────────────────────────────────────────
        int before = SignalOverrides.size();
        SignalOverrides.prune(justOutside);

        failures += check("prune 清掉过期条目", SignalOverrides.size() == before - 1,
            "before=" + before + " after=" + SignalOverrides.size());
        failures += check("prune 之后查不到", SignalOverrides.get(id) == null);

        // ── 多个边界互不干扰 ────────────────────────────────────────────
        UUID other = UUID.randomUUID();
        SignalOverrides.set(other, true, SignalState.GREEN, 200L);
        SignalOverrides.set(other, false, SignalState.GREEN, 200L);

        failures += check("另一条边界独立生效",
            SignalOverrides.shouldTakeOver(other, 200L));
        failures += check("另一条边界在同一个 now 下也独立", SignalOverrides.shouldTakeOver(other, 200L));
        SignalOverrides.clear();
        failures += check("clear 之后为空", SignalOverrides.size() == 0);

        // ── 红灯永远不放宽 ──────────────────────────────────────────────
        //
        // 安全不变式：Create 已按占用/强制红算出 RED 时，我方意图无论如何都不能改成非红。
        // 否则会出现「区段里有车，灯是绿的」——那比灯不改变严重得多。
        failures += check("原版 RED + 我方 GREEN → 仍是 RED",
            SignalOverrides.resolve(SignalState.RED, SignalState.GREEN) == SignalState.RED);
        failures += check("原版 RED + 我方 YELLOW → 仍是 RED",
            SignalOverrides.resolve(SignalState.RED, SignalState.YELLOW) == SignalState.RED);
        failures += check("原版 GREEN + 我方 RED → RED（可以更严）",
            SignalOverrides.resolve(SignalState.GREEN, SignalState.RED) == SignalState.RED);
        failures += check("原版 GREEN + 我方 YELLOW → YELLOW（我方意图生效）",
            SignalOverrides.resolve(SignalState.GREEN, SignalState.YELLOW) == SignalState.YELLOW);
        failures += check("我方为空 → 保持原版",
            SignalOverrides.resolve(SignalState.GREEN, null) == SignalState.GREEN);

        System.out.println();
        if (failures > 0) {
            System.out.println("灯色意图验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("灯色意图验收通过：两面独立 / 缺一面不接管 / 过期失效 / prune 清理");
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
