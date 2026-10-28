package top.hibermod.ctdhost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.DispatchKernel;
import top.hibermod.ctdapi.DispatchKernels;
import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.RailwayEvent;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdhost.dispatch.DispatchFrameImpl;
import top.hibermod.ctdhost.dispatch.DispatchPass;
import top.hibermod.ctdhost.execute.CommandApplier;
import top.hibermod.ctdhost.execute.SpeedApplier;
import top.hibermod.ctdhost.topology.TopologyMirror;
import top.hibermod.ctdhost.topology.TopologyView;

/**
 * 宿主的运行时单例：混入只调这里一个静态方法。
 *
 * <h2>为什么不每帧 new 一堆东西</h2>
 * 每帧要处理全部列车与全部轨道图。所有可变状态（镜像、采样缓冲、应用器）
 * 都活在这个单例里，帧内只做「填充」，把分配压到最低。
 */
public final class HostRuntime {

    private static HostRuntime instance;

    /** 注入是否生效过。{@code require = 0} 的注入失败不会报错，靠这个标志兜底。 */
    private static final AtomicLong TICKS = new AtomicLong();
    private static volatile boolean warnedAboutMissingHook;

    private final TopologyMirror mirror = new TopologyMirror();
    private final SpeedApplier speed = new SpeedApplier();
    private final List<Train> scratch = new ArrayList<>();

    private DispatchPass pass;
    private CommandApplier applier;

    private HostRuntime() {
        this.applier = new CommandApplier(
            () -> Create.RAILWAYS.trains,
            () -> Create.RAILWAYS.trackNetworks,
            speed);

        this.pass = new DispatchPass(this::collect, applier);
    }

    public static HostRuntime get() {
        HostRuntime h = instance;
        if (h == null) {
            synchronized (HostRuntime.class) {
                if (instance == null) instance = new HostRuntime();
                instance.activateFirstKernel();
            }
            h = instance;
        }
        return h;
    }

    /** 混入入口。每帧一次。 */
    public static void onRailwayTick(Level level) {
        try {
            TICKS.incrementAndGet();
            HostRuntime.get().tick(level);
        } catch (Throwable t) {
            // 宿主自己炸了不能拖垮服务端：下一个 tick 还要跑
            System.err.println("[ctdhost] dispatch tick failed: " + t);
        }
    }

    private long gameTime;

    private void tick(Level level) {
        this.gameTime = level.getGameTime();
        applier.setGameTime(gameTime);
        pass.tick();
    }

    /** 选一个已注册的内核激活。后续由配置或命令切换。 */
    private void activateFirstKernel() {
        for (var id : DispatchKernels.registeredIds()) {
            DispatchKernel k = DispatchKernels.create(id).orElse(null);
            if (k != null) {
                applier.setGameTime(0);
                pass.activate(k, new Env());
                System.out.println("[ctdhost] activated kernel " + id);
                return;
            }
        }
        System.out.println("[ctdhost] no dispatch kernel registered; running Create native behaviour");
    }

    // ── 采集 ──────────────────────────────────────────────────────────────

    private DispatchPass.Inputs collect() {
        var graphs = Create.RAILWAYS.trackNetworks;
        TopologyView view = mirror.refreshIfChanged(graphs);

        scratch.clear();
        scratch.addAll(Create.RAILWAYS.trains.values());

        List<Telemetry.TrainState> states = new ArrayList<>(scratch.size());
        for (Train t : scratch) {
            states.add(top.hibermod.ctdhost.telemetry.TelemetrySampler.sample(
                t, speed.authorityOf(t.id), null));
        }

        Telemetry telemetry = new Telemetry() {
            @Override public Collection<TrainState> trains() { return states; }
            @Override public TrainState train(UUID id) {
                for (TrainState s : states) if (s.id().equals(id)) return s;
                return null;
            }
        };

        return new DispatchPass.Inputs(
            gameTime,
            view, telemetry,
            List.<FrameContext.SignalState>of(),           // 信号快照：待 C6 完善
            List.<FrameContext.SwitchState>of(),           // 岔口快照：待 C7
            List.<RailwayEvent>of());
    }

    // ── 对外查询 ──────────────────────────────────────────────────────────

    public static long tickCount() { return TICKS.get(); }

    public DispatchPass pass() { return pass; }

    public TopologyMirror mirror() { return mirror; }

    public SpeedApplier speed() { return speed; }

    /**
     * 启动后自检：注入没生效时给出明确告警，而不是让内核静默不工作。
     */
    public static void verifyHook(boolean afterStartup) {
        if (TICKS.get() == 0 && afterStartup && !warnedAboutMissingHook) {
            warnedAboutMissingHook = true;
            System.err.println("[ctdhost] WARNING: GlobalRailwayManager#tick hook never fired. "
                + "The mixin probably failed to apply (upstream signature changed). "
                + "Dispatch is inactive; trains use vanilla Create behaviour.");
        }
    }

    // ── 最小 KernelEnv ────────────────────────────────────────────────────

    private static final class Env implements top.hibermod.ctdapi.KernelEnv {
        private final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();

        @Override public long gameTime() { return TICKS.get(); }
        @Override public net.minecraft.nbt.CompoundTag state() { return tag; }
        @Override public void markStateDirty() { }

        @Override public top.hibermod.ctdapi.Topology topology() {
            return HostRuntime.get().mirror().current();
        }

        @Override public void log(String level, String message, Object... args) {
            String out = message;
            for (Object a : args) {
                out = out.replaceFirst("\\{\\}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
            }
            System.out.println("[ctdhost/" + level + "] " + out);
        }

        @Override public Settings settings() {
            return new Settings(64.0, true, 15.0, java.util.Map.of());
        }
    }
}
