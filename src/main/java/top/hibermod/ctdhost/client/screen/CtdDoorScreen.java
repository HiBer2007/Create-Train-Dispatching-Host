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

/** 扳手右键屏蔽门后的配置界面：车站号 / 站台号 / 门号 / 开合速度。 */
public class CtdDoorScreen extends Screen {

    private final BlockPos pos;
    private final String station;
    private final String platform;
    private final String doorId;
    private final float speed;

    private EditBox stationBox;
    private EditBox platformBox;
    private EditBox doorBox;
    private EditBox speedBox;

    public CtdDoorScreen(BlockPos pos, String station, String platform, String doorId, float speed) {
        super(Component.translatable("gui.ctdhost.psd_door_config"));
        this.pos = pos;
        this.station = station == null ? "" : station;
        this.platform = platform == null ? "" : platform;
        this.doorId = doorId == null ? "" : doorId;
        this.speed = speed <= 0 ? 1f : speed;
    }

    @Override
    protected void init() {
        int cx = this.width / 2 - 100;
        int y = this.height / 2 - 60;

        stationBox = addBox(cx, y, station);
        platformBox = addBox(cx, y + 26, platform);
        doorBox = addBox(cx, y + 52, doorId);
        speedBox = addBox(cx, y + 78, String.valueOf(speed));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> save())
            .bounds(cx, y + 110, 200, 20).build());
    }

    private EditBox addBox(int x, int y, String value) {
        EditBox box = new EditBox(this.font, x, y, 200, 18, Component.empty());
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
        PacketDistributor.sendToServer(new CtdDoorConfigPayload(
            pos, stationBox.getValue().trim(), platformBox.getValue().trim(),
            doorBox.getValue().trim(), sp));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2 - 100;
        int y = this.height / 2 - 60;
        graphics.drawString(this.font, Component.translatable("gui.ctdhost.station"), cx, y - 12, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.ctdhost.platform"), cx, y + 14, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.ctdhost.psd_door"), cx, y + 40, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.ctdhost.psd_speed"), cx, y + 66, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
