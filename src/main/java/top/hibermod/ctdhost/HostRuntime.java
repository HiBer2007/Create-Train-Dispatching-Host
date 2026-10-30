package top.hibermod.ctdhost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    static final Logger LOG = LoggerFactory.getLogger("ctdhost");

    private static HostRuntime instance;

    /** 注入是否生效过。{@code require = 0} 的注入失败不会报错，靠这个标志兜底。 */
    private static final AtomicLong TICKS = new AtomicLong();
    private static volatile boolean warnedAboutMissingHook;

    private final TopologyMirror mirror = new TopologyMirror();
    private final top.hibermod.ctdhost.operation.AssignmentApplier assignmentApplier =
        new top.hibermod.ctdhost.operation.AssignmentApplier();

    private final top.hibermod.ctdhost.operation.AssignmentResolver assignments =
        new top.hibermod.ctdhost.operation.AssignmentResolver();
    private final top.hibermod.ctdhost.operation.UnitIndex units =
        new top.hibermod.ctdhost.operation.UnitIndex();
    private final SpeedApplier speed = new SpeedApplier();
    private final List<Train> scratch = new ArrayList<>();

    /** 内核 id → 各自的 pass。一车一脚本时会有多个。 */
    private final java.util.Map<ResourceLocation, DispatchPass> passes = new java.util.LinkedHashMap<>();
    private long kernelRevision = -1;
    private CommandApplier applier;
    private final top.hibermod.ctdhost.execute.AtoPass atoPass =
        new top.hibermod.ctdhost.execute.AtoPass();

    /** 最近一帧采样的列车状态：ATO 需要它，而采样发生在采集阶段。 */
    private java.util.List<top.hibermod.ctdapi.Telemetry.TrainState> lastStates =
        java.util.List.of();

    private HostRuntime() {
        this.applier = new CommandApplier(
            () -> Create.RAILWAYS.trains,
            () -> Create.RAILWAYS.trackNetworks,
            speed);

        // pass 在第一次 tick 时按已注册内核惰性创建（见 refreshPasses）
    }

    public static HostRuntime get() {
        HostRuntime h = instance;
        if (h == null) {
            synchronized (HostRuntime.class) {
                if (instance == null) instance = new HostRuntime();
                // 内核在第一个 tick 里惰性激活
            }
            h = instance;
        }
        return h;
    }

    /**
     * 每帧调度入口。**两条路径都调到这里**：
     *
     * <ol>
     *   <li>Mixin {@code GlobalRailwayManager#tick} 的 HEAD（首选：抢在清空占用之前）；</li>
     *   <li>NeoForge {@code LevelTickEvent.Post}（兜底：Mixin 不可用时仍能工作）。</li>
     * </ol>
     *
     * <p>两条路径都活着时用 (维度, 刻) 去重，只会跑一次——
     * 抢占入口存在时以它为准，否则退回事件入口（晚一帧，但功能完整）。
     */
    public static void onRailwayTick(Level level) {
        try {
            HostRuntime h = HostRuntime.get();
            String key = level.dimension().location() + "#" + level.getGameTime();
            if (key.equals(h.lastTickKey)) return;
            h.lastTickKey = key;

            TICKS.incrementAndGet();
            h.tick(level);
        } catch (Throwable t) {
            // 宿主自己炸了不能拖垮服务端：下一个 tick 还要跑
            LOG.error("[ctdhost] dispatch tick failed: " + t);
        }
    }

    private long gameTime;

    /** 上一次处理过的 (维度, 游戏刻)，用于两条入口去重。 */
    private String lastTickKey;

    private void tick(Level level) {
        this.gameTime = level.getGameTime();
        applier.setGameTime(gameTime);

        // 首次收到 tick 时打一条，证明调度真的在跑（含入口来源）
        if (TICKS.get() <= 1) {
            LOG.info("ctdhost: dispatch ticking (gameTime={}, dim={})",
                gameTime, level.dimension().location());
        }

        // ① 内核：每帧决策（可能多个，各管各的车）
        refreshPasses();
        for (DispatchPass p : passes.values()) p.tick();

        // ② 控制通道：后台/玩家/自动化随时插话。
        //    刻意在内核之后执行——操作员的扣车不该被脚本下一帧放行。
        applyControlChannel();
    }

    private void applyControlChannel() {
        var d = top.hibermod.ctdapi.operation.ControlChannel.drain();
        if (d.isEmpty()) return;

        for (var a : d.assignments()) {
            top.hibermod.ctdapi.operation.Assignments.publish(a.assignment());
        }
        if (!d.trains().isEmpty()) {
            java.util.List<top.hibermod.ctdapi.Command.TrainCommand> list = new java.util.ArrayList<>();
            for (var c : d.orderedTrains()) list.add(c.command());
            speed.apply(list, Create.RAILWAYS.trains, gameTime);
        }
        if (!d.aspects().isEmpty()) {
            java.util.List<top.hibermod.ctdapi.Command.AspectCommand> list = new java.util.ArrayList<>();
            for (var c : d.aspects()) list.add(c.command());
            applier.applyAspects(list);
        }

        // ATO 在授权之后跑：内核说「能走多远」，ATO 说「怎么走」。
        // 它的输出只能收紧，所以不会推翻上面刚施加的限速。
        atoPass.run(gameTime, lastStates);
    }


    // ── 采集 ──────────────────────────────────────────────────────────────

    private DispatchPass.Inputs collect(java.util.function.Predicate<UUID> filter) {
        var graphs = Create.RAILWAYS.trackNetworks;
        TopologyView view = mirror.refreshIfChanged(graphs);

        scratch.clear();
        for (Train t : Create.RAILWAYS.trains.values()) {
            if (filter == null || filter.test(t.id)) scratch.add(t);
        }

        // 车辆编号索引：编号就是 Train.name，每帧对账
        top.hibermod.ctdhost.telemetry.StationWatcher.tick(gameTime);
        top.hibermod.ctdhost.telemetry.TrainFeedSync.tick(
            net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(), gameTime);

        // 线路限速：先于内核指令生效，内核也不能突破它
        top.hibermod.ctdhost.execute.RestrictionsApplier.apply(scratch, gameTime);
        top.hibermod.ctdhost.execute.HornWatcher.tick(gameTime);

        units.reconcile(scratch);
        assignments.refresh(Create.RAILWAYS.trains, units);
        assignmentApplier.apply(assignments, Create.RAILWAYS.trains);

        List<Telemetry.TrainState> states = new ArrayList<>(scratch.size());
        java.util.Set<java.util.UUID> alive = new java.util.HashSet<>();
        for (Train t : scratch) alive.add(t.id);
        for (Train t : scratch) {
            states.add(top.hibermod.ctdhost.telemetry.TelemetrySampler.sample(
                t, speed.authorityOf(t.id), null));
        }

        top.hibermod.ctdhost.telemetry.TelemetrySampler.prune(alive);

        lastStates = states;

        Telemetry telemetry = new Telemetry() {
            @Override public Collection<TrainState> trains() { return states; }
            @Override public TrainState train(UUID id) {
                for (TrainState s : states) if (s.id().equals(id)) return s;
                return null;
            }
        };

        List<FrameContext.SignalState> signalSnapshots = new java.util.ArrayList<>();
        for (var site : view.signals()) {
            String aspect = top.hibermod.ctdapi.status.SignalStates.get(site.id());
            ResourceLocation aspectId = aspect.isEmpty() ? null
                : ResourceLocation.tryParse(aspect);
            for (var side : top.hibermod.ctdapi.Topology.SignalSide.values()) {
                signalSnapshots.add(new FrameContext.SignalState(
                    site.id(), side, aspectId, 0L));
            }
        }

        // 岔口快照：内核要看见「这条岔口现在锁向哪、谁锁的」，
        // 否则锁闭指令只能盲目下发，无法判断锁是否被抢。
        List<FrameContext.SwitchState> switchSnapshots = new java.util.ArrayList<>();
        for (var lockEntry : applier.switches().locks().entrySet()) {
            switchSnapshots.add(new FrameContext.SwitchState(
                lockEntry.getKey(),
                lockEntry.getValue(),
                applier.switches().holderOf(lockEntry.getKey()),
                false));
        }
        return new DispatchPass.Inputs(
            gameTime,
            view, telemetry,
            signalSnapshots,
            switchSnapshots,
            List.<RailwayEvent>of());
    }

    // ── 对外查询 ──────────────────────────────────────────────────────────

    public static long tickCount() { return TICKS.get(); }

    public java.util.Map<ResourceLocation, DispatchPass> passes() { return passes; }

    /**
     * 按「已注册内核 × 车-脚本绑定」维护 pass 集合。
     *
     * <p>规则：
     * <ul>
     *   <li>没有任何绑定 → 单个内核看全部车（兼容默认行为）；</li>
     *   <li>有绑定 → 每个内核只看绑给它的车；未绑定的车不受任何内核干预。</li>
     * </ul>
     * 后一条很重要：默认「不干预」比「默认随便挑一个内核管」安全得多。
     */
    private void refreshPasses() {
        // 修订号变了说明注册表被改过——最典型的是**同名工厂被换掉**（热重载）。
        // 只看 id 集合发现不了这种替换：宿主会一直握着绑在旧实例上的 pass，
        // 表现为「重载成功，但脚本行为没变」。
        long rev = top.hibermod.ctdapi.DispatchKernels.revision();
        if (rev != kernelRevision) {
            if (kernelRevision >= 0 && !passes.isEmpty()) {
                LOG.info("[ctdhost] kernel registry changed (rev " + kernelRevision + " -> " + rev
                    + "), rebuilding " + passes.size() + " pass(es)");
                passes.clear();
            }
            kernelRevision = rev;
        }

        var ids = top.hibermod.ctdapi.DispatchKernels.registeredIds();
        boolean anyBinding = top.hibermod.ctdhost.operation.TrainScripts.size() > 0;

        // 再按 id 剪枝：内核被注销之后，它的 pass 必须立刻停掉。
        // 只加不减的话，旧脚本会继续驱动列车，而且每重载一次就多一个实例。
        passes.entrySet().removeIf(e -> {
            if (ids.contains(e.getKey())) return false;
            LOG.info("[ctdhost] kernel " + e.getKey() + " retired");
            return true;
        });

        for (ResourceLocation id : ids) {
            if (passes.containsKey(id)) continue;
            var kernel = top.hibermod.ctdapi.DispatchKernels.create(id).orElse(null);
            if (kernel == null) continue;

            final java.util.function.Predicate<UUID> filter = anyBinding
                ? top.hibermod.ctdhost.operation.TrainScripts.trainsOf(id)::contains
                : null;
            DispatchPass p = new DispatchPass(() -> collect(filter), applier);
            p.setTrainFilter(filter);
            p.activate(kernel, new Env());
            passes.put(id, p);
            LOG.info("[ctdhost] kernel " + id + " active"
                + (anyBinding ? " (" + top.hibermod.ctdhost.operation.TrainScripts.trainsOf(id).size() + " trains)" : " (all trains)"));
        }
    }

    public TopologyMirror mirror() { return mirror; }

    public SpeedApplier speed() { return speed; }

    /** 指令应用器（含转交计数）。 */
    public CommandApplier applier() { return applier; }

    public top.hibermod.ctdhost.operation.UnitIndex units() { return units; }

    public top.hibermod.ctdhost.operation.AssignmentResolver assignments() { return assignments; }

    public top.hibermod.ctdhost.operation.AssignmentApplier assignmentApplier() { return assignmentApplier; }

    /** 道岔应用器（逻辑锁 + 状态诊断）。 */
    public top.hibermod.ctdhost.execute.SwitchApplier switches() {
        return applier == null ? null : applier.switches();
    }

    /**
     * 启动后自检：注入没生效时给出明确告警，而不是让内核静默不工作。
     */
    public static void verifyHook(boolean afterStartup) {
        if (TICKS.get() == 0 && afterStartup && !warnedAboutMissingHook) {
            warnedAboutMissingHook = true;
            LOG.error("[ctdhost] WARNING: GlobalRailwayManager#tick hook never fired. "
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
            LOG.info("[ctdhost/" + level + "] " + out);
        }

        @Override public Settings settings() {
            return new Settings(64.0, true, 15.0, java.util.Map.of());
        }
    }
}
