package top.hibermod.ctdhost.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.timetable.Service;

/**
 * 把指派**真正落到 Create 的时刻表上**（G4 的应用层）。
 *
 * <h2>写什么</h2>
 * {@code ScheduleRuntime.schedule} 是公开字段，所以落地就是「构造一个 Schedule 赋进去」。
 * 每条停站生成一个 {@link ScheduleEntry}，指令是 {@link DestinationInstruction}，
 * 站名写进它的 {@code data}（{@code Text} 键）。
 *
 * <h2>落不到的时候</h2>
 * <ul>
 *   <li>交路不存在 → 跳过并计数；</li>
 *   <li>停站序列为空 → 跳过。**空时刻表等于把车停在原地**，比不设置更糟；</li>
 *   <li>车已有时刻表 → 覆盖。改派是正常行为，不是异常。</li>
 * </ul>
 *
 * <h2>为什么只写 schedule 不碰别的</h2>
 * {@code currentEntry} / {@code state} 由 Create 的 {@code ScheduleRuntime.tick} 自己推进。
 * 宿主手动设 {@code state = ACTIVE} 会和它的状态机打架——
 * 观察到的表现会是「车不动，但时刻表显示在跑」。
 * 只交数据，把推进交给原版，是这里唯一稳妥的做法。
 */
public final class AssignmentApplier {

    private int applied;
    private int skippedNoService;
    private int skippedNoStops;
    private int lastEntries;

    /**
     * 应用一轮。
     *
     * @param resolver 已解析的指派
     * @param trains   当前世界的列车
     * @return 真正写了时刻表的列车数
     */
    public int apply(AssignmentResolver resolver, java.util.Map<UUID, Train> trains) {
        applied = 0;
        skippedNoService = 0;
        skippedNoStops = 0;
        lastEntries = 0;

        for (AssignmentResolver.Resolved r : resolver.all()) {
            if (r.trains().isEmpty()) continue;

            Service service = Service.service(r.assignment().run().serviceId()).orElse(null);
            if (service == null) {
                skippedNoService++;
                continue;
            }
            List<ResourceLocation> stops = service.stops();
            if (stops.isEmpty()) {
                skippedNoStops++;
                continue;
            }

            Schedule schedule = buildSchedule(stops);
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

    /** 停站序列 → Create 的 Schedule。 */
    static Schedule buildSchedule(List<ResourceLocation> stops) {
        List<ScheduleEntry> entries = new ArrayList<>();
        for (ResourceLocation stop : stops) {
            DestinationInstruction instruction = new DestinationInstruction();
            instruction.getData().putString("Text", stop.toString());
            // 每条一个空的等待条件组：Create 用 List<List<...>> 表达 OR/AND，
            // 空列表意味着「到站即继续」，这是最短的可工作配置。
            entries.add(new ScheduleEntry(instruction, new ArrayList<>()));
        }
        return new Schedule(entries, false, 0);
    }

    public int applied() {
        return applied;
    }

    public int skippedNoService() {
        return skippedNoService;
    }

    public int skippedNoStops() {
        return skippedNoStops;
    }

    public int lastEntries() {
        return lastEntries;
    }

    public String summary() {
        return "appliedTrains=" + applied + " entries=" + lastEntries
            + " noService=" + skippedNoService + " noStops=" + skippedNoStops;
    }

    /** 某列车当前时刻表的条目数，0 表示没有时刻表。 */
    public static int entriesOf(Train train) {
        if (train == null || train.runtime == null) return 0;
        Schedule s = train.runtime.schedule;
        return s == null ? 0 : s.entries.size();
    }

    @SuppressWarnings("unused")
    private static void touch(ScheduleRuntime.State state) {
        // 保留引用：说明本类刻意不写 state
    }
}
