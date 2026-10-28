package top.hibermod.ctdhost.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.timetable.Service;

/**
 * 把指派**真正落到 Create 的时刻表上**（G4 的应用层）。
 *
 * <h2>写什么</h2>
 * {@code ScheduleRuntime.schedule} 是公开字段，落地就是「构造 Schedule 赋进去」。
 * 每条停站生成一个 {@link ScheduleEntry}，指令是 {@link DestinationInstruction}。
 *
 * <h2>站名必须能匹配上</h2>
 * 指令里的 {@code Text} 会被当成**正则**去匹配车站名
 * （{@code DestinationInstruction.start} 里 {@code s.name.matches(regex)}）。
 * 所以这里先过 {@link StationResolver}，匹配不到的车站**整条跳过**——
 * 写一个匹配不到任何站的正则，会让列车反复导航失败并进冷却，比不写更糟。
 *
 * <h2>没有一条能落地时</h2>
 * 不写时刻表，并计数。空时刻表会把车钉在原地。
 *
 * <h2>为什么只写 schedule 不碰别的</h2>
 * {@code currentEntry} / {@code state} 由 {@code ScheduleRuntime.tick} 自己推进。
 * 宿主手动设会和它的状态机打架，表现为「车不动但时刻表在跑」。
 */
public final class AssignmentApplier {

    private int applied;
    private int skippedNoService;
    private int skippedNoStops;
    private int skippedUnresolved;
    private int lastEntries;

    public int apply(AssignmentResolver resolver, java.util.Map<UUID, Train> trains) {
        applied = 0;
        skippedNoService = 0;
        skippedNoStops = 0;
        skippedUnresolved = 0;
        lastEntries = 0;

        for (AssignmentResolver.Resolved r : resolver.all()) {
            if (r.trains().isEmpty()) continue;

            Service service = Service.service(r.assignment().run().serviceId()).orElse(null);
            if (service == null) {
                skippedNoService++;
                continue;
            }
            if (service.stops().isEmpty()) {
                skippedNoStops++;
                continue;
            }

            Schedule schedule = buildSchedule(service.stops());
            if (schedule == null) {
                skippedUnresolved++;
                continue;
            }

            for (UUID id : r.trains()) {
                Train train = trains.get(id);
                if (train == null || train.runtime == null) continue;
                train.runtime.schedule = schedule;
                applied++;
                lastEntries = schedule.entries.size();
            }
        }
        return applied;
    }

    /**
     * 停站序列 → Create 的 Schedule。
     *
     * @return 至少有一条能匹配上时返回 Schedule；否则 {@code null}
     */
    static Schedule buildSchedule(List<ResourceLocation> stops) {
        List<ScheduleEntry> entries = new ArrayList<>();
        for (ResourceLocation stop : stops) {
            var text = StationResolver.toInstructionText(stop);
            if (text.isEmpty()) continue;                 // 匹配不到就不写这条

            DestinationInstruction instruction = new DestinationInstruction();
            instruction.getData().putString("Text", text.get());
            entries.add(new ScheduleEntry(instruction, new ArrayList<>()));
        }
        return entries.isEmpty() ? null : new Schedule(entries, false, 0);
    }

    public int applied() {
        return applied;
    }

    public int lastEntries() {
        return lastEntries;
    }

    public int skippedNoService() {
        return skippedNoService;
    }

    public int skippedNoStops() {
        return skippedNoStops;
    }

    public int skippedUnresolved() {
        return skippedUnresolved;
    }

    public String summary() {
        return "appliedTrains=" + applied + " entries=" + lastEntries
            + " noService=" + skippedNoService
            + " noStops=" + skippedNoStops
            + " unresolvedStops=" + skippedUnresolved
            + " worldStations=" + StationResolver.stationCount();
    }

    /** 某列车当前时刻表的条目数，0 表示没有时刻表。 */
    public static int entriesOf(Train train) {
        if (train == null || train.runtime == null) return 0;
        Schedule s = train.runtime.schedule;
        return s == null ? 0 : s.entries.size();
    }
}
