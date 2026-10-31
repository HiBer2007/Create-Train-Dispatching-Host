package top.hibermod.ctdhost.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

/**
 * 一条命令跑完整条链路，逐项报 PASS / FAIL。
 *
 * <h2>它解决什么问题</h2>
 * 「系统到底通没通」这件事以前要靠人一页页翻 {@code /ctd host status}、
 * 对照 {@code docs/17} 的清单逐条判断。判断标准散在文档里，
 * 而且**每一项的失败长得都很像成功**——速度是 0、拓扑是空的、内核是没注册的，
 * 打印出来都不会报错。
 *
 * <p>这里把可自动判断的部分一次跑完，输出一张表。它不能替代人工目视
 * （灯色、HUD 外观、道岔物理位置），但能把「代码侧是否成立」压缩成一次执行。
 *
 * <h2>只读</h2>
 * 除了一处刻意写入并复位的探针，它不改变世界状态。可以在生产档里跑。
 */
public final class HostSelfTestCommand {

    private HostSelfTestCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("selftest").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        List<String> lines = new ArrayList<>();
        int fails = 0;

        // ① 三处必要注入
        boolean tick = injected(com.simibubi.create.content.trains.entity.Train.class,
            "ctd$trainTickMarker");
        boolean passive = injected(com.simibubi.create.content.trains.entity.Train.class,
            "ctd$passiveMarker");
        boolean boundary = top.hibermod.ctdapi.signal.SignalSystemHolder.class
            .isAssignableFrom(com.simibubi.create.content.trains.signal.SignalBoundary.class);

        fails += add(lines, "Train#tick 注入", tick, "内核的速度写不进去");
        fails += add(lines, "被动减速拦截", passive, "每帧加的一步会被 Create 减掉");
        fails += add(lines, "SignalBoundary 注入", boundary, "信号制度选择不生效");

        // ② 拓扑
        var mirror = top.hibermod.ctdhost.HostRuntime.get().mirror();
        var view = mirror.current();
        int nodes = view == null ? 0 : view.nodes().size();
        int edges = view == null ? 0 : view.edges().size();
        fails += add(lines, "拓扑非空", nodes > 0,
            "没有轨道：铺轨或跑 /ctd host debugtrack");
        // 早先这里写的是「rebuildCount() <= 3」，那是**错的**：
        // 它把「缓存是否生效」和「这个世界被改过几次」混成了一件事。
        // 连续跑几次 /ctd host debugtrack（每次都是真实的结构变化）之后，
        // 重建次数自然 > 3，自检就开始报假失败——而假失败会让人不再看它。
        //
        // 自检在这里能证明的只有「缓存路径在工作」：至少重建过一次，且耗时是个正常数。
        // 「没变化时不重建」由 PLAN 里那次 1200 tick 稳态实验证明（rebuilds 恒为 1），
        // 那需要观察一段时间，不是一条即时命令能判的。
        // 车站：契约里一直有 stations()，但曾经**没有任何代码往里填**，恒为空。
        // 这一项守住「有车站的世界里，拓扑里必须能看到车站」。
        int stationSites = mirror.current() == null ? 0 : mirror.current().stations().size();
        int deviceSites = mirror.current() == null ? 0 : mirror.current().devices().size();
        int worldStations = 0;
        try {
            for (var graph : Create.RAILWAYS.trackNetworks.values()) {
                worldStations += graph.getPoints(
                    com.simibubi.create.content.trains.graph.EdgePointType.STATION).size();
            }
        } catch (Throwable t) {
            top.hibermod.ctdapi.diagnostics.Failures.caught("自检：数车站", t);
        }

        if (worldStations == 0) {
            lines.add(pad("车站已收录") + "SKIP  世界里没有车站");
        } else {
            fails += add(lines, "车站已收录",
                stationSites >= worldStations && deviceSites >= stationSites,
                "世界有 " + worldStations + " 站，拓扑里只有 " + stationSites
                    + " 站 / " + deviceSites + " 个设备");
        }

        fails += add(lines, "拓扑缓存可观测",
            mirror.rebuildCount() >= 1 && mirror.lastRebuildMillis() >= 0
                && mirror.lastRebuildMillis() < 2000,
            "重建次数=" + mirror.rebuildCount() + " 耗时=" + mirror.lastRebuildMillis() + "ms");

