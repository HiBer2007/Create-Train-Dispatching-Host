package top.hibermod.ctdhost.pis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import top.hibermod.ctdapi.pis.Pis;
import top.hibermod.ctdhost.Failures;

/**
 * 发车指示器：把「距离正点还有多久」写到站台旁的辉光管上。
 *
 * <h2>为什么用辉光管而不是自建方块</h2>
 * 辉光管是 Create 已有的方块，**服务端就能改它的显示内容**
 * （{@code displayCustomText}），不需要写任何客户端渲染代码。
 * 这样这一条立刻可验：命令能把「写了什么、读回来是什么」都打出来。
 *
 * <h2>颜色规则</h2>
 * 早点 = 绿，晚点 = 红，正点 = 白。规则本身在
 * {@code DeparturesCommand.lampFor} 里，是纯函数，可离线测。
 *
 * <h2>局限（写在这里，不藏着）</h2>
 * <ul>
 *   <li><b>一根辉光管只显示一个字符</b>，所以字符串按相邻管子逐字排开；
 *       管子少于字符数时截断。</li>
 *   <li><b>原生辉光管上「文字」与「颜色」互斥</b>：颜色只走
 *       {@code computerSignal.first/second} 那条路（Create 的电脑信号集成），
 *       而一旦有 computerSignal，自定义文本就不再显示。
 *       所以这里只写文字，颜色留给高级显示器或自建方块（客户端渲染）。</li>
 * </ul>
 */
public final class DepartureIndicatorService {

    private DepartureIndicatorService() {}

    /** 一根管子的 RGB（0–63）。 */
    public record Rgb(byte r, byte g, byte b) {}

    public static final Rgb GREEN = new Rgb((byte) 10, (byte) 55, (byte) 15);
    public static final Rgb RED = new Rgb((byte) 60, (byte) 8, (byte) 8);
    public static final Rgb WHITE = new Rgb((byte) 50, (byte) 50, (byte) 50);
    public static final Rgb DIM = new Rgb((byte) 0, (byte) 0, (byte) 0);

    /** 找管子的半径。 */
    public static final int RADIUS = 8;

    // 注意：不能用 Long.MIN_VALUE 当初值——gameTime - Long.MIN_VALUE 会溢出成负数，
    // 于是 `gameTime - lastRun < 周期` 永远成立，整个服务一次都不跑（实测踩过）。
    private static long lastRun = -1L;
    private static int lastWritten;

    /** 每 20 刻跑一次。 */
    public static void tick(ServerLevel level, long gameTime) {
        if (lastRun >= 0 && gameTime - lastRun < 20) return;
        lastRun = gameTime;

        int written = 0;
        try {
            for (var graph : Create.RAILWAYS.trackNetworks.values()) {
                for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                    BlockPos stationPos = station.getBlockEntityPos();
                    if (stationPos == null) continue;
                    written += apply(level, stationPos);
                }
            }
        } catch (Throwable t) {
            Failures.caught("发车指示器：写入阶段", t);
        }
        lastWritten = written;
    }

    /** 这个站台要显示什么。没有 PIS 数据时返回 {@code null}。 */
    public static String textFor(BlockPos stationPos) {
        Pis.BoardEntry entry = nextDeparture();
        if (entry == null) return null;

        int deviation = deviationOf(entry);
        int totalSeconds = Math.abs(deviation) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String sign = deviation > 0 ? "+" : deviation < 0 ? "-" : "";
        return sign + minutes + ":" + String.format("%02d", seconds);
    }

    /** 偏差（正数晚点）。 */
    public static int deviationOf(Pis.BoardEntry entry) {
        if (entry == null) return 0;
        if (entry.scheduledTicks() < 0 || entry.etaTicks() < 0) return 0;
        return entry.etaTicks() - entry.scheduledTicks();
    }

    /** 颜色：早点绿、晚点红、正点白、未知暗。 */
    public static Rgb colorFor() {
        Pis.BoardEntry entry = nextDeparture();
        if (entry == null) return DIM;
        if (entry.scheduledTicks() < 0 || entry.etaTicks() < 0) return DIM;

        int deviation = deviationOf(entry);
        if (deviation < 0) return GREEN;
        if (deviation > 0) return RED;
        return WHITE;
    }

    /** 取最近的一条发车信息（第一块屏的第一条）。 */
    public static Pis.BoardEntry nextDeparture() {
        for (Pis.BoardModel model : BoardStore.snapshot().values()) {
            if (model.entries().isEmpty()) continue;
            return model.entries().get(0);
        }
        return null;
    }

    /** 把文本写到站台旁的辉光管上；返回写了多少根。 */
    public static int apply(ServerLevel level, BlockPos stationPos) {
        String text = textFor(stationPos);
        List<NixieTubeBlockEntity> tubes = findTubes(level, stationPos);

        if (tubes.isEmpty()) return 0;

        for (int i = 0; i < tubes.size(); i++) {
            NixieTubeBlockEntity tube = tubes.get(i);
            try {
                if (text == null || i >= text.length()) {
                    tube.displayEmptyText(i);
                } else {
                    tube.displayCustomText(charJson(text.charAt(i)), i);
                }
            } catch (Throwable t) {
                Failures.caught("发车指示器：写 " + tube.getBlockPos(), t);
            }
        }

        return tubes.size();
    }

    /** 读回一组管子当前显示的内容（命令用）。 */
    public static List<String> readback(ServerLevel level, BlockPos stationPos) {
        List<String> out = new ArrayList<>();
        for (NixieTubeBlockEntity tube : findTubes(level, stationPos)) {
            // 用 getFullText()：它直接解析 customText，而 getDisplayedStrings()
            // 要等 BE 的 tick 把 displayedStrings 算出来才非空（命令是瞬时读的）。
            String text = tube.getFullText().getString();
            out.add(tube.getBlockPos().toShortString() + " = '" + text + "'");
        }
        return out;
    }

    /** 找站台附近的一排辉光管，按坐标排序（先 y，再 x，再 z）。 */
    public static List<NixieTubeBlockEntity> findTubes(ServerLevel level, BlockPos stationPos) {
        List<NixieTubeBlockEntity> found = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                stationPos.offset(-RADIUS, -3, -RADIUS),
                stationPos.offset(RADIUS, 3, RADIUS))) {
            var be = level.getBlockEntity(pos);
            if (be instanceof NixieTubeBlockEntity tube) {
                found.add(tube);
            }
        }

        found.sort(Comparator
            .comparingInt((NixieTubeBlockEntity t) -> t.getBlockPos().getY())
            .thenComparingInt(t -> t.getBlockPos().getX())
            .thenComparingInt(t -> t.getBlockPos().getZ()));

        return found;
    }

    /** 一个字符转成文字组件的 JSON（辉光管要的是 JSON 字符串）。 */
    public static String charJson(char c) {
        String escaped = String.valueOf(c)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
        return "{\"text\":\"" + escaped + "\"}";
    }

    public static int lastWritten() {
        return lastWritten;
    }

    public static void reset() {
        lastRun = -1L;
        lastWritten = 0;
    }

    /** 供命令显示用：当前会写什么。 */
    public static String describe(BlockPos stationPos) {
        String text = textFor(stationPos);
        return text == null ? "(无 PIS 数据)" : text;
    }
}
