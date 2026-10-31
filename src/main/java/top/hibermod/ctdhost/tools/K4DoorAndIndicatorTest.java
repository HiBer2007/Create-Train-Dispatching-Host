package top.hibermod.ctdhost.tools;

import net.minecraft.core.BlockPos;

import top.hibermod.ctdapi.pis.Pis;
import top.hibermod.ctdhost.command.DeparturesCommand;
import top.hibermod.ctdhost.door.DoorPermissions;
import top.hibermod.ctdhost.pis.BoardStore;
import top.hibermod.ctdhost.pis.DepartureIndicatorService;

import net.minecraft.network.chat.Component;

/**
 * K4 的纯逻辑：门许可的优先级、发车指示器的偏差与颜色、字符转 JSON。
 *
 * <h2>为什么这些能离线测</h2>
 * 它们都不碰世界：
 * <ul>
 *   <li>{@link DoorPermissions} 只是一张表 + 一条优先级规则；</li>
 *   <li>偏差与颜色是整数运算；</li>
 *   <li>字符转 JSON 是字符串处理。</li>
 * </ul>
 *
 * <p>真正需要世界的那部分（找站台、找辉光管、开关门）留给游戏内命令。
 * 这里先把「规则」钉死——规则错了，世界里做什么都是错的。
 */
public final class K4DoorAndIndicatorTest {

    private K4DoorAndIndicatorTest() {}

    public static void main(String[] args) {
        int failures = 0;

        // ── 门许可：显式 > 自动 > 兜底 ─────────────────────────────────
        BlockPos station = new BlockPos(100, 64, 200);
        BlockPos other = new BlockPos(300, 64, 400);

        DoorPermissions.clear();

        failures += check("没有任何判定时用兜底 true", DoorPermissions.allowed(station, true));
        failures += check("没有任何判定时用兜底 false", !DoorPermissions.allowed(station, false));

        DoorPermissions.setAuto(station, true, 1L);
        failures += check("自动判定生效", DoorPermissions.allowed(station, false));

        DoorPermissions.setExplicit(station, false);
        failures += check("显式覆盖自动", !DoorPermissions.allowed(station, true));
        failures += check("显式可查询", DoorPermissions.hasExplicit(station));

        DoorPermissions.setExplicit(station, null);
        failures += check("解除显式后回到自动", DoorPermissions.allowed(station, false));
        failures += check("解除后不再标记显式", !DoorPermissions.hasExplicit(station));

        failures += check("别的站台不受影响", DoorPermissions.allowed(other, true));

        DoorPermissions.clear();
        failures += check("清空后回到兜底", DoorPermissions.allowed(station, true));

        // ── 偏差与颜色 ────────────────────────────────────────────────
        failures += check("晚点 → LATE", DeparturesCommand.lampFor(600, 720) == DeparturesCommand.Lamp.LATE);
        failures += check("早点 → EARLY", DeparturesCommand.lampFor(600, 480) == DeparturesCommand.Lamp.EARLY);
        failures += check("正点 → ON_TIME", DeparturesCommand.lampFor(600, 600) == DeparturesCommand.Lamp.ON_TIME);
        failures += check("未知 → UNKNOWN", DeparturesCommand.lampFor(-1, 600) == DeparturesCommand.Lamp.UNKNOWN);
        failures += check("实时未知 → UNKNOWN", DeparturesCommand.lampFor(600, -1) == DeparturesCommand.Lamp.UNKNOWN);

        failures += check("偏差 = eta - scheduled", DeparturesCommand.deviationOf(600, 720) == 120);
        failures += check("早点偏差为负", DeparturesCommand.deviationOf(600, 480) == -120);
        failures += check("未知偏差为 0", DeparturesCommand.deviationOf(-1, 720) == 0);

        // ── 格式化 ────────────────────────────────────────────────────
        failures += check("+120t = +0:06", DeparturesCommand.formatDeviation(120).equals("+0:06"),
            DeparturesCommand.formatDeviation(120));
        failures += check("-120t = -0:06", DeparturesCommand.formatDeviation(-120).equals("-0:06"),
            DeparturesCommand.formatDeviation(-120));
        failures += check("0 = 0:00", DeparturesCommand.formatDeviation(0).equals("0:00"),
            DeparturesCommand.formatDeviation(0));
        failures += check("+20*90=+1:30", DeparturesCommand.formatDeviation(20 * 90).equals("+1:30"),
            DeparturesCommand.formatDeviation(20 * 90));

        // ── 指示器：从 PIS 数据到显示字符串 ────────────────────────────
        BoardStore.clear();
        failures += check("没有数据时 textFor 为 null",
            DepartureIndicatorService.textFor(station) == null);
        failures += check("没有数据时颜色是暗",
            DepartureIndicatorService.colorFor() == DepartureIndicatorService.DIM);

        Pis.BoardEntry late = new Pis.BoardEntry(
            "G1234", null, Component.literal("北京南"), java.util.List.of(),
            600, 720, 120, null, Pis.Status.DELAYED, null, Pis.Urgency.NORMAL);

        BoardStore.update(java.util.List.of(new Pis.BoardModel(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ctd", "probe"),
            1L, null, java.util.List.of(late))), 100L);

        String text = DepartureIndicatorService.textFor(station);
        failures += check("晚点显示 +0:06", "+0:06".equals(text), String.valueOf(text));
        failures += check("晚点颜色是红",
            DepartureIndicatorService.colorFor() == DepartureIndicatorService.RED);

        Pis.BoardEntry early = new Pis.BoardEntry(
            "G1234", null, Component.literal("北京南"), java.util.List.of(),
            600, 480, -120, null, Pis.Status.EARLY, null, Pis.Urgency.NORMAL);

        BoardStore.update(java.util.List.of(new Pis.BoardModel(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ctd", "probe"),
            2L, null, java.util.List.of(early))), 120L);

        failures += check("早点显示 -0:06",
            "-0:06".equals(DepartureIndicatorService.textFor(station)),
            DepartureIndicatorService.textFor(station));
        failures += check("早点颜色是绿",
            DepartureIndicatorService.colorFor() == DepartureIndicatorService.GREEN);

        // ── 字符 → JSON ───────────────────────────────────────────────
        failures += check("数字转 JSON", DepartureIndicatorService.charJson('7').equals("{\"text\":\"7\"}"),
            DepartureIndicatorService.charJson('7'));
        failures += check("加号转 JSON", DepartureIndicatorService.charJson('+').equals("{\"text\":\"+\"}"),
            DepartureIndicatorService.charJson('+'));
        failures += check("引号被转义",
            DepartureIndicatorService.charJson('"').equals("{\"text\":\"\\\"\"}"),
            DepartureIndicatorService.charJson('"'));

        BoardStore.clear();

        System.out.println();
        if (failures > 0) {
            System.out.println("K4 门与指示器逻辑验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("K4 门与指示器逻辑验收通过：门许可优先级、偏差/颜色、字符 JSON");
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
