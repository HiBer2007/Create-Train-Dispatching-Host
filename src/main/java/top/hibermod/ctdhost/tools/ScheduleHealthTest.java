package top.hibermod.ctdhost.tools;

import top.hibermod.ctdhost.execute.ScheduleHealth;

/**
 * 时刻表健康判定。
 *
 * <h2>它守的是什么</h2>
 * 2026-10-30 的事故：一份 {@code cyclic=true} 但没有任何条目的时刻表被写进列车，
 * Create 的 {@code ScheduleRuntime.estimateStayDuration} 每个 tick 都
 * {@code entries.get(0)} 越界，服务端起不来。世界最后只能弃用。
 *
 * <p>这个判定把那个状态变成**可检测**的：服务端启动时、{@code /ctd selftest} 里
 * 都能提前说出来，而不是等人去翻 crash report。
 *
 * <p>纯函数所以能离线测——这正是它被写成纯函数的原因。
 */
public final class ScheduleHealthTest {

    private ScheduleHealthTest() {}

    public static void main(String[] args) {
        int failures = 0;

        // ── 会崩的那一种 ────────────────────────────────────────────────
        failures += check("cyclic 且 0 条目 → 判定为损坏", ScheduleHealth.isCorrupt(true, 0));
        failures += check("cyclic 且负条目 → 同样判定为损坏", ScheduleHealth.isCorrupt(true, -1),
            "负数不该被当成健康");

        // ── 不会崩的那几种 ──────────────────────────────────────────────
        failures += check("cyclic 且有 1 条 → 健康", !ScheduleHealth.isCorrupt(true, 1));
        failures += check("cyclic 且有 2 条 → 健康", !ScheduleHealth.isCorrupt(true, 2));
        failures += check("非 cyclic 且 0 条 → 健康（Create 返回 INVALID）",
            !ScheduleHealth.isCorrupt(false, 0),
            "Create 对非循环空表是安全的，误报会让人忽略这个检查");
        failures += check("非 cyclic 且有 1 条 → 健康", !ScheduleHealth.isCorrupt(false, 1));

        // ── 说明文本要能直接贴进日志 ───────────────────────────────────
        String bad = ScheduleHealth.describe(true, 0);
        String good = ScheduleHealth.describe(true, 2);

        failures += check("损坏时的说明含修复命令", bad.contains("/ctd host schedule clear"), bad);
        failures += check("损坏时的说明指出越界点", bad.contains("entries.get(0)"), bad);
        failures += check("健康时的说明不吓人", good.startsWith("ok"), good);
        failures += check("健康时的说明带数字", good.contains("cyclic=true") && good.contains("entries=2"), good);

        System.out.println();
        if (failures > 0) {
            System.out.println("时刻表健康判定验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("时刻表健康判定验收通过：只有 cyclic+空表算损坏，其余不误报");
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
