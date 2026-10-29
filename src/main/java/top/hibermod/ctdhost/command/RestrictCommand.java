package top.hibermod.ctdhost.command;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.status.Restrictions;
import top.hibermod.ctdhost.execute.RestrictionsApplier;

/**
 * {@code /ctd restrict ...} —— 发布与管理线路限制（限速 / 鸣笛 / 封闭）。
 *
 * <h2>为什么要给管理员一个命令</h2>
 * 限速是线路属性，不该只能由脚本写。施工、临时限速这些事，
 * 管理员应当能在游戏里直接加，且**即使一个内核都没装也生效**。
 *
 * <h2>边的指定方式</h2>
 * <ul>
 *   <li>{@code here} —— 最近一列车当前所在的边（调试/演示最省事）；</li>
 *   <li>{@code <edgeId>} —— {@code Rail.EdgeId.encode()} 的字符串形式。</li>
 * </ul>
 */
public final class RestrictCommand {

    private RestrictCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("restrict")
            .executes(RestrictCommand::list)
            .then(Commands.literal("list").executes(RestrictCommand::list))
            .then(Commands.literal("clear").executes(RestrictCommand::clear))
            .then(Commands.literal("speed")
                .then(Commands.argument("kmh", DoubleArgumentType.doubleArg(1, 400))
                    .then(Commands.argument("edge", StringArgumentType.word())
                        .executes(ctx -> publish(ctx, Restrictions.Kind.SPEED_LIMIT,
                            DoubleArgumentType.getDouble(ctx, "kmh"),
                            StringArgumentType.getString(ctx, "edge"), 0))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                            .executes(ctx -> publish(ctx, Restrictions.Kind.SPEED_LIMIT,
                                DoubleArgumentType.getDouble(ctx, "kmh"),
                                StringArgumentType.getString(ctx, "edge"),
                                IntegerArgumentType.getInteger(ctx, "seconds")))))))
            .then(Commands.literal("horn")
                .then(Commands.argument("edge", StringArgumentType.word())
                    .executes(ctx -> publish(ctx, Restrictions.Kind.HORN, 0,
                        StringArgumentType.getString(ctx, "edge"), 0))));
    }

    private static int publish(CommandContext<CommandSourceStack> ctx, Restrictions.Kind kind,
                               double kmh, String edgeArg, int seconds) {
        Rail.EdgeId edge = resolveEdge(edgeArg);
        if (edge == null) {
            ctx.getSource().sendFailure(Component.literal(
                "no such edge: " + edgeArg + " (use 'here' or a Rail.EdgeId string)"));
            return 0;
        }

        long now = ctx.getSource().getLevel().getGameTime();
        long until = seconds > 0 ? now + seconds * 20L : Long.MAX_VALUE;

        Restrictions.Restriction r = new Restrictions.Restriction(
            UUID.randomUUID(),
            kind,
            edge,
            0,
            Double.MAX_VALUE,
            RestrictionsApplier.toUnits(kmh),
            now,
            until,
            ResourceLocation.fromNamespaceAndPath("ctd", "admin"),
            Component.literal(kind + (kmh > 0 ? " " + (int) kmh + "km/h" : "") + " @ " + edgeArg),
            0L);

        Restrictions.publish(r);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "published " + kind + (kmh > 0 ? " " + (int) kmh + " km/h" : "")
                + " on " + edge.encode()
                + (seconds > 0 ? " for " + seconds + "s" : "")), false);
        return 1;
    }

    private static Rail.EdgeId resolveEdge(String raw) {
        if (raw != null && raw.equalsIgnoreCase("here")) {
            for (Train t : Create.RAILWAYS.trains.values()) {
                Rail.EdgeId id = RestrictionsApplier.edgeOf(t);
                if (id != null) return id;
            }
            return null;
        }
        return Rail.EdgeId.tryDecode(raw);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        long now = ctx.getSource().getLevel().getGameTime();
        List<Restrictions.Restriction> active = Restrictions.active(now);

        ctx.getSource().sendSuccess(() -> Component.literal(
            "active restrictions: " + active.size()), false);
        for (Restrictions.Restriction r : active) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + r.kind()
                + (r.hasSpeedLimit()
                    ? " " + (int) RestrictionsApplier.toKmh(r.speedLimit()) + "km/h" : "")
                + " on " + r.edge().encode()
                + (r.validUntil() == Long.MAX_VALUE ? " (no expiry)" : "")), false);
        }

        // 顺带报一下每列车当前被限到多少，便于确认「真的生效了」
        for (Train t : Create.RAILWAYS.trains.values()) {
            Rail.EdgeId edge = RestrictionsApplier.edgeOf(t);
            double limit = RestrictionsApplier.speedLimitOf(edge, now);
            if (limit == Double.MAX_VALUE) continue;
            ctx.getSource().sendSuccess(() -> Component.literal("  train "
                + (t.name == null ? t.id.toString() : t.name.getString())
                + " limited to " + (int) RestrictionsApplier.toKmh(limit) + " km/h"), false);
        }
        return active.size();
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int before = Restrictions.all().size();
        Restrictions.clear();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "cleared " + before + " restriction(s)"), false);
        return before;
    }
}