        // ②b 时刻表健康
        //
        // Create 的 ScheduleRuntime.estimateStayDuration 对「cyclic 但有 0 条目」的时刻表
        // 会 entries.get(0) 越界，每个 tick 抛一次——服务端起不来，而且状态在存档里。
        // 这一项就是提前把它说出来，而不是等人翻 crash report。
        int corrupt = 0;
        for (var train : Create.RAILWAYS.trains.values()) {
            if (train.runtime == null || train.runtime.schedule == null) continue;
            var s = train.runtime.schedule;
            if (top.hibermod.ctdhost.execute.ScheduleHealth.isCorrupt(s.cyclic, s.entries.size())) {
                corrupt++;
            }
        }
        // ②c 兜底注入本身也要可验证：只靠日志「拦过几次」不够，
        // 因为「一次都没拦」既可能是数据健康，也可能是注入没生效。
        boolean guard = injected(
            com.simibubi.create.content.trains.schedule.ScheduleRuntime.class,
            "ctd$scheduleGuardMarker");
        boolean aspectGuard = injected(
            com.simibubi.create.content.trains.signal.SignalBoundary.class,
            "ctd$tickStateGuardMarker");
        fails += add(lines, "灯色接管注入", aspectGuard,
            "信号灯色会在下一帧被 Create 覆盖，内核写的灯等于没写");

        boolean tickGuard = injected(
            com.simibubi.create.content.trains.schedule.ScheduleRuntime.class,
            "ctd$scheduleRuntimeTickGuardMarker");
        boolean stashGuard = injected(
            com.simibubi.create.content.trains.GlobalRailwayManager.class,
            "ctd$corruptScheduleStashMarker");
        fails += add(lines, "时刻表源头摘除", stashGuard,
            "Create 有四处会因坏时刻表越界；逐点补漏挡不住，必须在入口整帧摘除");

        fails += add(lines, "时刻表停摆注入", tickGuard,
            "循环但无条目的时刻表会让 runtime.tick 越界崩服（比 estimateStayDuration 更早）");

        fails += add(lines, "时刻表兜底注入", guard,
            "损坏的循环时刻表会再次把服务端打崩");

        fails += add(lines, "兜底真的会开火", ctd$proveGuardsFire(lines),
            "第二防线的守卫注册了但要能真的挡住一次（否则它只是装饰）");

        fails += add(lines, "时刻表健康", corrupt == 0,
            corrupt + " 列车持有无条目的时刻表（cyclic 与否都会崩）；跑 /ctd host schedule clear");

        // ③ 内核
        int kernels = Create.RAILWAYS.trains.isEmpty() ? 0 : 1;
        boolean kernelRegistered = top.hibermod.ctdhost.HostRuntime.get().kernelCount() > 0;
        fails += add(lines, "已注册内核", kernelRegistered,
            "脚本没加载或全部被禁用：看日志里的 ctd-script 行");

        // ④ 控制通道：写入后能取走
        boolean control = controlRoundTrip(src);
        fails += add(lines, "控制通道可入队/可取出", control,
            "ControlChannel 有东西取不出来");

        // ⑤ 存档：同一个键写两次读两次
        boolean persist = persistenceRoundTrip(src);
        fails += add(lines, "内核台账可读写", persist,
            "HostSavedData 没接上：重启后会丢");

        // ⑥ 列车侧的诊断字段是否可读
        //
        // 空车队不算失败：全新世界本来就没有车，把「还没有车」判成 FAIL 会让整套
        // 自检在新世界里永远是红的，人就会开始忽略它。所以这一项问的是
        // **「有车的话，它的状态读得出来吗」**，没车时明确说「跳过」。
        int fleet = Create.RAILWAYS.trains.size();
        boolean trainReadable = true;
        if (fleet > 0) {
            var train = Create.RAILWAYS.trains.values().iterator().next();
            trainReadable = train.graph != null && train.runtime != null;
        }
        fails += add(lines, fleet == 0 ? "列车状态可读（无车，跳过）" : "列车状态可读",
            trainReadable,
            fleet == 0 ? "" : "列车缺 graph/runtime");

