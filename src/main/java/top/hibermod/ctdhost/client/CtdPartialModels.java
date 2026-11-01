package top.hibermod.ctdhost.client;

import java.util.Map;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

import top.hibermod.ctdhost.CtdHost;

/**
 * 我们自己的信号辉光模型。
 *
 * <h2>为什么不能只染色</h2>
 * Create 官方的信号管**不是染色**出来的：它有三张**已经带颜色**的贴图
 * （`SIGNAL_RED_GLOW` / `SIGNAL_YELLOW_GLOW` / `SIGNAL_WHITE_GLOW`），
 * 用加法混合叠上去。
 *
 * <p>我们先前拿白管贴图乘一个 RGB 再去加法混合——白色贴图的 RGB 接近 1，
 * 乘完之后加法叠加仍然很亮，于是**看起来发白、发粉，而不是饱和的绿/蓝**。
 * 这就是「颜色不对」的根因。
 *
 * <h2>现在怎么做</h2>
 * 每个语义颜色配一张**自己的辉光贴图**（`assets/ctdhost/textures/block/signal_glow_<color>.png`）
 * 和一个自己的模型（`models/block/signal_glow_<color>.json`）。
 * 渲染时按灯色直接选模型，**不做染色**。
 *
 * <p>贴图是程序生成的径向渐变（中心亮、边缘透明），尺寸 16×16；
 * 模型是一个 16³ 的方块，靠渲染时的缩放压成灯管的形状。
 */
public final class CtdPartialModels {

    private CtdPartialModels() {}

    /**
     * 强制类初始化。
     *
     * <p>Catnip 的 {@code PartialModel.of(...)} 会把模型登记进一个静态表，
     * 之后由它自己在 {@code ModelEvent.RegisterAdditional} 里统一烘焙。
     * **登记必须发生在模型烘焙之前**，否则这些模型永远不会被烘焙，
     * 渲染时拿到的就是「缺失模型」——屏幕上表现为洋红。
     *
     * <p>我们之前是在 mixin 的渲染代码里第一次碰到这个类，那时烘焙早结束了，
     * 于是五个模型全是洋红。这里提供一个显式的入口，让客户端尽早触碰它。
     */
    public static void bootstrap() {
        // 触发静态初始化即可，不需要做任何事。
        GLOW_RED.toString();
    }

    private static PartialModel glow(String color) {
        return PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(CtdHost.MOD_ID, "block/signal_glow_" + color));
    }

    public static final PartialModel GLOW_RED = glow("red");
    public static final PartialModel GLOW_YELLOW = glow("yellow");
    public static final PartialModel GLOW_WHITE = glow("white");
    public static final PartialModel GLOW_GREEN = glow("green");
    public static final PartialModel GLOW_BLUE = glow("blue");

    /** 语义颜色名 → 模型。未知颜色退回白色。 */
    public static PartialModel of(String colorName) {
        return switch (colorName) {
            case "red" -> GLOW_RED;
            case "yellow" -> GLOW_YELLOW;
            case "green" -> GLOW_GREEN;
            case "blue" -> GLOW_BLUE;
            default -> GLOW_WHITE;
        };
    }

    /** 调试用：列出全部。 */
    public static Map<String, PartialModel> all() {
        return Map.of(
            "red", GLOW_RED, "yellow", GLOW_YELLOW, "white", GLOW_WHITE,
            "green", GLOW_GREEN, "blue", GLOW_BLUE);
    }
}
