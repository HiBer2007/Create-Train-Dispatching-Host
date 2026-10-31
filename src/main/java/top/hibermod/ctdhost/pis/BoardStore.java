package top.hibermod.ctdhost.pis;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import top.hibermod.ctdapi.pis.Pis;

/**
 * 最近一帧的 PIS 屏内容。
 *
 * <h2>为什么宿主也要留一份</h2>
 * 屏内容原本只经 {@code OperationFeed} 转给订阅者，宿主自己不留。
 * 但发车指示器、站台门指示器都要读它——它们不是"订阅者"，
 * 而是世界里的方块，按位置取数据。
 *
 * <p>只保留**最近一帧**（每块屏一条），不累积历史。
 */
public final class BoardStore {

    private BoardStore() {}

    private static final Map<String, Pis.BoardModel> LAST = new ConcurrentHashMap<>();
    private static volatile long gameTime = -1;

    public static void update(List<Pis.BoardModel> boards, long now) {
        gameTime = now;
        // 空列表 = 这一帧没有屏发布，**不是**「屏该清空」。
        // DispatchFrame#board 的契约是「不发布 = 保持上次内容」，
        // 这里清空会让一次性推送的屏立刻消失（实测踩过：内核推了，命令却读不到）。
        if (boards == null || boards.isEmpty()) {
            return;
        }
        for (Pis.BoardModel model : boards) {
            LAST.put(model.boardId().toString(), model);
        }
    }

    /** 所有屏的最新内容。 */
    public static Map<String, Pis.BoardModel> snapshot() {
        return Map.copyOf(LAST);
    }

    public static Pis.BoardModel board(String boardId) {
        return LAST.get(boardId);
    }

    public static long gameTime() {
        return gameTime;
    }

    public static void clear() {
        LAST.clear();
        gameTime = -1;
    }
}
