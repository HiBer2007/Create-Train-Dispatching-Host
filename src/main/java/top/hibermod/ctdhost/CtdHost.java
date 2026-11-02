package top.hibermod.ctdhost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforge.event.server.ServerStartingEvent;

import top.hibermod.ctdapi.ApiCatalog;

/**
 * 宿主模组：把 CTD API 接到 Create 的列车系统上。
 *
 * <h2>它负责什么</h2>
 * <ul>
 *   <li>{@code Topology}：把 {@code TrackGraph} 镜像成稳定 id 的只读拓扑；</li>
 *   <li>{@code Telemetry}：每帧把 {@code Train} 采成 {@code TrainState}；</li>
 *   <li>{@code DispatchFrame}：把内核写的指令落回 Create；</li>
 *   <li>存档、安全钳位、信号/道岔的翻译。</li>
 * </ul>
 *
 * <h2>它不负责什么</h2>
 * 决定列车怎么走——那是内核（脚本或 Java 模组）的事。宿主只执行，不决策。
 */
@Mod(CtdHost.MOD_ID)
public final class CtdHost {

    private static final Logger LOG = LoggerFactory.getLogger("ctdhost");

    public static final String MOD_ID = "ctdhost";

    static {
        LOG.info("ctdhost: class loaded");
    }

    /**
     * 启动自检：mixin 到底挂上没有。
     *
     * <p>mixin 失败有两种表现：注入点找不到（配置成 required 会直接崩），
     * 或者接口没被织进去（不崩，但所有 instanceof 判断都是 false）。
     * 后者极难从现象上分辨，所以开机就打一行。
     */
    private static void checkSignalMixin() {
        try {
            boolean applied = top.hibermod.ctdapi.signal.SignalSystemHolder.class
                .isAssignableFrom(
                    com.simibubi.create.content.trains.signal.SignalBoundary.class);
            System.out.println("[ctdhost] SignalBoundary mixin "
                + (applied ? "APPLIED" : "MISSING -- signal system selection will not work"));
        } catch (Throwable t) {
            System.out.println("[ctdhost] SignalBoundary mixin check failed: " + t);
        }
    }
    public CtdHost(IEventBus modBus) {
        LOG.info("ctdhost: constructing (mod bus present: {})", modBus != null);

        // 客户端注册：放在构造的**最前面**，只要构造发生就一定执行。
        try {
            Class.forName("net.minecraft.client.Minecraft");
            Class.forName("top.hibermod.ctdhost.client.screen.CtdDisplayTypes")
                .getMethod("registerAll").invoke(null);
            System.out.println("[ctdhost] 客户端注册已调用");
        } catch (Throwable t) {
            System.out.println("[ctdhost] 客户端注册跳过/失败: " + t);
        }
        top.hibermod.ctdhost.item.CtdItems.ITEMS.register(modBus);
        // 方块与方块实体：自动道岔机（只有装了它的岔口才允许信号系统自动扳动）。
        top.hibermod.ctdhost.block.CtdBlocks.BLOCKS.register(modBus);
        top.hibermod.ctdhost.block.CtdBlocks.BLOCK_ENTITIES.register(modBus);
        top.hibermod.ctdhost.block.CtdBlocks.ITEMS.register(modBus);

        // 创造模式物品栏：不挂这个事件的话，方块在创造模式里看不到。
        modBus.addListener(top.hibermod.ctdhost.block.CtdCreativeTab::onBuildContents);

        // 客户端注册（站台屏类型 / 灯头渲染器）。
        //
        // 以前挂在 @Mixin 类的静态初始化块里——**mixin 的静态块不保证执行**，
        // 结果就是显示类型没注册、GUI 列表里看不到。这里改成构造期反射调用，
        // 服务端因为找不到 net.minecraft.client 会直接跳过。

        // 站台屏类型只在客户端注册。
        //
        // 用「类是否存在」判断，而不是 FMLEnvironment：前者不依赖任何 dist API，
        // 而且服务端根本没有 net.minecraft.client，Class.forName 会直接失败——
        // 失败即跳过，不需要额外的分支。

        // 查出来的「没注入」可能是「还没加载」。
        //
        // 也不能放在 common setup：那里 Train 往往**还没被加载过**，
        // 反射看到的是未织入的原生类，自检会把「还没轮到」误报成「注入失败」。
        // 实测过——它确实这样误报了一次。放到服务器启动完成，那时 Create 早已
        // 加载过 Train，查到的才是真实结果。
        NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartedEvent e) -> MixinChecks.run());
        DiagnosticKernel.registerIfEnabled();
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) ->
            e.getDispatcher().register(
                net.minecraft.commands.Commands.literal("ctd")
                    .requires(src -> src.hasPermission(2))
                    .then(top.hibermod.ctdhost.command.HostStatusCommand.build())
                    .then(top.hibermod.ctdhost.command.HostStateCommand.build())
                    .then(top.hibermod.ctdhost.command.HostSelfTestCommand.build())));

        // 兜底入口：Mixin 不可用（开发环境常见）时仍能每帧调度。
        // 与 Mixin 同时存在时由 HostRuntime 按 (维度, 刻) 去重。
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel().isClientSide()) return;
            HostRuntime.onRailwayTick(e.getLevel());
        });
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOG.info("[ctdhost] attached; " + ApiCatalog.entries().size() + " registry entries");

        // 车门许可的持久化：调度说「这个站台别开门」，重启之后仍然别开。
        // 只在内存里的话，一次重启就悄悄回到自动判定——这是安全相关的行为，
        // 不能靠「应该不会重启」来保证。
        var server = event.getServer();
        var saved = top.hibermod.ctdhost.save.HostSavedData.get(server);
        var tag = saved.doorPermissions();
        top.hibermod.ctdhost.door.DoorPermissions.loadFrom(tag);

        top.hibermod.ctdhost.door.DoorPermissions.setPersister(current -> {
            var t = saved.doorPermissions();
            t.getAllKeys().forEach(t::remove);
            current.forEach((pos, allowed) ->
                t.putBoolean(pos.getX() + "," + pos.getY() + "," + pos.getZ(), allowed));
            saved.setDirty();
        });

        // 注入自检：require=0 的注入失败不会报错，只能靠「有没有被调过」发现
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) ->
                HostRuntime.verifyHook(e.getServer().getTickCount() > 100));
    }
}
