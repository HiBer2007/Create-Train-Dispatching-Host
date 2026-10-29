package top.hibermod.ctdhost.telemetry;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


import top.hibermod.ctdapi.net.TrainFeedPayload;

/**
 * 把**服务端真实车速**推给客户端。
 *
 * <h2>为什么不能让客户端自己算</h2>
 * Create 只在有人操控时同步速度（{@code CarriageContraptionEntity#control:571}）。
 * 松手或自动驾驶之后，客户端那份 {@code train.speed} 就停在那里不再变，
 * 而列车还在减速——仪表会显示一张过期的照片。
 *
 * <p>客户端从实体位移反推也不行：位置是按帧插值的，算出来的速度抖得没法看，
 * 而且分不清「车在动」和「人在走」。
 *
 * <h2>频率</h2>
 * 每 2 刻一次（10 Hz）。HUD 的刷新间隔最快也是 1 刻，10 Hz 足够跟手；
 * 再高就是拿带宽换一个看不出来的差别。
 */
public final class TrainFeedSync {

    /** 每几刻推一次。 */
    private static final int INTERVAL = 2;

    private static long lastSent;

    /** 发不出去（对端没装 ctdscript）时置位，避免每 2 刻刷一次异常。 */
    private static boolean disabled;

    private TrainFeedSync() {}

    /** 每帧调用；内部按 {@link #INTERVAL} 节流。 */
    public static void tick(MinecraftServer server, long gameTime) {
        if (disabled || server == null) return;
        if (gameTime - lastSent < INTERVAL) return;
        lastSent = gameTime;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;

        List<TrainFeedPayload.Entry> entries = new ArrayList<>();
        for (Train train : Create.RAILWAYS.trains.values()) {
            entries.add(new TrainFeedPayload.Entry(
                train.id,
                train.speed,
                train.targetSpeed,
                train.manualTick || train.runtime == null || train.runtime.paused,
                gameTime));
        }

        TrainFeedPayload payload = new TrainFeedPayload(entries);
        for (ServerPlayer player : players) {
            try {
                player.connection.send(payload);
            } catch (Throwable t) {
                // 客户端没装 ctdscript（没人注册这个包）时会走到这里：
                // 记一次就停，别每 2 刻刷一条栈
                disabled = true;
                System.out.println("[ctdhost] train feed disabled: " + t);
                return;
            }
        }
    }
}
