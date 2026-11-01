package top.hibermod.ctdhost.execute;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;

/**
 * 「哪些岔口装了自动道岔机」的登记表。
 *
 * <h2>为什么需要一张表，而不是每次扫世界</h2>
 * 调度每帧都可能查「这个岔口能不能自动扳」。扫世界是 O(区块) 的，
 * 而这张表是 O(1)。机器自己在 {@code onLoad}/{@code setRemoved} 里登记与注销，
 * 所以表的内容总是「当前加载着的机器」。
 *
 * <h2>没有机器时会发生什么</h2>
 * 岔口按**固定方向**走：有直行就直行，否则始终左转（见 {@link FixedRoute}）。
 * 这不是「不工作」，而是一个明确的默认——列车不会因为没人调度而卡死。
 */
public final class SwitchMachines {

    private SwitchMachines() {}

    /** 岔口 id（编码串）→ 机器位置。 */
    private static final Map<String, BlockPos> MACHINES = new ConcurrentHashMap<>();

    public static void register(String nodeId, BlockPos pos) {
        if (nodeId == null || nodeId.isEmpty() || pos == null) return;
        MACHINES.put(nodeId, pos.immutable());
    }

    public static void unregister(String nodeId, BlockPos pos) {
        if (nodeId == null || nodeId.isEmpty()) return;
        MACHINES.remove(nodeId, pos);
    }

    /** 这个岔口有没有装机器。 */
    public static boolean hasMachine(String nodeId) {
        return nodeId != null && MACHINES.containsKey(nodeId);
    }

    public static boolean hasMachine(top.hibermod.ctdapi.Rail.NodeId node) {
        return node != null && hasMachine(node.encode());
    }

    public static Map<String, BlockPos> snapshot() {
        return Map.copyOf(MACHINES);
    }

    public static int count() {
        return MACHINES.size();
    }

    public static void clear() {
        MACHINES.clear();
    }
}
