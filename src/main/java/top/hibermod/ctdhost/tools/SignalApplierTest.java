package top.hibermod.ctdhost.tools;

import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;

import top.hibermod.ctdapi.signal.Aspect;
import top.hibermod.ctdhost.execute.SignalApplier;

/**
 * C6 验收：语义 → 灯色的映射，以及写进 {@code SignalBoundary} 的反射路径。
 *
 * <h2>为什么用未挂载的边界</h2>
 * 挂到 {@code TrackGraph} 上的边点每帧都会走 {@code tickState}，
 * 而它会去查 {@code Create.RAILWAYS.signalEdgeGroups}——手动造的边界没有对应分组，
 * 会让**整个 tick** 出问题。所以这里只验「写进去」这一步，
 * 用的是一个不挂图的 {@code SignalBoundary} 实例，安全且完整。
 *
 * <p>这条测试跑的是**真实的 Create 类**，不是 mock：
 * 如果哪天 {@code cachedStates} 改了名字或类型，反射会立刻失败。
 */
public final class SignalApplierTest {

    private SignalApplierTest() {}

    public static void main(String[] args) {
        int failures = 0;

        // ① 七种语义全部映射，且不留缺口
        failures += check("STOP → RED",
            SignalApplier.toCreateState(Aspect.Category.STOP) == SignalState.RED);
        failures += check("DARK → RED（灭灯等同于不可通行）",
            SignalApplier.toCreateState(Aspect.Category.DARK) == SignalState.RED);
        failures += check("CAUTION → YELLOW",
            SignalApplier.toCreateState(Aspect.Category.CAUTION) == SignalState.YELLOW);
        failures += check("ADVANCE_CAUTION → YELLOW",
            SignalApplier.toCreateState(Aspect.Category.ADVANCE_CAUTION) == SignalState.YELLOW);
        failures += check("PROCEED → GREEN",
            SignalApplier.toCreateState(Aspect.Category.PROCEED) == SignalState.GREEN);
        failures += check("SHUNT → GREEN",
            SignalApplier.toCreateState(Aspect.Category.SHUNT) == SignalState.GREEN);
        failures += check("CALL_ON → GREEN",
            SignalApplier.toCreateState(Aspect.Category.CALL_ON) == SignalState.GREEN);

        // ② 反射写入：两个面互不影响
        try {
            SignalBoundary boundary = new SignalBoundary();
            boundary.setId(java.util.UUID.randomUUID());

            boolean ok1 = SignalApplier.writeState(boundary, false, SignalState.RED);
            boolean ok2 = SignalApplier.writeState(boundary, true, SignalState.GREEN);

            failures += check("第一面写入成功", ok1);
            failures += check("第二面写入成功", ok2);
            failures += check("第一面读到 RED",
                SignalApplier.readState(boundary, false) == SignalState.RED,
                String.valueOf(SignalApplier.readState(boundary, false)));
            failures += check("第二面读到 GREEN",
                SignalApplier.readState(boundary, true) == SignalState.GREEN,
                String.valueOf(SignalApplier.readState(boundary, true)));

            // ③ 覆盖写
            SignalApplier.writeState(boundary, false, SignalState.YELLOW);
            failures += check("同面二次写入覆盖",
                SignalApplier.readState(boundary, false) == SignalState.YELLOW,
                String.valueOf(SignalApplier.readState(boundary, false)));
        } catch (Throwable t) {
            System.out.println("  FAIL 反射路径异常: " + t);
            failures++;
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("信号写入验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("信号写入验收通过：七种语义映射正确，反射写入可用且两面独立");
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
