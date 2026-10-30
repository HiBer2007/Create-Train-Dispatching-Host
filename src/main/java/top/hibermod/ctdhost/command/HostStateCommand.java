package top.hibermod.ctdhost.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 内核台账的读写探针。
 *
 * <h2>它为什么存在</h2>
 * {@code HostSavedData} 早先写好了 {@code save}/{@code load}，却**从来没有被实例化**：
 * {@code KernelEnv#state()} 返回临时 {@code CompoundTag}，{@code markStateDirty()} 是空方法。
 * 于是「重启之后内核还在原地」从未成立，而且没有任何报错——这种缺口只能靠
 * 「写进去、重启、读出来」来发现。
 *
 * <p>用法：
 * <pre>
 *   /ctd host state put foo bar     → 写入（并标脏）
 *   /ctd host state show            → 读出来
 *   重启服务器
 *   /ctd host state show            → 还在，就说明真的存下来了
 * </pre>
 */
public final class HostStateCommand {

    /** 探针用的内核 id，与任何真实内核都不冲突。 */
    private static final ResourceLocation PROBE = ResourceLocation.fromNamespaceAndPath("ctdhost", "probe");

    private HostStateCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("state")
            .then(Commands.literal("show").executes(ctx -> show(ctx.getSource())))
            .then(Commands.literal("put")
                .then(Commands.argument("key", StringArgumentType.word())
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> put(ctx.getSource(),
                            StringArgumentType.getString(ctx, "key"),
                            StringArgumentType.getString(ctx, "value"))))));
    }

    private static int put(CommandSourceStack src, String key, String value) {
        try {
            var data = top.hibermod.ctdhost.save.HostSavedData.get(src.getServer());
            CompoundTag tag = data.kernelState(PROBE);
            tag.putString(key, value);
            data.putKernelState(PROBE, tag);
            data.setDirty();
            src.sendSuccess(() -> Component.literal(
                "state[" + PROBE + "]." + key + " = " + value + " (marked dirty)"), false);
            return 1;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("state put failed: " + t));
            return 0;
        }
    }

    private static int show(CommandSourceStack src) {
        try {
            var data = top.hibermod.ctdhost.save.HostSavedData.get(src.getServer());
            CompoundTag tag = data.kernelState(PROBE);

            if (tag.isEmpty()) {
                src.sendSuccess(() -> Component.literal(
                    "state[" + PROBE + "] is empty — put something, restart, then show again"), false);
                return 0;
            }

            StringBuilder sb = new StringBuilder("state[" + PROBE + "] ");
            for (String key : tag.getAllKeys()) {
                sb.append(key).append('=').append(tag.getString(key)).append(' ');
            }
            final String line = sb.toString().trim();
            src.sendSuccess(() -> Component.literal(line), false);
            return tag.getAllKeys().size();
        } catch (Throwable t) {
            src.sendFailure(Component.literal("state show failed: " + t));
            return 0;
        }
    }
}
