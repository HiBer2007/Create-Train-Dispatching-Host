package top.hibermod.ctdhost.operation;

import java.util.Optional;
import java.util.regex.Pattern;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.resources.ResourceLocation;

/**
 * 交路的停站 id → Create 的**车站名**。
 *
 * <h2>为什么必须有这一层</h2>
 * {@code DestinationInstruction.start} 是这么找站的：
 *
 * <pre>
 * for (GlobalStation s : train.graph.getPoints(EdgePointType.STATION))
 *     if (!s.name.matches(regex)) continue;
 * </pre>
 *
 * 也就是说：**指令里写的是正则，匹配的是车站名。**
 * 直接把 {@code ctdapi:alpha} 这种 id 写进去，它一个站也匹配不到，
 * 列车会反复 {@code failedNavigationNoTarget} + 冷却——比不写时刻表更糟。
 *
 * <h2>匹配规则</h2>
 * 停站 id 的 path 段（冒号后面）与车站名做**忽略大小写的精确比较**；
 * 也接受 id 全串匹配。匹配上之后用 {@link Pattern#quote} 包起来再写进去，
 * 避免车站名里带正则元字符（括号、加号）把语义搞歪。
 *
 * <h2>匹配不到时</h2>
 * 返回空并计数，调用方应当**跳过该站**而不是退化成写 id。
 */
public final class StationResolver {

    private StationResolver() {}

    /**
     * @param stop  交路里的停站 id
     * @return 可以直接写进 {@code Text} 的正则；匹配不到返回空
     */
    public static Optional<String> toInstructionText(ResourceLocation stop) {
        if (stop == null) return Optional.empty();

        String path = stop.getPath();
        String full = stop.toString();

        for (var graph : Create.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (station == null || station.name == null) continue;
                if (station.name.equalsIgnoreCase(path) || station.name.equalsIgnoreCase(full)) {
                    // 车站名可能含正则元字符，必须转义后再当匹配式用
                    return Optional.of(Pattern.quote(station.name));
                }
            }
        }
        return Optional.empty();
    }

    /** 世界里是否已经有车站（用于给使用者一个明确的提示）。 */
    public static int stationCount() {
        int n = 0;
        for (var graph : Create.RAILWAYS.trackNetworks.values()) {
            n += graph.getPoints(EdgePointType.STATION).size();
        }
        return n;
    }
}
