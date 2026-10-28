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

    // ── 宿主不执行、但必须**转交**的指令 ────────────────────────────────────
    //
    // 设备协议、牵引电量、运营服务、HUD、PIS 板面、事件流：它们的实际效果
    // 属于别的模组（后台调度、供电、PIS 渲染）。
    //
    // 早先这里是空实现。空实现的问题不是「没做」，而是**静默**：
    // 脚本作者看到调用成功、没有异常、没有日志，会以为指令生效了。
    // 现在统一走 OperationFeed 转交并计数，至少可观测。

    private long forwardedDevices;
    private long forwardedEnergies;
    private long forwardedServices;
    private long forwardedHud;
    private long forwardedBoards;
    private long forwardedEvents;

    @Override
    public void applyDevices(List<Command.DeviceCommand> commands) {
        for (Command.DeviceCommand c : commands) {
            top.hibermod.ctdapi.operation.OperationFeed.deviceCommand(c);
            forwardedDevices++;
        }
    }

    @Override
    public void applyEnergies(List<Command.EnergyCommand> commands) {
        for (Command.EnergyCommand c : commands) {
            top.hibermod.ctdapi.operation.OperationFeed.energyCommand(c);
            forwardedEnergies++;
        }
    }

    @Override
    public void applyServices(List<Command.ServiceCommand> commands) {
        for (Command.ServiceCommand c : commands) {
            top.hibermod.ctdapi.operation.OperationFeed.serviceCommand(c);
            forwardedServices++;
        }
    }

    @Override
    public void applyHud(List<Hud.HudPanel> panels) {
        for (Hud.HudPanel p : panels) {
            top.hibermod.ctdapi.operation.OperationFeed.hudPanel(p);
            forwardedHud++;
        }
    }

    @Override
    public void applyBoards(List<Pis.BoardModel> boards) {
        for (Pis.BoardModel b : boards) {
            top.hibermod.ctdapi.operation.OperationFeed.boardModel(b);
            forwardedBoards++;
        }
    }

    @Override
    public void applyEvents(List<RailwayEvent> events) {
        for (RailwayEvent e : events) {
            top.hibermod.ctdapi.operation.OperationFeed.railwayEvent(e);
            forwardedEvents++;
        }
    }

    /** 转交计数摘要，供 `/ctd host status` 显示。 */
    public String forwardedSummary() {
        return "devices=" + forwardedDevices
            + " energy=" + forwardedEnergies
            + " service=" + forwardedServices
            + " hud=" + forwardedHud
            + " boards=" + forwardedBoards
            + " events=" + forwardedEvents;
    }
}
