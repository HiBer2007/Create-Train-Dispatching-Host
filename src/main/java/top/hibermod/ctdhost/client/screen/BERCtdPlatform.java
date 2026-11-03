package top.hibermod.ctdhost.client.screen;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel.EScrollMode;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.Pair;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 站台 PIDS。**手动排版**：只针对五种格数分别写死坐标与字号。
 *
 * <table>
 *   <tr><th>格数</th><th>用途</th><th>版式</th></tr>
 *   <tr><td>3x2</td><td>普通站台悬挂屏</td><td>方向条 + 三趟 + 提示</td></tr>
 *   <tr><td>2x2</td><td>小尺寸悬挂屏</td><td>方向条 + 两趟</td></tr>
 *   <tr><td>4x1</td><td>大尺寸屏蔽门上屏</td><td>方向条 + 一趟</td></tr>
 *   <tr><td>3x1</td><td>中尺寸屏蔽门上屏</td><td>方向条 + 一趟（更小）</td></tr>
 *   <tr><td>2x1</td><td>小尺寸屏蔽门上屏</td><td>仅一趟倒计时</td></tr>
 * </table>
 *
 * <p>坐标是**基线**（{@code BERLabel} 的字从 y 向上长，占 9×scale 高），
 * 字号直接写死，不参与任何自动缩放。
 */
public class BERCtdPlatform implements AbstractAdvancedDisplayRenderer<CtdPlatformSettings> {

    private final BERLabel head = new BERLabel();
    private final BERLabel[] rows = new BERLabel[3];
    private final BERLabel footer = new BERLabel();

    private static final String[] ROW_NAMES = {"本趟", "下一趟", "第三趟"};

