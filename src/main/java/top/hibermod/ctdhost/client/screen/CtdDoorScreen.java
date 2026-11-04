package top.hibermod.ctdhost.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import top.hibermod.ctdhost.network.CtdDoorConfigPayload;

/**
 * 扳手右键屏蔽门后的配置界面。
 *
 * <p>版式照机械动力 / CRN 的设置窗：左侧标签、右侧输入框、底部按钮，
 * 背景是一块半透明面板，标签与输入框**不在同一层叠放**，避免文字被输入框盖住。
 */
public class CtdDoorScreen extends Screen {

    private static final int FIELD_W = 120;
    private static final int FIELD_H = 18;
    private static final int ROW_GAP = 26;
    private static final int LABEL_W = 90;

    private final BlockPos pos;
    private final String station;
    private final String platform;
    private final String doorId;
    private final float speed;
    private final float doorHeight;

    private EditBox stationBox;
    private EditBox platformBox;
    private EditBox doorBox;
    private EditBox speedBox;
    private EditBox heightBox;

    private int panelX;
    private int panelY;
    private int panelW = 250;
    private int panelH = 190;

    public CtdDoorScreen(BlockPos pos, String station, String platform, String doorId,
                         float speed, float height) {
        super(Component.translatable("gui.ctdhost.psd_door_config"));
        this.pos = pos;
        this.station = station == null ? "" : station;
        this.platform = platform == null ? "" : platform;
        this.doorId = doorId == null ? "" : doorId;
        this.speed = speed <= 0 ? 1f : speed;
        this.doorHeight = height <= 0 ? 2f : height;
    }

    @Override
    protected void init() {
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int fieldX = panelX + LABEL_W + 12;
        int rowY = panelY + 28;

        stationBox = addBox(fieldX, rowY, station);
        platformBox = addBox(fieldX, rowY + ROW_GAP, platform);
        doorBox = addBox(fieldX, rowY + ROW_GAP * 2, doorId);
        speedBox = addBox(fieldX, rowY + ROW_GAP * 3, String.valueOf(speed));
        heightBox = addBox(fieldX, rowY + ROW_GAP * 4, String.valueOf(doorHeight));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save())
            .bounds(panelX + (panelW - 120) / 2, panelY + panelH - 30, 120, 20).build());
    }

    private EditBox addBox(int x, int y, String value) {
        EditBox box = new EditBox(this.font, x, y, FIELD_W, FIELD_H, Component.empty());
        box.setValue(value);
        box.setMaxLength(64);
        addRenderableWidget(box);
        return box;
    }

    private void save() {
        float sp;
        try {
            sp = Float.parseFloat(speedBox.getValue().trim());
        } catch (NumberFormatException e) {
            sp = 1f;
        }
        float h;
        try {
            h = Float.parseFloat(heightBox.getValue().trim());
        } catch (NumberFormatException e) {
            h = 2f;
        }
        PacketDistributor.sendToServer(new CtdDoorConfigPayload(
            pos, stationBox.getValue().trim(), platformBox.getValue().trim(),
            doorBox.getValue().trim(), sp, h));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 先让 Screen 自己画背景与控件；之后画的才不会被第二次 renderBackground 盖掉
        super.render(graphics, mouseX, mouseY, partialTick);

        // 面板
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC8101010);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF808080);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF808080);

        // 标题
        graphics.drawString(this.font, this.title, panelX + 10, panelY + 8, 0xFFE0E0E0, false);

        // 标签：画在输入框左侧，与输入框同一行居中
        int rowY = panelY + 28;
        label(graphics, "gui.ctdhost.station", rowY);
        label(graphics, "gui.ctdhost.platform", rowY + ROW_GAP);
        label(graphics, "gui.ctdhost.psd_door", rowY + ROW_GAP * 2);
        label(graphics, "gui.ctdhost.psd_speed", rowY + ROW_GAP * 3);
        label(graphics, "gui.ctdhost.psd_height", rowY + ROW_GAP * 4);
    }

    private void label(GuiGraphics graphics, String key, int fieldY) {
        Component text = Component.translatable(key);
        int textY = fieldY + (FIELD_H - this.font.lineHeight) / 2;
        graphics.drawString(this.font, text, panelX + 10, textY, 0xFFB0B0B0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
