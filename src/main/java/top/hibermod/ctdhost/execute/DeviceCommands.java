package top.hibermod.ctdhost.execute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

import top.hibermod.ctdapi.Command;

/**
 * 设备指令的落点：**记住每台设备最后一次收到的指令**。
 *
 * <h2>它补的是什么</h2>
 * 设备指令以前只走 {@code OperationFeed} 并计入 {@code forwarded: devices=N}——
 * 也就是说，脚本发「开站台门」，宿主数了一下，然后什么也没发生，
 * 而且事后无从查证到底发过什么。
 *
 * <p>这里把每条指令按设备 id 存下来，并给出查询：
 *
 * <ul>
 *   <li>调度内核可以问「3 号站台门现在是什么指令」；</li>
 *   <li>{@code /ctd host status} 能显示「最近收到指令的设备有几台」；</li>
 *   <li>指令内容进存档，重启后仍能解释「门为什么开着」。</li>
 * </ul>
 *
 * <h2>它不做什么</h2>
 * 不解释 payload——门、屏、计轴器的语义属于各自的设备实现。
 * 这一层只保证「指令确实被接收、可被查询、可被追溯」。
 */
public final class DeviceCommands {

    /** 一条已接受的指令。{@code gameTime} 是收到它的游戏刻。 */
    public record Received(UUID deviceId, CompositeTag payload, long gameTime, long holder) {}

    /** 只是为了在 record 里带一个可读类型；实际存的是 NBT。 */
    public static final class CompositeTag {
        private final CompoundTag tag;

        CompositeTag(CompoundTag tag) {
            this.tag = tag == null ? new CompoundTag() : tag;
        }

        public CompoundTag tag() {
            return tag;
        }

        public String summary() {
            if (tag.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{");
            for (String key : tag.getAllKeys()) {
                if (sb.length() > 1) sb.append(',');
                sb.append(key).append('=').append(tag.getString(key));
            }
            return sb.append('}').toString();
        }
    }

    private final Map<UUID, Received> last = new LinkedHashMap<>();
    private long accepted;
    private long rejected;

    /** 应用一批指令；返回实际接受的条数。 */
    public int apply(List<Command.DeviceCommand> commands, long gameTime) {
        if (commands == null || commands.isEmpty()) return 0;

        int count = 0;
        for (Command.DeviceCommand c : commands) {
            if (c == null || c.deviceId() == null) {
                rejected++;
                continue;
            }
            last.put(c.deviceId(), new Received(
                c.deviceId(), new CompositeTag(c.payload()), gameTime, c.holder()));
            accepted++;
            count++;
        }
        return count;
    }

    /** 某台设备最后一次收到的指令；没有返回 {@code null}。 */
    public Received lastOf(UUID deviceId) {
        return deviceId == null ? null : last.get(deviceId);
    }

    public int size() {
        return last.size();
    }

    public long accepted() {
        return accepted;
    }

    public long rejected() {
        return rejected;
    }

    /** 诊断用快照，按设备 id 排序，便于稳定输出。 */
    public List<Received> snapshot() {
        List<Received> out = new ArrayList<>(last.values());
        out.sort((a, b) -> a.deviceId().compareTo(b.deviceId()));
        return out;
    }

    public void clear() {
        last.clear();
    }
}
