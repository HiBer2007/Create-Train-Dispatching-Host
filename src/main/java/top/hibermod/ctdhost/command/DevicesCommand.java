package top.hibermod.ctdhost.command;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /ctd host devices} —— 摊开每台设备**最后收到的那条指令**。
 *
 * <h2>它补的是什么</h2>
 * `/ctd host status` 只有 {@code deviceCmds=1/7}：一台设备、七条指令。
 * 但「给哪台设备、发了什么」看不出来。内核脚本发了「开站台门」，
 * 宿主确实收下了（计数涨了），可 afterwards 没人能查「到底发的是什么」。
 *
 * <p>这条命令把 {@code DeviceCommands} 里记的东西原样打出来：
 * 设备 id、收到时的游戏刻、holder（谁发的）、payload 摘要。
 * 只读，不改任何状态。
 *
 * <h2>它不做什么</h2>
 * 不解释 payload。门、屏、计轴器的语义属于各自的设备实现；
 * 宿主这一层只保证「收到、存下、查得到」。
 */
public final class DevicesCommand {

    private DevicesCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("devices").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        var host = top.hibermod.ctdhost.HostRuntime.get();

        if (host.applier() == null) {
            src.sendFailure(Component.literal("applier 未初始化"));
            return 0;
        }

        var store = host.applier().deviceCommands();
        List<top.hibermod.ctdhost.execute.DeviceCommands.Received> snapshot = store.snapshot();

        // 先列「世界里的设备」：没有它们，指令列表只是些孤立 UUID。
        var topo = host.mirror() == null ? null : host.mirror().current();
        int siteCount = topo == null ? 0 : topo.devices().size();

        if (siteCount > 0) {
            src.sendSuccess(() -> Component.literal("世界里的设备：" + siteCount + " 台"), false);

            for (var site : topo.devices()) {
                var last = store.lastOf(site.id());
                final String line = "  " + site.typeId()
                    + "  " + site.id()
                    + (last == null ? "  (还没收到过指令)" : "  <- " + last.payload().summary());
                src.sendSuccess(() -> Component.literal(line), false);
            }

            src.sendSuccess(() -> Component.literal(""), false);
        } else {
            src.sendSuccess(() -> Component.literal(
                "拓扑里还没有设备（K4 的设备方块尚未实现；当前只有车站派生的站台设备）"), false);
        }

        if (snapshot.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                "还没有设备收到过指令（deviceCmds=0）；内核发 out.device(...) 之后这里会有条目"), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal(
            "设备指令落点：" + snapshot.size() + " 台设备，共接受 " + store.accepted()
                + " 条，拒绝 " + store.rejected() + " 条"), false);

        for (var received : snapshot) {
            final String line = "  " + received.deviceId()
                + "  t=" + received.gameTime()
                + "  holder=" + received.holder()
                + "  " + received.payload().summary();

            src.sendSuccess(() -> Component.literal(line), false);
        }

        return snapshot.size();
    }
}
