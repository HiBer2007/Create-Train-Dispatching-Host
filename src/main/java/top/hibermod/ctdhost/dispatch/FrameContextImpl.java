package top.hibermod.ctdhost.dispatch;

import java.util.Collection;
import java.util.List;

import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.RailwayEvent;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.Topology;

/**
 * {@link FrameContext} 的实现：一个持久的容器，每帧被重新填充。
 *
 * <p>每帧 new 一个也能用，但内核可能持有引用跨帧比较——复用同一个对象更安全，
 * 也少一点分配。填充由 {@link DispatchPass} 负责。
 */
public final class FrameContextImpl implements FrameContext {

    private long gameTime;
    private Topology topology;
    private Telemetry telemetry;
    private Collection<SignalState> signalStates = List.of();
    private Collection<SwitchState> switchStates = List.of();
    private Collection<RailwayEvent> pendingEvents = List.of();

    void fill(long gameTime, Topology topology, Telemetry telemetry,
              Collection<SignalState> signals, Collection<SwitchState> switches,
              Collection<RailwayEvent> events) {
        this.gameTime = gameTime;
        this.topology = topology;
        this.telemetry = telemetry;
        this.signalStates = signals == null ? List.of() : signals;
        this.switchStates = switches == null ? List.of() : switches;
        this.pendingEvents = events == null ? List.of() : events;
    }

    @Override public long gameTime() { return gameTime; }
    @Override public Topology topology() { return topology; }
    @Override public Telemetry telemetry() { return telemetry; }
    @Override public Collection<SignalState> signalStates() { return signalStates; }
    @Override public Collection<SwitchState> switchStates() { return switchStates; }
    @Override public Collection<RailwayEvent> pendingEvents() { return pendingEvents; }
}