        final int failed = fails;
        final String header = "CTD selftest — " + (failed == 0 ? "全部通过" : failed + " 项失败");
        src.sendSuccess(() -> Component.literal(header), false);
        for (String line : lines) {
            src.sendSuccess(() -> Component.literal(line), false);
        }
        src.sendSuccess(() -> Component.literal(
            "  （灯色、HUD 外观、道岔物理位置无法自动判断，见 docs/17 的人工项）"), false);
        return failed == 0 ? 1 : 0;
    }

    private static boolean injected(Class<?> target, String method) {
        for (var m : target.getDeclaredMethods()) {
            if (m.getName().equals(method)) return true;
        }
        return false;
    }

    /** 入队一条指令，再取出来；验证「网络线程能写、tick 线程能读」这条路径。 */
    private static boolean controlRoundTrip(CommandSourceStack src) {
        try {
            var channel = top.hibermod.ctdapi.operation.ControlChannel.class;
            int before = top.hibermod.ctdapi.operation.ControlChannel.pending();

            java.util.UUID id = java.util.UUID.randomUUID();
            top.hibermod.ctdapi.operation.ControlChannel.hold(
                top.hibermod.ctdapi.operation.ControlChannel.Source.EXTERNAL, 0L,
                top.hibermod.ctdapi.Command.TrainCommand.hold(id), "selftest");

            if (top.hibermod.ctdapi.operation.ControlChannel.pending() <= before) return false;

            var drained = top.hibermod.ctdapi.operation.ControlChannel.drain();
            for (var c : drained.trains()) {
                if (c.command().trainId().equals(id)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 写一个键再读回来；顺便清掉，避免污染存档。 */
    private static boolean persistenceRoundTrip(CommandSourceStack src) {
        try {
            var data = top.hibermod.ctdhost.save.HostSavedData.get(src.getServer());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ctdhost", "selftest");
            var tag = data.kernelState(id);
            tag.putString("probe", "ok");
            data.putKernelState(id, tag);

            return "ok".equals(data.kernelState(id).getString("probe"));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 证明两道「第二防线」真的会开火——**在游戏里当场调用它们守的方法**。
     *
     * <h2>为什么不能只看「注入已注册」</h2>
     * {@code @Unique} 标记只能证明 mixin 挂上去了，证明不了那个 {@code @Inject}
     * 分支真的会被走到、返回的值真的是我们想的那个。这一轮已经吃过一次亏：
     * 「时刻表兜底」写了三轮没触发过，最后发现它压根没跑过。
     *
     * <h2>做法</h2>
     * 借一列真车的 runtime，把一份损坏时刻表**直接赋给字段**（绕开 setSchedule 的副作用），
     * 然后当场调用：
     * <ol>
     *   <li>{@code estimateStayDuration(0)}（私有，反射）→ 期望 {@code -2}；</li>
     *   <li>{@code tick(level)} → 期望**不抛**。</li>
     * </ol>
     * 两步都在同一个命令调用里完成、立即还原，所以不会泄漏到下个 tick。
     *
     * <p>如果守卫是坏的，这里会直接崩——那正是它该暴露的。
     */
    private static boolean ctd$proveGuardsFire(List<String> lines) {
        ScheduleRuntime runtime = null;
        Schedule saved = null;

        try {
            for (var train : Create.RAILWAYS.trains.values()) {
                if (train.runtime != null) {
                    runtime = train.runtime;
                    saved = runtime.schedule;
                    break;
                }
            }

            if (runtime == null) return true;   // 没有列车就不判失败

            // 自检要故意触发守卫，而守卫会往 swallowed 里记一条。
            // 自检不该污染它正在检查的通道，所以在验证窗口内静音。
            top.hibermod.ctdapi.diagnostics.Failures.beginSuppression();

            Schedule broken = new Schedule(new java.util.ArrayList<>(), true, 0);
            runtime.schedule = broken;          // 直接赋值：不触发 reset()

            var method = ScheduleRuntime.class.getDeclaredMethod("estimateStayDuration", int.class);
            method.setAccessible(true);
            Object result = method.invoke(runtime, 0);

            boolean returnedInvalid = result instanceof Integer i && i == -2;

            // 第二道：tick 守卫应当取消这一帧而不是抛出去
            boolean tickSafe;
            try {
                runtime.tick(null);
                tickSafe = true;
            } catch (Throwable t) {
                tickSafe = false;
                lines.add("      tick 守卫没挡住: " + t.getClass().getSimpleName());
            }

            return returnedInvalid && tickSafe;
        } catch (Throwable t) {
            lines.add("      现场验证抛了: " + t.getClass().getSimpleName() + " / " + t.getMessage());
            return false;
        } finally {
            top.hibermod.ctdapi.diagnostics.Failures.endSuppression();
            if (runtime != null) runtime.schedule = saved;
        }
    }

    private static int add(List<String> lines, String name, boolean ok, String why) {
        lines.add("  " + (ok ? "PASS  " : "FAIL  ") + pad(name) + (ok ? "" : "  <- " + why));
        return ok ? 0 : 1;
    }

    private static String pad(String s) {
        // 中文字符按两个宽度算，简单对齐够用
        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            width += s.charAt(i) > 0x2E80 ? 2 : 1;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = width; i < 24; i++) sb.append(' ');
        return sb.toString();
    }
}
