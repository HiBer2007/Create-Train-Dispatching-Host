package top.hibermod.ctdhost.execute;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;

/**
 * 「我们想让这条边界显示什么」——按边界的每个面记录，带过期时间。
 *
 * <h2>为什么需要它</h2>
 * {@code SignalBoundary#tickState} **每个 tick 都会重算 {@code cachedStates}**
 * （占用 / 预定 / 红石强制红 → RED，否则走链式解析）。
 * 所以直接写 {@code cachedStates} 只在当帧有效，下一帧就被 Create 抹掉——
 * 表现是「写了灯色，但灯从来不按我们写的变」。
 *
 * <p>这个类保存我方意图，由 {@code SignalBoundaryTickStateMixin} 在
 * {@code tickState} 的 HEAD 处接管：**两面都有新鲜意图时才取消重算**，
 * 否则完全放行原版。宁可少接管，也不要制造「一半我方、一半原版」的灯。
 *
 * <h2>过期时间为什么必须存在</h2>
 * 内核可能崩、可能被禁用、可能改了逻辑不再写这条边界。如果没有过期，
 * 最后写下的那个灯色会**永久生效**——红灯永远红、绿灯永远绿，
 * 那比不接管危险得多。{@link #TTL_TICKS} 之内没再写就自动交还原版。
 */
public final class SignalOverrides {

    /** 写入意图的有效期（tick）。内核每帧写，20 给足抖动余量。 */
    // 60 而不是 20：探针/内核可能每 40 帧才写一次（或逻辑上按间隔写），
    // TTL 比写入间隔短的话，意图会在两次写之间过期，表现成「写入时好时坏」。
    // 代价是内核停写后灯最多多保持 3 秒——比「时好时坏」可接受得多。
    public static final int TTL_TICKS = 60;

    /** 一条边界两面的意图。{@code null} 表示该面没有意图。 */
    public record Entry(SignalState first, SignalState second, long gameTime) {

        public boolean fresh(long now) {
            return now - gameTime <= TTL_TICKS;
        }

        /** 两面都有意图才允许接管。 */
        public boolean complete() {
            return first != null && second != null;
        }
    }

    private static final Map<UUID, Entry> OVERRIDES = new ConcurrentHashMap<>();

    private SignalOverrides() {}

    /** 记录某条边界某一面的意图（{@code side=true} 表示 FIRST/primary）。 */
    public static void set(UUID boundaryId, boolean side, SignalState state, long gameTime) {
        if (boundaryId == null || state == null) return;

        OVERRIDES.compute(boundaryId, (id, old) -> {
            SignalState first = old == null ? null : old.first();
            SignalState second = old == null ? null : old.second();

            if (side) {
                first = state;
            } else {
                second = state;
            }

            return new Entry(first, second, gameTime);
        });
    }

    /** 取一条边界当前的意图（可能已过期，调用方用 {@link Entry#fresh(long)} 判断）。 */
    public static Entry get(UUID boundaryId) {
        return OVERRIDES.get(boundaryId);
    }

    /** 这条边界现在是否应当由我方接管（两面都有新鲜意图）。 */
    public static boolean shouldTakeOver(UUID boundaryId, long now) {
        Entry entry = OVERRIDES.get(boundaryId);
        return entry != null && entry.complete() && entry.fresh(now);
    }

    public static int size() {
        return OVERRIDES.size();
    }

    /** 清掉过期条目；每帧调一次就够，避免 Map 无限增长。 */
    public static void prune(long now) {
        OVERRIDES.entrySet().removeIf(e -> !e.getValue().fresh(now));
    }

    public static void clear() {
        OVERRIDES.clear();
    }
}
