package top.hibermod.ctdhost.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 设置界面的「信号系统」候选列表。
 *
 * <p>两个来源合并：
 * <ol>
 *   <li>宿主随显示器方块实体同步过来的 id 串（内核运行时注册的系统）</li>
 *   <li>本进程里 {@code SignalRegistries} 已注册的系统（客户端内置的那部分）</li>
 * </ol>
 *
 * <p>两者都空时退到 {@code DEFAULT_SYSTEM_ID}，保证下拉框永远不是空的。
 */
public final class CtdSignalSystemData {

    private CtdSignalSystemData() {}

    private static final List<String> SYNCED = new ArrayList<>();

    /** 客户端收到方块实体同步时调用。 */
    public static void update(String commaSeparated) {
        SYNCED.clear();
        if (commaSeparated == null || commaSeparated.isEmpty()) return;
        for (String s : commaSeparated.split(",")) {
            if (!s.isBlank()) SYNCED.add(s.trim());
        }
    }

    public static List<String> all() {
        Set<String> out = new LinkedHashSet<>(SYNCED);

        try {
            for (var sys : top.hibermod.ctdapi.signal.SignalRegistries.systems()) {
                if (sys != null && sys.id() != null) out.add(sys.id().toString());
            }
        } catch (Throwable ignored) {
            // 注册表在客户端可能尚未初始化，忽略
        }

        if (out.isEmpty()) {
            try {
                out.add(top.hibermod.ctdapi.signal.SignalRegistries.DEFAULT_SYSTEM_ID.toString());
            } catch (Throwable ignored) {
                // 实在拿不到就不加，由调用方兜底
            }
        }
        return List.copyOf(out);
    }
}