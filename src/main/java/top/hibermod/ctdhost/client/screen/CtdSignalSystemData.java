package top.hibermod.ctdhost.client.screen;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端可见的信号系统 id 列表。
 *
 * 信号系统的注册表在服务端，客户端拿不到。宿主把列表写进显示器的方块实体
 * （随 BE 同步），客户端读到后缓存在这里，设置界面的下拉框从这份缓存取值。
 */
public final class CtdSignalSystemData {

    private CtdSignalSystemData() {}

    private static final List<String> SYSTEMS = new ArrayList<>();

    public static void update(String commaSeparated) {
        SYSTEMS.clear();
        if (commaSeparated != null && !commaSeparated.isEmpty()) {
            for (String s : commaSeparated.split(",")) {
                if (!s.isBlank()) SYSTEMS.add(s.trim());
            }
        }
    }

    public static List<String> all() {
        return List.copyOf(SYSTEMS);
    }
}