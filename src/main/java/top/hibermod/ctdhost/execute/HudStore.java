package top.hibermod.ctdhost.execute;

import java.util.List;

import top.hibermod.ctdapi.hud.Hud;

/**
 * 最近一帧内核推来的 HUD 数据。
 *
 * <h2>为什么要在宿主里留一份</h2>
 * HUD 数据原本只经 {@code OperationFeed} 转手给订阅者，**谁都没留底**。
 * 于是「屏幕上画的和内核说的不一致」时，没有任何地方能回答
 * 「内核到底说了什么」——只能去改代码加日志、重启、复现。
 *
 * <p>留一份最近快照，`/ctd host hud` 就能当场回答这个问题。
 * 它只存**最近一帧**（不累积），所以内存占用是有界的。
 */
public final class HudStore {

    private HudStore() {}

    private static volatile List<Hud.HudPanel> last = List.of();
    private static volatile long gameTime = -1;

    /** 每帧由 {@code CommandApplier.applyHud} 调用。 */
    public static void update(List<Hud.HudPanel> panels, long now) {
        last = List.copyOf(panels);
        gameTime = now;
    }

    /** 最近一帧的面板；没有推送过则为空列表。 */
    public static List<Hud.HudPanel> snapshot() {
        return last;
    }

    /** 最近一次推送发生在哪个游戏刻；从未推送为 -1。 */
    public static long gameTime() {
        return gameTime;
    }

    public static void clear() {
        last = List.of();
        gameTime = -1;
    }
}
