package top.hibermod.ctdhost.operation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.content.trains.entity.Train;

import top.hibermod.ctdapi.operation.Assignment;
import top.hibermod.ctdapi.operation.Assignments;
import top.hibermod.ctdapi.operation.UnitNumber;

/**
 * 把 {@link Assignment} 解析成**具体的列车**。
 *
 * <h2>这一层解决什么</h2>
 * 指派说的是「这些车辆编号跑那个车次」，而世界里的东西是 {@code Train}。
 * 中间隔着一个 {@link UnitIndex}。这一步全是纯数据映射，不碰 Create 的行为，
 * 所以可以离线/无头验证。
 *
 * <h2>为什么先只做解析</h2>
 * 「解析出来之后怎么让列车真的按交路跑」要改 {@code ScheduleRuntime}——
 * 那需要观察真实的 {@code Schedule}/{@code GlobalStation} 行为才能写对。
 * 没验过就写，多半会写出一个看起来能跑、实际不调度的东西。
 * 解析层先落地，应用层等游戏内确认后再接，这样每一步都可以单独验。
 *
 * <h2>解析不了的时候</h2>
 * 记一条 warn 并计数，**不抛异常也不改动任何东西**。
 * 指派指向一辆不存在的车是正常现象（车被拆了、编号改了），不该让调度停摆。
 */
public final class AssignmentResolver {

    /** 一次解析的结果。 */
    public record Resolved(Assignment assignment, List<UUID> trains, List<UnitNumber> missing) {
        public boolean complete() {
            return missing.isEmpty() && !trains.isEmpty();
        }
    }

    private final List<Resolved> resolved = new ArrayList<>();
    private long unresolvedUnits;

    /**
     * 重新解析全部指派。
     *
     * @param trains 当前世界的列车（用于确认 uuid 仍存在）
     */
    public void refresh(java.util.Map<UUID, Train> trains, UnitIndex units) {
        resolved.clear();
        unresolvedUnits = 0;

        for (Assignment a : Assignments.all()) {
            List<UUID> found = new ArrayList<>();
            List<UnitNumber> missing = new ArrayList<>();

            for (UnitNumber unit : a.allUnits()) {
                Optional<UUID> id = units.trainOf(unit);
                if (id.isPresent() && trains.containsKey(id.get())) {
                    if (!found.contains(id.get())) found.add(id.get());
                } else {
                    missing.add(unit);
                    unresolvedUnits++;
                }
            }
            resolved.add(new Resolved(a, found, missing));
        }
    }

    public List<Resolved> all() {
        return List.copyOf(resolved);
    }

    /** 某列车参与哪些指派。 */
    public List<Assignment> assignmentsOf(UUID trainId) {
        List<Assignment> out = new ArrayList<>();
        for (Resolved r : resolved) {
            if (r.trains().contains(trainId)) out.add(r.assignment());
        }
        return out;
    }

    /** 有列车参与、但没有完整解析的指派数量。 */
    public int incompleteCount() {
        int n = 0;
        for (Resolved r : resolved) {
            if (!r.complete()) n++;
        }
        return n;
    }

    public long unresolvedUnits() {
        return unresolvedUnits;
    }

    /** 参与任何指派的不同列车数。 */
    public int participatingTrains() {
        Set<UUID> s = new HashSet<>();
        for (Resolved r : resolved) s.addAll(r.trains());
        return s.size();
    }

    public void clear() {
        resolved.clear();
        unresolvedUnits = 0;
    }

    /** 一行摘要，给命令输出用。 */
    public String summary() {
        return "assignments=" + resolved.size()
            + " trains=" + participatingTrains()
            + " unresolvedUnits=" + unresolvedUnits
            + " incomplete=" + incompleteCount();
    }
}
