package top.hibermod.ctdhost.dispatch;

import java.util.List;
import java.util.function.Supplier;

import top.hibermod.ctdapi.DispatchKernel;
import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.KernelEnv;
import top.hibermod.ctdapi.RailwayEvent;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.Topology;

/**
 * 每帧的编排：采集 → 调内核 → 应用。
 *
 * <p><b>本类刻意不知道 Create</b>：它只认 {@link Topology} / {@link Telemetry} /
 * {@link DispatchFrameImpl.CommandSink}。Create 相关的东西全部由实现方在构造时塞进来。
 * 这样编排逻辑可以离线测试，也不会把 Create 的类型渗到调度流程里。
 *
 * <h2>失败语义</h2>
 * 内核抛异常 → **丢弃整帧**（不写任何指令）→ 列车退回 Create 原生行为。
 * 不选「全部停车」：一台坏脚本不该让整条线网瘫掉。
 */
public final class DispatchPass {

    /** 一帧需要的输入。 */
    public record Inputs(
        long gameTime,
        Topology topology,
        Telemetry telemetry,
        List<FrameContext.SignalState> signals,
        List<FrameContext.SwitchState> switches,
        List<RailwayEvent> events
    ) {}

    private final Supplier<Inputs> collector;
    private final DispatchFrameImpl.CommandSink sink;
    private final FrameContextImpl context = new FrameContextImpl();
    private final DispatchFrameImpl frame = new DispatchFrameImpl();

    private DispatchKernel kernel;
    private KernelEnv env;

    private int consecutiveFailures;
    private int disabledAfter = 10;
    private boolean disabled;

    public DispatchPass(Supplier<Inputs> collector, DispatchFrameImpl.CommandSink sink) {
        this.collector = collector;
        this.sink = sink;
    }

    /** 激活/切换内核。旧内核会收到 {@code onDetach}。 */
    public void activate(DispatchKernel newKernel, KernelEnv env) {
        if (kernel != null && kernel != newKernel) {
            try {
                kernel.onDetach();
            } catch (Exception e) {
                env.log("warn", "ctdhost: kernel {} onDetach failed: {}", kernel.id(), e.getMessage());
            }
        }
        this.kernel = newKernel;
        this.env = env;
        this.consecutiveFailures = 0;
        this.disabled = false;
        if (newKernel != null) {
            try {
                newKernel.onAttach(env);
            } catch (Exception e) {
                env.log("error", "ctdhost: kernel {} onAttach failed: {}", newKernel.id(), e.getMessage());
            }
        }
    }

    public DispatchKernel kernel() { return kernel; }
    public boolean disabled() { return disabled; }
    public int consecutiveFailures() { return consecutiveFailures; }

    /** 连续失败多少次后停用。设 0 表示永不停用。 */
    public void setDisabledAfter(int n) { this.disabledAfter = Math.max(0, n); }

    /**
     * 跑一帧。
     *
     * @return 本帧是否真的产生了指令
     */
    public boolean tick() {
        if (kernel == null || disabled) return false;

        Inputs in;
        try {
            in = collector.get();
        } catch (Exception e) {
            fail("collector", e);
            return false;
        }

        context.fill(in.gameTime(), in.topology(), in.telemetry(),
            in.signals(), in.switches(), in.events());

        // 清掉上一帧的指令：DispatchFrameImpl 是复用的
        boolean hadCommands;
        try {
            DispatchFrameImpl fresh = new DispatchFrameImpl();
            kernel.onFrame(context, fresh);
            if (fresh.isEmpty()) return false;
            fresh.applyTo(sink);
            hadCommands = true;
            consecutiveFailures = 0;
        } catch (Throwable t) {
            // 整帧作废：sink 可能已经应用了一部分，接管方要保证「部分应用」是安全的
            // —— 实现方的 CommandSink 必须逐条独立，应用失败只丢那一条。
            fail("onFrame", t);
            return false;
        }
        return hadCommands;
    }

    private void fail(String phase, Throwable t) {
        consecutiveFailures++;
        if (env != null) {
            if (consecutiveFailures == 1 || consecutiveFailures % 10 == 0) {
                env.log("error", "ctdhost: kernel {} failed in {} ({} consecutive): {}",
                    kernel.id(), phase, consecutiveFailures, t.toString());
            }
            if (disabledAfter > 0 && consecutiveFailures >= disabledAfter) {
                disabled = true;
                env.log("error", "ctdhost: kernel {} disabled after {} consecutive failures; "
                    + "trains fall back to vanilla Create behaviour", kernel.id(), consecutiveFailures);
            }
        }
    }
}
