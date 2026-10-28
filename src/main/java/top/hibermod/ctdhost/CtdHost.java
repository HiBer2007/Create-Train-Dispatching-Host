package top.hibermod.ctdhost;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
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

    public static final String MOD_ID = "ctdhost";

    public CtdHost(IEventBus modBus) {
        top.hibermod.ctdhost.item.CtdItems.ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void onServerStarting(ServerStartingEvent event) {
        System.out.println("[ctdhost] attached; " + ApiCatalog.entries().size() + " registry entries");
        // 注入自检：require=0 的注入失败不会报错，只能靠「有没有被调过」发现
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) ->
                HostRuntime.verifyHook(e.getServer().getTickCount() > 100));
    }
}
