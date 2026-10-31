package top.hibermod.ctdhost.command;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackGraph;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.Rail;

/**
 * {@code /ctd host switches} —— 列出**当前被锁住的岔口**：锁在哪条股道、谁锁的。
 *
 * <h2>它补的是什么</h2>
 * `/ctd host status` 只有 {@code switches: logical=N}。N 是几，看不出
 * 「哪个岔、锁向哪边、谁锁的」——而进路排错时最先要问的恰恰是这三样。
 *
 * <p>{@code SwitchApplier} 内部本来就有 {@code locks()} 与 {@code holderOf(...)}，
 * 只是没有出口。这条命令把它们摊开，**只读**，不改任何锁。
 *
 * <h2>它同时暴露两件容易混的事</h2>
 * <ul>
 *   <li><b>逻辑锁</b>：我们的锁表。就算没装汽鸣铁道也存在，能挡住其它内核。</li>
 *   <li><b>物理扳动</b>：装了汽鸣铁道才会真的动岔机。状态行里分开写。</li>
 * </ul>
 */
public final class SwitchesCommand {

    private SwitchesCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("switches").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        var host = top.hibermod.ctdhost.HostRuntime.get();

        if (host.applier() == null) {
            src.sendFailure(Component.literal("applier 未初始化"));
            return 0;
        }

        var applier = host.applier().switches();
        Map<Rail.NodeId, Rail.EdgeId> locks = applier.locks();

        final String physical = applier.physicalAvailable()
            ? "物理扳动：可用（汽鸣铁道在场）"
            : "物理扳动：不可用（未装汽鸣铁道，退化为逻辑锁）";

        src.sendSuccess(() -> Component.literal(physical), false);

        if (locks.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                "当前没有被锁住的岔口（switches: logical=0）"), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("被锁住的岔口：" + locks.size() + " 个"), false);

        List<Map.Entry<Rail.NodeId, Rail.EdgeId>> entries = List.copyOf(locks.entrySet());

        for (var entry : entries) {
            Rail.NodeId node = entry.getKey();
            Rail.EdgeId branch = entry.getValue();
            long holder = applier.holderOf(node);

            final String line = "  node=" + node
                + "  ->  " + branch
                + "  holder=" + describeHolder(holder);

            src.sendSuccess(() -> Component.literal(line), false);
        }

        return locks.size();
    }

    /** holder 是「谁锁的」：0 表示调度内核（脚本），其它值原样显示。 */
    private static String describeHolder(long holder) {
        if (holder == 0) return "kernel";
        return String.valueOf(holder);
    }
}
