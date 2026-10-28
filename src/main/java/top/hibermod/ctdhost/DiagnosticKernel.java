package top.hibermod.ctdhost;

import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdapi.DispatchFrame;
import top.hibermod.ctdapi.DispatchKernel;
import top.hibermod.ctdapi.DispatchKernels;
import top.hibermod.ctdapi.FrameContext;
import top.hibermod.ctdapi.KernelEnv;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.operation.Assignment;
import top.hibermod.ctdapi.operation.Assignments;
import top.hibermod.ctdapi.operation.ControlChannel;
import top.hibermod.ctdapi.operation.Run;
import top.hibermod.ctdapi.operation.UnitSet;

/**
 * 自检内核：证明「宿主 → 内核 → 指令 → 应用」这条链在真实服务端里是通的。
 *
 * <p>它**不做调度**——只是数帧，并把看到的东西报出来。
 * 之所以需要有这么一个东西：在没有铺轨、没有列车的新世界，
 * 一个什么都不输出的宿主看起来和工作正常的宿主一模一样。
 *
 * <p>默认关闭，用 JVM 属性打开：{@code -Dctdhost.diagnostics=true}。
 */
public final class DiagnosticKernel implements DispatchKernel {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath("ctdhost", "diagnostic");

    /**
     * 开发环境默认开启，生产环境默认关闭。
     *
     * <p>判据用 {@code FMLLoader.isProduction()} 而不是 JVM 属性：
     * Gradle 传属性在不同 run 配置下路径不一样，漏传就变成「看起来没生效」。
     */
    private static final boolean ENABLED =
        Boolean.getBoolean("ctdhost.diagnostics")
            || !net.neoforged.fml.loading.FMLLoader.isProduction();

    private long frames;
    private long lastReport;

    /** 在模组构造期调用。默认不注册，避免生产环境多一个内核。 */
    public static void registerIfEnabled() {
        if (!ENABLED) return;
        DispatchKernels.register(ID, DiagnosticKernel::new);

        // 诊断用信号制度必须先注册，否则下面那条交路在 freeze 校验时会失败——
        // 这是两个模组一起跑才暴露的问题：ctdscript 启动时会 freezeAll，
        // 一个引用不存在制度的交路会让整批校验失败。
        // 必须先 bootstrap 内置灯色/语义，否则下面按语义登记的制度会缺 STOP 语义，
        // 校验器会（正确地）拒绝它：「a signal system that cannot stop trains」。
        // bootstrap 是幂等的。
        top.hibermod.ctdapi.signal.SignalRegistries.bootstrap();

        ResourceLocation sysId = ResourceLocation.fromNamespaceAndPath("ctdapi", "diagnostic");
        top.hibermod.ctdapi.signal.SignalRegistries.registerSystem(
            top.hibermod.ctdapi.signal.SignalSystem.builder(sysId)
                .name(net.minecraft.network.chat.Component.literal("Diagnostic"))
                .palette(top.hibermod.ctdapi.signal.LampColor.RED,
                         top.hibermod.ctdapi.signal.LampColor.GREEN)
                .aspect(top.hibermod.ctdapi.signal.SignalRegistries.Builtin.STOP)
                .aspect(top.hibermod.ctdapi.signal.SignalRegistries.Builtin.PROCEED)
                .type(top.hibermod.ctdapi.signal.SignalRegistries.registerType(
                    top.hibermod.ctdapi.signal.SignalType
                        .builder(ResourceLocation.fromNamespaceAndPath("ctdapi", "diagnostic_head"))
                        .system(sysId)
                        .name(net.minecraft.network.chat.Component.literal("Diagnostic Head"))
                        .lamp(top.hibermod.ctdapi.signal.LampColor.RED)
                        .lamp(top.hibermod.ctdapi.signal.LampColor.GREEN)
                        .build()))
                .build());

        // 诊断用交路：两条停站，供「指派 → 时刻表」这段链路验证。
        // 停站 id 不需要对应真实的 Create 车站——Applier 只是把它们写进指令的 Text。
        ResourceLocation svcId = ResourceLocation.fromNamespaceAndPath("ctdapi", "diagnostic");
        top.hibermod.ctdapi.timetable.Service.register(
            top.hibermod.ctdapi.timetable.Service.builder(svcId)
                .code("T0")
                .signalSystem(sysId)
                .stop(ResourceLocation.fromNamespaceAndPath("ctdapi", "alpha"))
                .stop(ResourceLocation.fromNamespaceAndPath("ctdapi", "beta"))
                .build());
        HostRuntime.LOG.info("ctdhost: diagnostic kernel enabled");
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void onAttach(KernelEnv env) {
        env.log("info", "ctdhost: diagnostic kernel attached");
    }

    @Override
    public void onFrame(FrameContext in, DispatchFrame out) {
        frames++;

        // 自检运营身份链路：每 600 帧发布一次指派，验证 ControlChannel → Assignments 是通的。
        // 没有列车也能验——断言的是「登记进了注册表」，不是「列车按它跑」。
        if (frames % 600 == 0) {
            ResourceLocation setId = ResourceLocation.fromNamespaceAndPath("ctdhost", "diagnostic");
            ResourceLocation runId = ResourceLocation.fromNamespaceAndPath("ctdapi", "diagnostic");
            UnitSet unit = new UnitSet(setId,
                net.minecraft.network.chat.Component.literal("diag"),
                java.util.List.of(new top.hibermod.ctdapi.operation.UnitNumber("CTD-DIAG")));
            Run run = new Run(runId, "T" + frames, in.gameTime());
            Assignment assignment = new Assignment(
                ResourceLocation.fromNamespaceAndPath("ctdhost", "diag_run"), run,
                java.util.List.of(unit), Assignment.Source.KERNEL, in.gameTime());
            ControlChannel.assign(ControlChannel.Source.AUTOMATION, in.gameTime(), assignment);
            HostRuntime.LOG.info("ctdhost: diagnostic queued assignment {} (registry now {})",
                "T" + frames, Assignments.size());
        }
        // 每 60 帧报一次：证明跨语言/跨层调用真的在持续发生，
        // 而不是只在启动时凑巧调了一次。
        if (frames - lastReport >= 60) {
            lastReport = frames;
            int trains = in.telemetry() == null ? -1 : in.telemetry().trains().size();
            int nodes = in.topology() == null ? -1 : in.topology().nodes().size();
            HostRuntime.LOG.info(
                "ctdhost: diagnostic frames={} trains={} nodes={} gameTime={}",
                frames, trains, nodes, in.gameTime());
        }
    }

    /** 供测试读取。 */
    public long frames() {
        return frames;
    }

    @SuppressWarnings("unused")
    private static void touch(Command.TrainCommand c, Telemetry.TrainState s) {
        // 保持导入被使用，避免 IDE 清理掉上面需要的类型
    }
}