package top.hibermod.ctdhost;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一的「不该静默」失败出口。
 *
 * <h2>为什么不直接 {@code catch (Throwable ignored) {}}</h2>
 * 本项目已经吃过一次教训：`/ctd reload` 一直失败并自动回滚，日志只有一行 WARN，
 * 于是「重载从不生效」这件事藏了好几轮。同一种病在宿主侧还有若干处——
 * mixin 的注入体、拓扑重建、道岔物理扳动，全是 `catch (Throwable ignored)`。
 *
 * <p>它们的共同后果是：**功能悄悄失效，而系统看起来一切正常**。
 * 但这些地方又确实不能直接抛——一帧里抛出去会打崩整个列车 tick。
 *
 * <p>折中办法是「失败可见但不刷屏」：
 *
 * <ul>
 *   <li>某个点第一次失败 → <b>ERROR</b>，带完整异常；</li>
 *   <li>之后每 100 次失败 → 一条 WARN，附累计次数；</li>
 *   <li>恢复后不主动报，但计数保留，{@code /ctd host status} 能读到。</li>
 * </ul>
 *
 * <p>这样第一次发生就一定看得见，长期失效也不会被日志淹没。
 */
public final class Failures {

    private static final Logger LOG = LoggerFactory.getLogger("ctdhost");

    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();

    private Failures() {}

    /**
     * 记录一次被兜住的失败。
     *
     * @param where 位置标识（例如 {@code "Train#tick 注入"}），用作计数键与日志前缀
     * @param t     原始异常；可以是 {@code null}（只计数）
     */
    public static void caught(String where, Throwable t) {
        AtomicLong counter = COUNTS.computeIfAbsent(where, k -> new AtomicLong());
        long n = counter.incrementAndGet();

        if (n == 1) {
            // 第一次一定报全：这是「功能已经坏了」的最早信号
            LOG.error("[ctdhost] {} failed (first time, will keep counting): {}",
                where, describe(t), t);
        } else if (n % 100 == 0) {
            LOG.warn("[ctdhost] {} has failed {} times; still continuing", where, n);
        }
    }

    /** 某个点累计失败次数。 */
    public static long count(String where) {
        AtomicLong c = COUNTS.get(where);
        return c == null ? 0L : c.get();
    }

    /** 所有出过问题的点，供诊断输出。 */
    public static Map<String, Long> snapshot() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        COUNTS.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }

    /** 自启动以来是否出过任何被兜住的失败。 */
    public static boolean any() {
        return !COUNTS.isEmpty();
    }

    public static void resetForTests() {
        COUNTS.clear();
    }

    private static String describe(Throwable t) {
        if (t == null) return "(no exception)";
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }
}
