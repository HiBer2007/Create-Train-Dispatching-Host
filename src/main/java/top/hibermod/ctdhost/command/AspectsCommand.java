package top.hibermod.ctdhost.command;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import com.simibubi.create.content.trains.signal.SignalBoundary;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdhost.execute.SignalApplier;

/**
 * {@code /ctd host aspects} —— **实时**读回每架信号机两面的灯色。
 *
 * <h2>为什么需要这条命令</h2>
 * 拓扑镜像（{@code ctx.signals()}）是**重建时的快照**：它的 {@code aspectId}
 * 只在图结构变化时更新。拿快照去验证「刚写进去的灯色」必然失败——
 * 镜像里永远是上次重建时的样子，看起来像「写入没生效」。
 *
 * <p>这条命令直接读 {@code SignalBoundary.cachedStates}（Create 用来显示的字段），
 * 所以它是判断写入是否落地的可复跑手段。
 *
 * <h2>它证明什么、不证明什么</h2>
 * <ul>
 *   <li>证明：{@code AspectCommand} → {@code SignalApplier} → {@code cachedStates} 走通了；</li>
 *   <li>不证明：灯**看起来**对不对（渲染与贴图，只能人眼）。</li>
 * </ul>
 */
public final class AspectsCommand {

    private AspectsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("aspects").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        int shown = 0;
        Set<UUID> seenEdges = new HashSet<>();

        for (Map.Entry<UUID, TrackGraph> entry : Create.RAILWAYS.trackNetworks.entrySet()) {
            TrackGraph graph = entry.getValue();

            for (var nodeLocation : graph.getNodes()) {
                var node = graph.locateNode(nodeLocation);
                if (node == null) continue;

                Map<?, TrackEdge> connections = graph.getConnectionsFrom(node);
                if (connections == null) continue;

                for (TrackEdge edge : connections.values()) {
                    if (edge == null) continue;
                    if (!seenEdges.add(java.util.UUID.nameUUIDFromBytes(
                            (String.valueOf(nodeLocation) + edge.getEdgeData().hashCode()).getBytes()))) {
                        continue;
                    }

                    for (TrackEdgePoint point : edge.getEdgeData().getPoints()) {
                        if (!(point instanceof SignalBoundary boundary)) continue;

                        SignalState first = SignalApplier.readState(boundary, true);
                        SignalState second = SignalApplier.readState(boundary, false);

                        final String line = "  " + shortId(boundary.getId())
                            + "  FIRST=" + first + "  SECOND=" + second;

                        src.sendSuccess(() -> Component.literal(line), false);
                        shown++;
                    }
                }
            }
        }

        if (shown == 0) {
            src.sendSuccess(() -> Component.literal("no signals in the world"), false);
        }

        final int count = shown;
        src.sendSuccess(() -> Component.literal(
            count + " signal(s); read live from cachedStates, not from the topology snapshot"),
            false);

        return shown;
    }

    private static String shortId(UUID id) {
        String s = String.valueOf(id);
        return s.length() > 8 ? s.substring(0, 8) : s;
    }
}
