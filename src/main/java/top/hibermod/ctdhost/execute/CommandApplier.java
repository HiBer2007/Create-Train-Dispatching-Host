package top.hibermod.ctdhost.execute;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackGraph;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.RailwayEvent;
import top.hibermod.ctdapi.hud.Hud;
import top.hibermod.ctdapi.pis.Pis;
import top.hibermod.ctdhost.dispatch.DispatchFrameImpl;

/**
 * {@link DispatchFrameImpl.CommandSink} 的实现：把指令落到 Create。
 *
 * <p>每一条指令**独立处理**：一条失败不影响其它条。这一点很重要——
 * 内核可能在一帧里写几十条指令，其中一条引用了拆掉的信号机，
 * 不该让其余几十条一起陪葬。
 */
public final class CommandApplier implements DispatchFrameImpl.CommandSink {

    private final Supplier<java.util.Map<UUID, Train>> trains;
    private final Supplier<java.util.Map<UUID, TrackGraph>> graphs;
    private final SpeedApplier speed;
    private final SignalApplier signals = new SignalApplier();
    private final SwitchApplier switches = new SwitchApplier();

    private long gameTime;

    private int lastAppliedTrains;
    private int lastAppliedSignals;
    private int lastAppliedSwitches;

    public CommandApplier(Supplier<java.util.Map<UUID, Train>> trains,
                          Supplier<java.util.Map<UUID, TrackGraph>> graphs,
                          SpeedApplier speed) {
        this.trains = trains;
        this.graphs = graphs;
        this.speed = speed;
    }

    public void setGameTime(long t) { this.gameTime = t; }

    public int lastAppliedTrains() { return lastAppliedTrains; }
    public int lastAppliedSignals() { return lastAppliedSignals; }

    @Override
    public void applyTrains(List<Command.TrainCommand> commands) {
        lastAppliedTrains = speed.apply(commands, trains.get(), gameTime);
    }

    @Override
    public void applyAspects(List<Command.AspectCommand> commands) {
        lastAppliedSignals = signals.apply(commands, graphs.get());
    }

    // 以下尚未实现。留空而不是抛异常：内核多写一条指令不该让整帧崩掉。

    @Override
    public void applySwitches(List<Command.SwitchCommand> commands) {
        lastAppliedSwitches = switches.apply(commands, graphs.get());
    }

    public SwitchApplier switches() { return switches; }

    public int lastAppliedSwitches() { return lastAppliedSwitches; }

    @Override public void applyDevices(List<Command.DeviceCommand> commands) { }

    @Override public void applyEnergies(List<Command.EnergyCommand> commands) { }

    @Override public void applyServices(List<Command.ServiceCommand> commands) { }

    @Override public void applyHud(List<Hud.HudPanel> panels) { }

    @Override public void applyBoards(List<Pis.BoardModel> boards) { }

    @Override public void applyEvents(List<RailwayEvent> events) { }
}
