package top.hibermod.ctdhost.tools;

import top.hibermod.ctdapi.diagnostics.Failures;

/**
 * 「被兜住的失败」必须可见。
 *
 * <h2>为什么给一个日志辅助类写测试</h2>
 * 因为它存在的唯一理由是**可见性**，而可见性没法靠肉眼确认。
 * 这个仓库已经吃过一次亏：`/ctd reload` 一直失败并自动回滚，日志只有一行 WARN，
 * 于是「重载从不生效」藏了好几轮。同类问题在宿主侧还有十几处
 * `catch (Throwable ignored)`，改完之后必须有人证明「它真的会记」。
 *
 * <p>这里验证的是契约，不是实现：
 *
 * <ul>
 *   <li>第一次失败必须被记下（计数 1，并进入 {@code snapshot()}）；</li>
 *   <li>重复失败继续累加，不会因为「报过第一次」就丢；</li>
 *   <li>不同位置分开计数，互不覆盖；</li>
 *   <li>{@code null} 异常也能计数（有些地方只有失败没有异常对象）。</li>
 * </ul>
 */
public final class FailuresTest {

    private FailuresTest() {}

    public static void main(String[] args) {
        int failures = 0;

        Failures.resetForTests();

        // ① 一开始什么也没有
        failures += check("干净状态没有任何记录", !Failures.any(), "any=" + Failures.any());
        failures += check("未知位置计数为 0", Failures.count("nope") == 0);

        // ② 第一次失败被记住
        Failures.caught("Train#tick TAIL 施加速度", new IllegalStateException("boom"));

        failures += check("第一次失败被记录", Failures.any());
        failures += check("计数为 1", Failures.count("Train#tick TAIL 施加速度") == 1,
            "count=" + Failures.count("Train#tick TAIL 施加速度"));
        failures += check("出现在快照里",
            Failures.snapshot().containsKey("Train#tick TAIL 施加速度"));

        // ③ 继续累加
        for (int i = 0; i < 9; i++) {
            Failures.caught("Train#tick TAIL 施加速度", new IllegalStateException("boom"));
        }
        failures += check("重复失败继续累加", Failures.count("Train#tick TAIL 施加速度") == 10,
            "count=" + Failures.count("Train#tick TAIL 施加速度"));

        // ④ 不同位置互不干扰
        Failures.caught("Train#tickPassiveSlowdown 拦截", new RuntimeException("other"));
        failures += check("另一个位置独立计数", Failures.count("Train#tickPassiveSlowdown 拦截") == 1);
        failures += check("原位置不受影响", Failures.count("Train#tick TAIL 施加速度") == 10);
        failures += check("快照里有两项", Failures.snapshot().size() == 2,
            String.valueOf(Failures.snapshot()));

        // ⑤ null 异常也能计数（不抛）
        boolean nullOk = true;
        try {
            Failures.caught("无异常对象的失败", null);
        } catch (Throwable t) {
            nullOk = false;
        }
        failures += check("null 异常不抛且计数", nullOk && Failures.count("无异常对象的失败") == 1);

        // ⑥ 重置
        Failures.resetForTests();
        failures += check("重置后回到干净状态", !Failures.any() && Failures.count("Train#tick TAIL 施加速度") == 0);

        System.out.println();
        if (failures > 0) {
            System.out.println("失败可见性验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("失败可见性验收通过：首次即记、持续累加、分点统计、可重置");
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
