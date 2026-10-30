package top.hibermod.ctdhost;

import java.util.Map;

/**
 * 宿主侧的失败登记 —— 转发到 API 层那一份。
 *
 * <p>登记表放在 {@code ctdapi.diagnostics.Failures}，因为**脚本运行时也要往同一张表里写**。
 * 两个模组各记各的，排查时要翻两个地方，等于没有统一视图。
 *
 * <p>这里保留同名类只是为了让宿主内的调用点读起来短一些，没有独立状态。
 */
public final class Failures {

    private Failures() {}

    /** 见 {@link top.hibermod.ctdapi.diagnostics.Failures#caught(String, Throwable)}。 */
    public static void caught(String where, Throwable t) {
        top.hibermod.ctdapi.diagnostics.Failures.caught(where, t);
    }

    public static long count(String where) {
        return top.hibermod.ctdapi.diagnostics.Failures.count(where);
    }

    public static Map<String, Long> snapshot() {
        return top.hibermod.ctdapi.diagnostics.Failures.snapshot();
    }

    public static boolean any() {
        return top.hibermod.ctdapi.diagnostics.Failures.any();
    }

    public static void resetForTests() {
        top.hibermod.ctdapi.diagnostics.Failures.resetForTests();
    }
}
