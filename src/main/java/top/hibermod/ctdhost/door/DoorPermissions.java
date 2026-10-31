package top.hibermod.ctdhost.door;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;

/**
 * 「这个站台现在能不能开门」的判定结果。
 *
 * <h2>谁写、谁读</h2>
 * <ul>
 *   <li>{@link DoorService} 每若干刻写一次（自动判定）；</li>
 *   <li>内核可以用 {@code out.device(...)} 下发显式指令，覆盖自动判定；</li>
 *   <li>门的 mixin 与门控制器读它。</li>
 * </ul>
 *
 * <h2>为什么按「站台方块坐标」而不是车站 UUID</h2>
 * 因为读它的地方（{@code DoorControlBehaviour}、门控制器）手上只有方块坐标。
 * 用坐标做键，两边不需要额外的映射表；站台被拆掉时坐标失效，判定自然消失——
 * 这正是我们要的语义。
 */
public final class DoorPermissions {

    private DoorPermissions() {}

    /** 站台坐标 → 是否允许开门（自动判定）。 */
    private static final Map<BlockPos, Boolean> AUTO = new ConcurrentHashMap<>();

    /** 站台坐标 → 内核显式覆盖（优先于自动判定）。 */
    private static final Map<BlockPos, Boolean> EXPLICIT = new ConcurrentHashMap<>();

    /** 站台坐标 → 最近一次判定的游戏刻。 */
    private static final Map<BlockPos, Long> UPDATED = new ConcurrentHashMap<>();

    public static void setAuto(BlockPos station, boolean allowed, long gameTime) {
        AUTO.put(station.immutable(), allowed);
        UPDATED.put(station.immutable(), gameTime);
    }

    /** 存档句柄：由宿主在服务端启动时接上；为空表示不持久化（单机测试）。 */
    private static java.util.function.Consumer<java.util.Map<BlockPos, Boolean>> persister;

    public static void setPersister(java.util.function.Consumer<java.util.Map<BlockPos, Boolean>> fn) {
        persister = fn;
    }

    /** 存档是否已接上。没接上 = 重启后显式许可会丢。 */
    public static boolean hasPersister() {
        return persister != null;
    }

    private static void persist() {
        if (persister != null) {
            try { persister.accept(Map.copyOf(EXPLICIT)); }
            catch (Throwable ignored) { /* 存档失败不该让调度停摆 */ }
        }
    }

    public static void setExplicit(BlockPos station, Boolean allowed) {
        if (allowed == null) {
            EXPLICIT.remove(station.immutable());
            persist();
        } else {
            EXPLICIT.put(station.immutable(), allowed);
            persist();
        }
    }

    public static boolean hasExplicit(BlockPos station) {
        return EXPLICIT.containsKey(station.immutable());
    }

    /**
     * 该站台是否允许开门。
     *
     * @param fallback 没有判定时用的值（通常传 Create 自己的结论）
     */
    public static boolean allowed(BlockPos station, boolean fallback) {
        Boolean explicit = EXPLICIT.get(station.immutable());
        if (explicit != null) return explicit;

        Boolean auto = AUTO.get(station.immutable());
        return auto != null ? auto : fallback;
    }

    /** 该站台是否有过任何判定（用于日志与命令输出）。 */
    public static boolean known(BlockPos station) {
        return AUTO.containsKey(station.immutable()) || EXPLICIT.containsKey(station.immutable());
    }

    public static Long updatedAt(BlockPos station) {
        return UPDATED.get(station.immutable());
    }

    public static Map<BlockPos, Boolean> autoSnapshot() {
        return Map.copyOf(AUTO);
    }

    /** 从存档读回显式许可（服务端启动时调用）。 */
    public static void loadFrom(net.minecraft.nbt.CompoundTag tag) {
        EXPLICIT.clear();
        if (tag == null) return;

        for (String key : tag.getAllKeys()) {
            String[] parts = key.split(",");
            if (parts.length != 3) continue;
            try {
                BlockPos pos = new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
                EXPLICIT.put(pos, tag.getBoolean(key));
            } catch (NumberFormatException ignored) {
                // 单个坐标坏了不该让整张表读不出来
            }
        }
    }

    public static void clear() {
        // clear() 是「测试用」的：不写存档，免得把测试状态带进正式存档。
        AUTO.clear();
        EXPLICIT.clear();
        UPDATED.clear();
    }
}
