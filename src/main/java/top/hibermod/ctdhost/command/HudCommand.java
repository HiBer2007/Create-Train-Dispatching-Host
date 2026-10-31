package top.hibermod.ctdhost.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.hibermod.ctdapi.hud.Hud;
import top.hibermod.ctdhost.execute.HudStore;

/**
 * {@code /ctd host hud} —— 打印**内核当前推给 HUD 的数据**。
 *
 * <h2>为什么需要它</h2>
 * {@code docs/17} §9 里有一条人工项：「HUD 外观是否正确」。但人眼只能判断
 * 「屏幕上画的东西像不像」，判断不了「画的是不是内核说的那份数据」。
 *
 * <p>这条命令把「内核说了什么」摊出来，于是人工核对变成两件事：
 * <ol>
 *   <li>看这个输出——期望的内容是什么；</li>
 *   <li>看屏幕——画出来的是不是这个。</li>
 * </ol>
 * 两者不一致时，问题就被定位到「渲染」而不是「数据」，反之亦然。
 *
 * <p>没有这条命令之前，这两件事是混在一起的：屏幕不对，你不知道该查内核还是查渲染。
 */
public final class HudCommand {

    private HudCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("hud").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack src) {
        var snapshot = HudStore.snapshot();

        if (snapshot.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                "数据推送路径当前没有内容（脚本画布 HUD 是另一条路径，不受影响）"), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal(
            "内核推来的 HUD：" + snapshot.size() + " 个面板，t=" + HudStore.gameTime()), false);

        for (var panel : snapshot) {
            src.sendSuccess(() -> Component.literal(
                "  [" + panel.panelId() + "] " + panel.rows().size() + " 行"), false);

            if (panel.subtitle() != null) {
                final String sub = panel.subtitle().getString();
                src.sendSuccess(() -> Component.literal("    subtitle: " + sub), false);
            }

            for (Hud.HudRow row : panel.rows()) {
                final String line = "    " + row.label().getString()
                    + " = " + row.value().getString()
                    + "  (" + row.severity() + ")";
                src.sendSuccess(() -> Component.literal(line), false);
            }
        }

        return snapshot.size();
    }
}
