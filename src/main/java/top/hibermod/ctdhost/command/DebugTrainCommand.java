package top.hibermod.ctdhost.command;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TrainIconType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /ctd host debugtrain}：在调试轨道上**凭空造一列车**。
 *
 * <h2>为什么用反射</h2>
 * {@code Train} 的构造器是 private —— 正常路径是从方块组装（`StationBlockEntity`）。
 * 调试时没有方块，只能反射。这个做法刻意只出现在调试命令里：
 * 生产代码调私有构造器，等于把自己绑死在某个版本的内部结构上。
 *
 * <h2>它能验证什么</h2>
 * <ul>
 *   <li>{@code TelemetrySampler}：`trains` 从 0 变 1，速度/位置能采出来；</li>
 *   <li>{@code UnitIndex}：`Train.name` → 车辆编号 → 可寻址；</li>
 *   <li>{@code SpeedApplier}：给 0 授权，`targetSpeed` 应被压到 0。</li>
 * </ul>
 *
 * <p>它**不能**验证列车真的会在世界里跑起来——没有车厢实体、没有碰撞、
 * 没有客户端渲染。那需要真实组装的列车。
 */
public final class DebugTrainCommand {

    private DebugTrainCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debugtrain").executes(ctx -> run(ctx.getSource()))
            // 先把原生目标速度抬到 1.0，再下一条 0.4 的指令：
            // 期望结果 0.4 —— 验证「只能收紧、不能放宽」
            .then(Commands.literal("drive").executes(ctx -> drive(ctx.getSource())));
    }

    private static int drive(CommandSourceStack src) {
        var trains = Create.RAILWAYS.trains;
        if (trains.isEmpty()) {
            src.sendFailure(Component.literal("no train — run /ctd host debugtrain first"));
            return 0;
        }
        long now = src.getLevel().getGameTime();
        for (var train : trains.values()) {
            train.targetSpeed = 1.0;                     // 模拟「原生想跑满」
            top.hibermod.ctdapi.operation.ControlChannel.hold(
                top.hibermod.ctdapi.operation.ControlChannel.Source.EXTERNAL, now,
                top.hibermod.ctdapi.Command.TrainCommand.proceed(train.id, 100, 0.4),
                "debug drive");
        }
        src.sendSuccess(() -> Component.literal(
            "queued proceed(cap=0.4) for " + trains.size() + " train(s); "
            + "native target was forced to 1.0 — expect target=0.400 after the next tick"), false);
        return trains.size();
    }

    private static int run(CommandSourceStack src) {
        try {
            ServerLevel level = src.getLevel();

            TrackGraph graph = null;
            TrackEdge edge = null;
            for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
                for (TrackNode node : g.getNodes().stream()
                        .map(g::locateNode).filter(java.util.Objects::nonNull).toList()) {
                    var conns = g.getConnectionsFrom(node);
                    if (conns != null && !conns.isEmpty()) {
                        graph = g;
                        edge = conns.values().iterator().next();
                        break;
                    }
                }
                if (edge != null) break;
            }
            if (edge == null) {
                src.sendFailure(Component.literal("no track edge found — run /ctd host debugtrack first"));
                return 0;
            }

            TravellingPoint front = new TravellingPoint(edge.node1, edge.node2, edge, 0.2, false);
            TravellingPoint back = new TravellingPoint(edge.node2, edge.node1, edge, 0.8, false);

            // 直接从方块注册表里找转向架：AllBlocks 的返回类型是 Registrate 的
            // BlockEntry，而 Registrate 不在本模组的编译路径上。
            com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<?> bogeyBlock = null;
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (block instanceof com.simibubi.create.content.trains.bogey.AbstractBogeyBlock<?> b) {
                    bogeyBlock = b;
                    break;
                }
            }
            if (bogeyBlock == null) {
                src.sendFailure(Component.literal("no bogey block registered"));
                return 0;
            }
            CarriageBogey bogey = new CarriageBogey(bogeyBlock, false, new CompoundTag(), front, back);
            Carriage carriage = new Carriage(bogey, null, 0);

            List<Carriage> carriages = new ArrayList<>();
            carriages.add(carriage);
            List<Integer> spacing = new ArrayList<>();

            TrainIconType icon = TrainIconType.REGISTRY.values().stream().findFirst().orElse(null);

            Constructor<Train> ctor = Train.class.getDeclaredConstructor(
                UUID.class, UUID.class, List.class, List.class,
                boolean.class, Component.class, TrainIconType.class, int.class);
            ctor.setAccessible(true);

            final Train train = ctor.newInstance(
                UUID.randomUUID(), null, carriages, spacing, false,
                Component.literal("CTD-DIAG"), icon, 0);
            train.graph = graph;

            Create.RAILWAYS.trains.put(train.id, train);

            // graph 在循环里赋值，不是 effectively final，先拼好字符串再进 lambda
            final String createdMsg = "debug train created: id=" + train.id
                + " name=CTD-DIAG graph=" + (graph == null ? "?" : graph.id);
            src.sendSuccess(() -> Component.literal(createdMsg), false);
            src.sendSuccess(() -> Component.literal(
                "  /ctd host status should now show trains, units and a resolved assignment"), false);
            return 1;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("debug train failed: " + t));
            return 0;
        }
    }
}
