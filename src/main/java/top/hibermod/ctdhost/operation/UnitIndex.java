package top.hibermod.ctdhost.operation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.simibubi.create.content.trains.entity.Train;

import top.hibermod.ctdapi.operation.UnitNumber;

/**
 * 车辆编号 ↔ {@code Train} 的索引。
 *
 * <h2>绑定规则（G6）</h2>
 * 车辆编号**就是** {@code Train.name} 的字符串。理由是这条路不需要任何 UI：
 * 玩家在 Create 的列车编辑界面改名，就是在给车辆编号。宿主只做同步。
 *
 * <p>反过来（单独的映射表）需要自建界面，而且玩家看不到编号与车的对应关系，
 * 排障时会先怀疑调度，最后才发现编号绑错了。
 *
 * <h2>重名怎么办</h2>
 * **先到先得，后到的不可寻址**，并记一条 warn。
 *
 * <p>不给后来者自动加后缀：「CRH2A」(2) 这种编号在玩家眼里是另一辆车，
 * 自动改名会让人以为系统出了 bug。宁可明确地拒绝寻址，让玩家改名。
 * 两辆车仍然各自能通过 UUID 被调度（内核给的车辆编号会指向第一辆）。
 *
 * <h2>改名</h2>
 * 每帧 reconcile：{@code Train.name} 变了就更新索引。这样改名立即生效，
 * 不需要监听任何事件，也不怕漏掉某条改名路径（`TrainEditPacket` /
 * `StationPeripheral` / 装车时的 {@code lastDisassembledTrainName} 都能覆盖）。
 */
public final class UnitIndex {

    private final Map<UUID, UnitNumber> byTrain = new HashMap<>();
    private final Map<UnitNumber, UUID> byUnit = new HashMap<>();

    private int collisions;

    /** 与当前世界里的列车对账。每帧调用，代价是 O(车数)。 */
    public void reconcile(Iterable<Train> trains) {
        for (Train t : trains) {
            if (t == null || t.id == null) continue;

            String raw = t.name == null ? "" : t.name.getString();
            if (raw.isBlank()) {
                // 未命名的列车不参与编号寻址，但仍保留 UUID 侧记录
                UnitNumber old = byTrain.remove(t.id);
                if (old != null && t.id.equals(byUnit.get(old))) byUnit.remove(old);
                continue;
            }

            UnitNumber unit = new UnitNumber(raw);
            UnitNumber previous = byTrain.put(t.id, unit);

            if (previous != null && previous.equals(unit)) {
                continue;                                   // 没变
            }
            if (previous != null && t.id.equals(byUnit.get(previous))) {
                byUnit.remove(previous);                    // 改名：释放旧编号
            }

            UUID owner = byUnit.get(unit);
            if (owner == null || owner.equals(t.id)) {
                byUnit.put(unit, t.id);
            } else {
                collisions++;
                // 不覆盖：先到者持有该编号
            }
        }
    }

    public Optional<UnitNumber> unitOf(UUID trainId) {
        return Optional.ofNullable(byTrain.get(trainId));
    }

    public Optional<UUID> trainOf(UnitNumber unit) {
        return Optional.ofNullable(byUnit.get(unit));
    }

    /** 该编号是否已被占用（可能属于别的车）。 */
    public boolean isTaken(UnitNumber unit) {
        return byUnit.containsKey(unit);
    }

    public int size() {
        return byUnit.size();
    }

    /** 累计重名次数，用于自检与命令输出。 */
    public int collisions() {
        return collisions;
    }

    public List<UnitNumber> units() {
        List<UnitNumber> out = new ArrayList<>(byUnit.keySet());
        out.sort(null);
        return out;
    }

    public void clear() {
        byTrain.clear();
        byUnit.clear();
        collisions = 0;
    }
}
