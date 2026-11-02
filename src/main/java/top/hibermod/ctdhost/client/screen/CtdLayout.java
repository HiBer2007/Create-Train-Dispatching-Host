package top.hibermod.ctdhost.client.screen;

/**
 * 显示件的排版计算。
 *
 * <p><b>关键</b>：{@code BERLabel} 的 {@code position.y} 是**文字基线**，
 * 字形向上生长（占 9×scale 高）。所以「把一行放在带 [top, top+height) 的垂直中线」
 * 要写成 {@code baseline = top + height/2 + 4.5 * scale}，否则文字会顶出面板上沿。
 */
public final class CtdLayout {

    private CtdLayout() {}

    /** MC 字体行高。 */
    public static final float LINE = 9f;

    /** 带高 → 字号，留 38% 余量。 */
    public static float fit(float bandHeight, float max) {
        return Math.max(0.22f, Math.min(max, bandHeight * 0.62f / LINE));
    }

    /** 把一行垂直居中放进取值区间后的基线 y。 */
    public static float baseline(float bandTop, float bandHeight, float scale) {
        return bandTop + bandHeight * 0.5f + LINE * scale * 0.5f;
    }

    /** 估算一段文字在给定字号下的宽度。 */
    public static float width(String text, float scale) {
        int n = text == null ? 0 : text.length();
        return n * 5.6f * scale;
    }
}