    public BERCtdPlatform() {
        head.horizontalAlign.set(ETextAlignment.LEFT);
        head.color.set(DLColor.fromInt(0xFFECEFF1));
        head.horizontalScrollMode.set(EScrollMode.NEVER);
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new BERLabel();
            rows[i].horizontalAlign.set(ETextAlignment.LEFT);
            rows[i].horizontalScrollMode.set(EScrollMode.NEVER);
        }
        footer.horizontalAlign.set(ETextAlignment.LEFT);
        footer.color.set(DLColor.fromInt(0xFF9E9E9E));
        footer.horizontalScrollMode.set(EScrollMode.ALWAYS);
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics, float partialTicks,
                       AdvancedDisplayRenderInstance parent, int light, boolean backSide) {
        top.hibermod.ctdhost.client.CtdHeadRenderers.ensureRegistered();
        head.render(graphics, light);
        for (BERLabel row : rows) {
            if (row.text.get() != null) row.render(graphics, light);
        }
        footer.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity, AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {
        if (!blockEntity.isController()) return;

        CtdPlatformSettings settings = safeSettings(blockEntity);
        Level lvl = blockEntity.getLevel();
        long now = lvl == null ? 0 : lvl.getGameTime();

        int bw = Math.max(1, (int) blockEntity.getXSizeScaled());
        int bh = Math.max(1, (int) blockEntity.getYSizeScaled());
        float w = bw * 16f;
        float h = bh * 16f;
        Rectangle area = Rectangle.withSize(0, 0, (int) w, (int) h);

        var stops = blockEntity.getStops();
        String line = settings.lineName();
        String dest = stops.isEmpty() ? "—"
            : stops.get(0).displayName(de.mrjulsen.crn.api.core.CallDirection.DEPARTURE);
        if (dest.length() > 7) dest = dest.substring(0, 7);

        // ── 手动排版表 ──
        int rowCount;
        float headY, headScale;
        float[] rowY;
        float rowScale;
        float footY = -1, footScale = 0;

        if (bw == 3 && bh == 2) {                 // 普通站台悬挂屏
            headY = 8; headScale = 0.50f;
            rowY = new float[]{15, 22, 28}; rowScale = 0.55f; rowCount = 3;
            footY = 31.5f; footScale = 0.30f;
        } else if (bw == 2 && bh == 2) {          // 小尺寸悬挂屏
            headY = 8; headScale = 0.45f;
            rowY = new float[]{18, 28, 0}; rowScale = 0.55f; rowCount = 2;
        } else if (bw == 4 && bh == 1) {          // 大尺寸屏蔽门上屏
            headY = 6.5f; headScale = 0.42f;
            rowY = new float[]{14, 0, 0}; rowScale = 0.55f; rowCount = 1;
        } else if (bw == 3 && bh == 1) {          // 中尺寸屏蔽门上屏
            headY = 6.5f; headScale = 0.38f;
            rowY = new float[]{14, 0, 0}; rowScale = 0.50f; rowCount = 1;
        } else if (bw == 2 && bh == 1) {          // 小尺寸屏蔽门上屏
            headY = -1; headScale = 0;
            rowY = new float[]{10.5f, 0, 0}; rowScale = 0.50f; rowCount = 1;
        } else {                                   // 其它尺寸：按格数粗略兜底
            headY = Math.max(6, h * 0.25f); headScale = 0.45f;
            int n = bh >= 2 ? 3 : 1;
            rowY = new float[3];
            for (int i = 0; i < 3; i++) rowY[i] = Math.min(h - 2, h * (0.45f + i * 0.22f));
            rowScale = 0.5f; rowCount = n;
        }

        // 头：线路色 + 开往
        if (headY > 0) {
            StringBuilder sb = new StringBuilder();
            if (!line.isEmpty()) sb.append("【").append(line).append("】");
            sb.append("开往 ").append(dest);
            head.clippingArea.set(area);
            head.text.set(TextUtils.text(sb.toString()));
            head.preferredWidth.set((float) Math.max(1, w - 4));
            head.position.set(Point.of(2, headY));
            scale(head, headScale);
            head.color.set(lineColor(settings));
        } else {
            head.text.set(null);
        }

        // 三趟
        for (int i = 0; i < rows.length; i++) {
            BERLabel row = rows[i];
            if (i >= rowCount) { row.text.set(null); continue; }

            String minutes = "--";
            int color = 0xFF757575;
            if (i < stops.size()) {
                var e = stops.get(i);
                long ticks = e.realtime().isKnown()
                    ? e.realtime().departureIn(now)
                    : e.scheduled().departureIn(now);
                minutes = String.valueOf(Math.max(0, ticks / 20 / 60));
                color = i == 0 ? 0xFFFF5252 : 0xFFECEFF1;
            }
            row.clippingArea.set(area);
            row.text.set(TextUtils.text(ROW_NAMES[i] + "   " + minutes + " 分钟"));
            row.preferredWidth.set((float) Math.max(1, w - 4));
            row.position.set(Point.of(2, rowY[i]));
            scale(row, rowScale);
            row.color.set(DLColor.fromInt(color));
        }

        // 底部提示
        if (footY > 0) {
            footer.clippingArea.set(area);
            footer.text.set(TextUtils.text("请注意站台间隙，小心慢行。"));
            footer.preferredWidth.set((float) Math.max(1, w - 4));
            footer.position.set(Point.of(2, footY));
            scale(footer, footScale);
        } else {
            footer.text.set(null);
        }
    }

    private static void scale(BERLabel label, float s) {
        label.horizontalScale.set(Pair.of(s, s));
        label.verticalScale.set(Pair.of(s, s));
    }

    /** 线路色：用户选的优先，其次按代号查 CTD 色号表，最后按名字哈希兜底。 */
    static DLColor lineColor(CtdPlatformSettings settings) {
        if (settings.lineColor() != 0) return DLColor.fromInt(settings.lineColor());

        String key = settings.lineName();
        if (key != null) {
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("([0-9]+)").matcher(key);
            if (m.find()) {
                var id = net.minecraft.resources.ResourceLocation.tryParse("ctd:line" + m.group(1));
                int c = top.hibermod.ctdapi.display.LineColors.getOrDefault(id, 0);
                if (c != 0) return DLColor.fromInt(c);
            }
        }

        int hash = key == null ? 0 : key.hashCode();
        int[] palette = {
            0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047,
            0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFF6D4C41
        };
        return DLColor.fromInt(palette[Math.floorMod(hash, palette.length)]);
    }

    static String contentLabel(CtdPlatformSettings.Content content) {
        return switch (content) {
            case DEPARTURES -> "发车";
            case ARRIVALS -> "到达";
            case BOTH -> "到发";
        };
    }

    static String shorten(String s) {
        return s.length() > 18 ? s.substring(0, 18) : s;
    }

    static CtdPlatformSettings safeSettings(AdvancedDisplayBlockEntity blockEntity) {
        var s = blockEntity.getSettings();
        return s instanceof CtdPlatformSettings ctd ? ctd : new CtdPlatformSettings();
    }
}