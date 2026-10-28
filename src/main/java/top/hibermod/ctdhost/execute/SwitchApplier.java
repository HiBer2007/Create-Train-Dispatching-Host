package top.hibermod.ctdhost.execute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

import com.simibubi.create.content.trains.graph.TrackGraph;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.Rail;

/**
 * 道岔：逻辑锁 + （装了汽鸣铁道时的）物理扳动。
 *
 * <h2>当前实现到哪一步</h2>
 * <b>逻辑锁完整可用</b>：记录「某岔口某股道被锁定」，内核通过 {@link #lockOf} 查询，
 * 宿主据此拒绝冲突的进路。这部分不依赖任何外部模组，行为确定。
 *
 * <p><b>物理扳动尚未接通</b>：要真正驱动汽鸣铁道的道岔，需要
 * (1) 用反射从 {@code EdgePointType.TYPES} 取出 {@code railways:switch} 这个类型，
 * (2) 在 {@code TrackGraph#getPoints} 的结果里按节点位置匹配到具体的那一个，
 * (3) 调 {@code trySetSwitchState}。
 *
 * <p>这三步都能写，但**无法在无头环境验证**：位置匹配规则、道岔的
 * 「哪条边是直股」映射只有进了游戏摆一个岔才知道对不对。凭猜写出来的代码
 * 要么静默不生效、要么扳错方向——后者比不做更糟。所以这里明确留空并报出
 * 「未接通」，而不是给一个看起来能跑的假实现。
 *
 * <p>装没装汽鸣铁道会被探测并记录，便于排障时先排除这一项。
 */
public final class SwitchApplier {

    private static final String SWITCH_CLASS =
        "com.railwayteam.railways.content.switches.TrackSwitch";

    private static final Logger LOG = LoggerFactory.getLogger("ctdhost");

    private static Boolean railwaysPresent;

    /** 逻辑锁：岔口 → 锁定的股道。 */
    private final Map<Rail.NodeId, Rail.EdgeId> logicalLocks = new HashMap<>();

    /** 汽鸣铁道是否可用（只用于日志与命令输出）。 */
    public static boolean isRailwaysPresent() {
        if (railwaysPresent == null) {
            try {
                Class.forName(SWITCH_CLASS);
                railwaysPresent = Boolean.TRUE;
            } catch (Throwable t) {
                railwaysPresent = Boolean.FALSE;
            }
        }
        return Boolean.TRUE.equals(railwaysPresent);
    }

    /**
     * 应用一批道岔指令。
     *
     * @return 生效条数（逻辑锁全部计入）
     */
    public int apply(List<Command.SwitchCommand> commands, Map<UUID, TrackGraph> graphs) {
        int applied = 0;
        for (Command.SwitchCommand c : commands) {
            if (c.branch() == null) {
                logicalLocks.remove(c.node());
            } else {
                logicalLocks.put(c.node(), c.branch());
            }
            applied++;
        }
        return applied;
    }

    /** 内核查询：这个岔口当前被锁到哪条股道。 */
    public Optional<Rail.EdgeId> lockOf(Rail.NodeId node) {
        return Optional.ofNullable(logicalLocks.get(node));
    }

    public Map<Rail.NodeId, Rail.EdgeId> locks() {
        return Map.copyOf(logicalLocks);
    }

    /** 闸口是否与某条边冲突（进路检查用）。 */
    public boolean blocks(Rail.NodeId node, Rail.EdgeId branch) {
        Rail.EdgeId locked = logicalLocks.get(node);
        return locked != null && !locked.equals(branch);
    }

    /**
     * 诊断信息：告诉使用者物理扳动到底通没通。
     *
     * <p>宁可在命令输出里写「未接通」，也不要让人以为扳了。
     */
    public String status() {
        return "logical locks=" + logicalLocks.size()
            + ", railways=" + (isRailwaysPresent() ? "present (physical switching NOT wired yet)"
                                                   : "absent (logical only)");
    }

    // ── 读取汽鸣铁道的股道开通状态 ────────────────────────────────────────
    //
    // S&R 把「股道被扳开」记在 EdgeData 上（mixin 实现 ISwitchDisabledEdge）。
    // EdgeData 本身没有 enabled 字段，所以必须按接口名反射探测。
    // 拿不到就返回 true —— 「不知道」不能等于「禁用」，否则没装 S&R 的线网会全线堵塞。

    private static Boolean switchEdgeInterfaceAvailable;
    private static java.lang.reflect.Method isEnabledMethod;

    private static synchronized void resolveSwitchEdgeInterface() {
        if (switchEdgeInterfaceAvailable != null) return;
        try {
            Class<?> iface = Class.forName("com.railwayteam.railways.mixin_interfaces.ISwitchDisabledEdge");
            isEnabledMethod = iface.getMethod("isEnabled");
            switchEdgeInterfaceAvailable = Boolean.TRUE;
        } catch (Throwable t) {
            switchEdgeInterfaceAvailable = Boolean.FALSE;
        }
    }

    /**
     * 这条边当前是否可通行。
     *
     * <p>没装 S&R、或对象没实现该接口时返回 {@code true}：
     * 失败要朝「放行」方向兜底，因为把整条线网当成禁用会让列车全部停摆。
     */
    public static boolean isEdgeEnabled(com.simibubi.create.content.trains.graph.TrackEdge edge) {
        if (edge == null) return false;
        resolveSwitchEdgeInterface();
        if (!Boolean.TRUE.equals(switchEdgeInterfaceAvailable)) return true;
        try {
            Object data = edge.getEdgeData();
            if (data == null || !isEnabledMethod.getDeclaringClass().isInstance(data)) return true;
            Object v = isEnabledMethod.invoke(data);
            return !(v instanceof Boolean b) || b;
        } catch (Throwable t) {
            return true;
        }
    }

    // 占位：物理扳动的入口。等进游戏验证过位置匹配规则再填。
    @SuppressWarnings("unused")
    private static Object switchTypeOrNull() {
        try {
            Class<?> ept = Class.forName("com.simibubi.create.content.trains.graph.EdgePointType");
            Field types = ept.getField("TYPES");
            Object raw = types.get(null);
            if (raw instanceof Map<?, ?> map) {
                return map.get(ResourceLocation.fromNamespaceAndPath("railways", "switch"));
            }
        } catch (Throwable ignored) {
            // 没装或结构变了：退回逻辑锁
        }
        return null;
    }
}
