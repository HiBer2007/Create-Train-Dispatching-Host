package top.hibermod.ctdhost.execute;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.Topology;
import top.hibermod.ctdapi.signal.Aspect;
import top.hibermod.ctdapi.signal.SignalRegistries;

/**
 * 把 {@link Command.AspectCommand} 写进 {@code SignalBoundary.cachedStates}。
 *
 * <h2>为什么只写 cachedStates</h2>
 * Create 的行车判定**不读** {@code cachedStates}——它实时读
 * {@code SignalEdgeGroup.isOccupiedUnless(...)}（见 `AGENTS.md` §3）。
 * 所以这一步只改变**显示**。真正的放行/拦截要另找地方（授权与
 * {@code frontSignalListener}），那是 C5 与后续的事。把这点写清楚，
 * 免得以后有人以为设了绿灯车就会动。
 *
 * <h2>语义 → 灯色</h2>
 * API 说的是**语义**（停车/注意/通行），Create 说的是**灯色**。中间的映射是宿主的事：
 * 同一句「通行」在三显示机上是绿灯、在调车机上是白灯，但都是 {@code GREEN}。
 */
public final class SignalApplier {

    /** 语义分类 → Create 的灯色。 */
    public static SignalState toCreateState(Aspect.Category category) {
        return switch (category) {
            case STOP, DARK -> SignalState.RED;
            case CAUTION, ADVANCE_CAUTION -> SignalState.YELLOW;
            case PROCEED, SHUNT, CALL_ON -> SignalState.GREEN;
        };
    }

    /**
     * 应用一批信号指令。
     *
     * @param graphs 当前所有轨道图
     * @return 成功写入的数量
     */
    public int apply(List<Command.AspectCommand> commands, Map<UUID, TrackGraph> graphs,
                     long gameTime) {
        int applied = 0;
        for (Command.AspectCommand c : commands) {
            Aspect aspect = SignalRegistries.aspect(c.aspectId()).orElse(null);
            if (aspect == null) continue;               // 没注册的语义：静默丢弃

            SignalBoundary boundary = findBoundary(c.signalId(), graphs);
            if (boundary == null) continue;             // 信号机已被拆

            SignalState state = toCreateState(aspect.category());
            boolean side = sideIndex(c.side());

            // ① 记下意图：Create 每帧会重算 cachedStates，光写字段只能活一帧。
            SignalOverrides.set(boundary.getId(), side, state, gameTime);

            // ② 当帧也写一次：能让本帧的显示立刻变，不用等下一个 tick。
            if (writeState(boundary, side, state)) applied++;
        }
        return applied;
    }

    // ── 反射写 cachedStates ────────────────────────────────────────────────
    //
    // 为什么不直接 boundary.cachedStates.set(...)：那个字段的类型是
    // net.createmod.catnip.Couple，而它在 Catnip 的不同版本里待过 utility 与 data
    // 两个包，编译期硬绑会让整个宿主绑死在某个 Catnip 小版本上。
    // 反射只依赖「字段名 + set(boolean, T) 方法」这两个稳定得多的事实。

    private static final java.lang.reflect.Field CACHED_STATES;
    private static final java.lang.reflect.Method COUPLE_SET;
    private static boolean reflectionBroken;

    static {
        java.lang.reflect.Field f = null;
        java.lang.reflect.Method m = null;
        try {
            f = SignalBoundary.class.getDeclaredField("cachedStates");
            f.setAccessible(true);
            m = f.getType().getMethod("set", boolean.class, Object.class);
        } catch (Throwable t) {
            reflectionBroken = true;
            System.err.println("[ctdhost] cannot reach SignalBoundary.cachedStates: " + t
                + "; signal aspects will not be applied");
        }
        CACHED_STATES = f;
        COUPLE_SET = m;
    }

    /**
     * 把灯色写进某个边点的某一面。
     *
     * <p>公开是为了能离线验证：不挂到图上的 {@code SignalBoundary} 实例是安全的，
     * 而挂上去之后每帧都会走 {@code tickState}，没配好组的边界会拖累整个 tick。
     */
    public static boolean writeState(SignalBoundary boundary, boolean side, SignalState state) {
        if (reflectionBroken || CACHED_STATES == null || COUPLE_SET == null) return false;
        try {
            Object couple = CACHED_STATES.get(boundary);
            if (couple == null) return false;
            COUPLE_SET.invoke(couple, side, state);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 读回某个边点的灯色。
     *
     * <p>同样走反射：{@code cachedStates} 是 Catnip 的 {@code Couple}，
     * 而 Catnip 不在本模组的编译路径上。把这类访问全部收在这一个类里，
     * 其它地方就不用知道 Catnip 的存在。
     */
    public static SignalState readState(SignalBoundary boundary, boolean side) {
        if (reflectionBroken || CACHED_STATES == null) return SignalState.INVALID;
        try {
            Object couple = CACHED_STATES.get(boundary);
            if (couple == null) return SignalState.INVALID;
            java.lang.reflect.Method get = couple.getClass().getMethod(
                firstSlot(side) ? "getFirst" : "getSecond");
            Object v = get.invoke(couple);
            return v instanceof SignalState s ? s : SignalState.INVALID;
        } catch (Throwable t) {
            return SignalState.INVALID;
        }
    }

    /**
     * 语义面 → {@code Couple} 的槽位。
     *
     * <p><b>实测结论</b>：{@code Couple.set(boolean, T)} 的 {@code true} 是**第一面**。
     * （这个结论来自 {@code SignalApplierTest}：一开始按「false = first」写，
     * 结果写第一面改到了第二面，测试直接把方向翻了出来。）
     */
    private static boolean sideIndex(Topology.SignalSide side) {
        return side == Topology.SignalSide.FIRST;
    }

    private static boolean firstSlot(boolean sideIndex) {
        return sideIndex;
    }

    private static SignalBoundary findBoundary(UUID id, Map<UUID, TrackGraph> graphs) {
        for (TrackGraph graph : graphs.values()) {
            SignalBoundary b = graph.getPoint(EdgePointType.SIGNAL, id);
            if (b != null) return b;
        }
        return null;
    }
}
