package top.hibermod.ctdhost.command;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainIconType;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /ctd host debugtrain}：在调试轨道上**凭空造一列车**。
 *
 * <h2>它能验证什么</h2>
 * 遥测采样（{@code Train → TrainState}）、车辆编号索引、时刻表写入、速度钳位。
 * 这些都不需要列车真的动。
 *
 * <h2>它**不能**验证什么</h2>
 * <b>列车跑起来。</b>实测过：如果把列车加进 {@code waitingTrains} 让它参与物理 tick，
 * 它很快会被 {@code Train.tick} 判为 {@code invalid}，然后由 {@code tickTrains}
 * 从 {@code trains} 里移除——整列车直接消失。
 * 原因是它没有车厢实体，撑不过一轮物理。
 *
 * <p>所以这里**刻意不**把它加入那两个列表：调试列车只用于静态观测。
 * 「列车真的按时刻表跑」必须用真实组装的列车验，这是环境边界，不是实现偷懒。
 *
 * <h2>为什么用反射</h2>
 * {@code Train} 的构造器是 private（正常路径从方块组装）。反射刻意只出现在调试命令里：
 * 生产代码调私有构造器等于把自己绑死在某个版本的内部结构上。
 */
public final class DebugTrainCommand {

    private DebugTrainCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debugtrain")
            .executes(ctx -> create(ctx.getSource()))
            .then(Commands.literal("drive").executes(ctx -> drive(ctx.getSource())))
            .then(Commands.literal("push").executes(ctx -> push(ctx.getSource())));
    }

    // ── 造车 ──────────────────────────────────────────────────────────────

    private static int create(CommandSourceStack src) {
        try {
            ServerLevel level = src.getLevel();

            TrackGraph graph = null;
            TrackEdge edge = null;
            outer:
            for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
                for (var loc : g.getNodes()) {
                    TrackNode node = g.locateNode(loc);
                    if (node == null) continue;
                    var conns = g.getConnectionsFrom(node);
                    if (conns != null && !conns.isEmpty()) {
                        graph = g;
                        edge = conns.values().iterator().next();
                        break outer;
                    }
                }
            }
            if (edge == null) {
                src.sendFailure(Component.literal(
                    "no track edge found — run /ctd host debugtrack first"));
                return 0;
            }

            AbstractBogeyBlock<?> bogeyBlock = null;
            for (var block : BuiltInRegistries.BLOCK) {
                if (block instanceof AbstractBogeyBlock<?> b) {
                    bogeyBlock = b;
                    break;
                }
            }
            if (bogeyBlock == null) {
                src.sendFailure(Component.literal("no bogey block registered"));
                return 0;
            }

            TravellingPoint front = new TravellingPoint(edge.node1, edge.node2, edge, 0.2, false);
            TravellingPoint back = new TravellingPoint(edge.node2, edge.node1, edge, 0.8, false);
            CarriageBogey bogey = new CarriageBogey(bogeyBlock, false, new CompoundTag(), front, back);
            Carriage carriage = new Carriage(bogey, null, 0);

            // 关键：车厢必须能造出自己的实体，否则 Train.tick 会把它判成 invalid。
            // CarriageEntityHandler.createEntity 里 EntityType.create(空 NBT) 返回 null，
            // 紧接着就是 train.invalid = true —— 列车随即被 tickTrains 从注册表移除。
            // 这里用真实实体存一份 NBT 出来，让 EntityType.create 能造出
            // CarriageContraptionEntity。
            CompoundTag entityTag = new CompoundTag();
            try {
                EntityType<?> cceType = BuiltInRegistries.ENTITY_TYPE.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "create", "carriage_contraption"));
                var cce = new com.simibubi.create.content.trains.entity.CarriageContraptionEntity(
                    cceType, level);
                cce.saveWithoutId(entityTag);

                // 只有实体 id 不够：读档时 CarriageContraptionEntity 会调
                // Contraption.readNBT(label)，"Contraption" 缺失直接 NPE。
                // 这里放一份**空车厢构造**：没有方块，但结构合法。
                var contraption = new com.simibubi.create.content.trains.entity.CarriageContraption(
                    net.minecraft.core.Direction.UP);
                entityTag.put("Contraption", contraption.writeNBT(level.registryAccess(), true));
            } catch (Throwable ignored) {
                // 造不出来就退化成静态列车，不影响其它验证
            }
            entityTag.putString("id", "create:carriage_contraption");

            var serialisedField = Carriage.class.getDeclaredField("serialisedEntity");
            serialisedField.setAccessible(true);
            serialisedField.set(carriage, entityTag);

            List<Carriage> carriages = new ArrayList<>();
            carriages.add(carriage);
            List<Integer> spacing = new ArrayList<>();

            TrainIconType icon = TrainIconType.REGISTRY.values().stream().findFirst().orElse(null);

            Constructor<Train> ctor = Train.class.getDeclaredConstructor(
                UUID.class, UUID.class, List.class, List.class,
                boolean.class, Component.class, TrainIconType.class, int.class);
            ctor.setAccessible(true);

            Train train = ctor.newInstance(
                UUID.randomUUID(), null, carriages, spacing, false,
                Component.literal("CTD-DIAG"), icon, 0);
            train.graph = graph;

            Create.RAILWAYS.trains.put(train.id, train);

            // **刻意不加入 waitingTrains / movingTrains。**
            //
            // 试过两次，都是从真实源码里找出的确切原因：
            //   ① 空 serialisedEntity → CarriageEntityHandler.createEntity 里
            //      EntityType.create 返回 null → train.invalid = true → 被移除；
            //   ② 补上实体 NBT 与空 contraption → 列车确实 tick 了，但紧接着
            //      NPE: Contraption.readNBT(...) because "contraption" is null，
            //      **整个服务端崩掉**。
            //
            // 一辆能被 tick 的列车需要一个真正从方块组装出来的车厢构造，
            // 那是玩家放置动作的产物，程序化造不出来。
            // 所以这个调试列车只做静态观测，不参与物理。
            final String message = "debug train created: id=" + train.id
                + " name=CTD-DIAG graph=" + graph.id + " (static)";
            src.sendSuccess(() -> Component.literal(message), false);
            src.sendSuccess(() -> Component.literal(
                "  static observation only — see the comment for why it is not ticked"), false);
            return 1;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("debug train failed: " + t));
            return 0;
        }
    }

    // ── 观测用：让钳位规则可验 ────────────────────────────────────────────

    /** 把原生目标速度抬到 1.0，同时下发 cap=0.4 —— 期望最终 0.400。 */
    private static int drive(CommandSourceStack src) {
        var trains = Create.RAILWAYS.trains;
        if (trains.isEmpty()) {
            src.sendFailure(Component.literal("no train — run /ctd host debugtrain first"));
            return 0;
        }
        long now = src.getLevel().getGameTime();
        for (var train : trains.values()) {
            train.targetSpeed = 1.0;
            top.hibermod.ctdapi.operation.ControlChannel.hold(
                top.hibermod.ctdapi.operation.ControlChannel.Source.EXTERNAL, now,
                top.hibermod.ctdapi.Command.TrainCommand.proceed(train.id, 100, 0.4),
                "debug drive");
        }
        src.sendSuccess(() -> Component.literal(
            "queued proceed(cap=0.4); native target forced to 1.0 — expect target=0.400"), false);
        return trains.size();
    }

    /** 给一个初速度，用于观察列车是否参与物理 tick。 */
    private static int push(CommandSourceStack src) {
        var trains = Create.RAILWAYS.trains;
        if (trains.isEmpty()) {
            src.sendFailure(Component.literal("no train — run /ctd host debugtrain first"));
            return 0;
        }
        for (var train : trains.values()) {
            train.speed = 0.3;
            train.targetSpeed = 0.3;
        }
        src.sendSuccess(() -> Component.literal(
            "set speed=0.300 — a ticking train would decay this via passive slowdown"), false);
        return trains.size();
    }
}
