package top.hibermod.ctdhost.dispatch;

import java.util.ArrayList;
import java.util.List;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.DispatchFrame;
import top.hibermod.ctdapi.RailwayEvent;
import top.hibermod.ctdapi.hud.Hud;
import top.hibermod.ctdapi.pis.Pis;

/**
 * 收集内核写的指令，帧末统一交给 {@link CommandSink} 应用。
 *
 * <p><b>为什么要缓冲</b>：内核写指令的顺序不该影响结果。若边写边应用，
 * 「先给车 A 授权、再给车 B」和反过来可能得到不同的物理结果，
 * 内核就必须知道应用顺序——那是宿主的实现细节，不该泄漏到契约层。
 *
 * <p>同一列车同帧多次提交：以**最后一次**为准（与 {@link DispatchFrame#command} 的约定一致）。
 */
public final class DispatchFrameImpl implements DispatchFrame {

    private final List<Command.TrainCommand> trains = new ArrayList<>();
    private final List<Command.AspectCommand> aspects = new ArrayList<>();
    private final List<Command.SwitchCommand> switches = new ArrayList<>();
    private final List<Command.DeviceCommand> devices = new ArrayList<>();
    private final List<Command.EnergyCommand> energies = new ArrayList<>();
    private final List<Command.ServiceCommand> services = new ArrayList<>();
    private final List<Hud.HudPanel> huds = new ArrayList<>();
    private final List<Pis.BoardModel> boards = new ArrayList<>();
    private final List<RailwayEvent> events = new ArrayList<>();
    private final List<top.hibermod.ctdapi.operation.Assignment> assignments = new ArrayList<>();

    @Override
    public void assign(top.hibermod.ctdapi.operation.Assignment assignment) {
        if (assignment != null) assignments.add(assignment);
    }

    public List<top.hibermod.ctdapi.operation.Assignment> assignments() { return assignments; }

    @Override public void command(Command.TrainCommand command) { trains.add(command); }
    @Override public void aspect(Command.AspectCommand command) { aspects.add(command); }
    @Override public void switchLock(Command.SwitchCommand command) { switches.add(command); }
    @Override public void device(Command.DeviceCommand command) { devices.add(command); }
    @Override public void energy(Command.EnergyCommand command) { energies.add(command); }
    @Override public void service(Command.ServiceCommand command) { services.add(command); }
    @Override public void hud(Hud.HudPanel panel) { huds.add(panel); }
    @Override public void board(Pis.BoardModel board) { boards.add(board); }
    @Override public void event(RailwayEvent event) { events.add(event); }

    // ── 读取（宿主内部使用）────────────────────────────────────────────────

    public List<Command.TrainCommand> trainCommands() { return trains; }
    public List<Command.AspectCommand> aspectCommands() { return aspects; }
    public List<Command.SwitchCommand> switchCommands() { return switches; }
    public List<Command.DeviceCommand> deviceCommands() { return devices; }
    public List<Command.EnergyCommand> energyCommands() { return energies; }
    public List<Command.ServiceCommand> serviceCommands() { return services; }
    public List<Hud.HudPanel> huds() { return huds; }
    public List<Pis.BoardModel> boards() { return boards; }
    public List<RailwayEvent> events() { return events; }

    /** 本帧是否有任何指令。空帧等于「不干预」。 */
    public boolean isEmpty() {
        return trains.isEmpty() && aspects.isEmpty() && switches.isEmpty()
            && devices.isEmpty() && energies.isEmpty() && services.isEmpty()
            && huds.isEmpty() && boards.isEmpty() && events.isEmpty()
            && assignments.isEmpty();
    }

    /**
     * 把这批指令交给应用器。
     *
     * <p>应用器负责：取 min 钳位、校验 id、落回 Create。
     * 它抛异常时整帧作废——调用方（{@link DispatchPass}）必须捕获。
     */
    public void applyTo(CommandSink sink) {
        sink.applyTrains(trains);
        sink.applyAspects(aspects);
        sink.applySwitches(switches);
        sink.applyDevices(devices);
        sink.applyEnergies(energies);
        sink.applyServices(services);
        sink.applyHud(huds);
        sink.applyBoards(boards);
        sink.applyEvents(events);
        for (var assignment : assignments) {
            top.hibermod.ctdapi.operation.Assignments.publish(assignment);
        }
    }

    /** 指令落地接口。实现方是宿主，持有 Create 的类型。 */
    public interface CommandSink {
        void applyTrains(List<Command.TrainCommand> commands);
        void applyAspects(List<Command.AspectCommand> commands);
        void applySwitches(List<Command.SwitchCommand> commands);
        void applyDevices(List<Command.DeviceCommand> commands);
        void applyEnergies(List<Command.EnergyCommand> commands);
        void applyServices(List<Command.ServiceCommand> commands);
        void applyHud(List<Hud.HudPanel> panels);
        void applyBoards(List<Pis.BoardModel> boards);
        void applyEvents(List<RailwayEvent> events);
    }
}
