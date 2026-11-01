package top.hibermod.ctdhost.execute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * 灯头找信号机、灯色怎么算——两件纯逻辑，单独放一处。
 *
 * <h2>为什么灯头要「找」信号机</h2>
 * 四种形态（地面/高杆/吊顶/墙壁）安装位置不同，但都从**同一个**信号机本体读状态。
 * 高杆形态中间还可能隔着若干节金属梁段，所以不能只看相邻一格。
 *
 * <h2>为什么状态只有一份</h2>
 * 灯头不缓存语义，每次渲染前现算。魔改 nixie tube 那次失败，
 * 根本原因就是 Create 与我们的状态各存一份、重启后对不上。
 */
public final class SignalHeadLamps {

    private SignalHeadLamps() {}

    /** 往上/往下最多找多少格。高杆信号机可以很高，但没必要无限找。 */
    public static final int MAX_SEARCH = 24;

    /**
     * 从灯头位置找它所属的信号机。
     *
     * <p>顺序很重要：先**向下**（地面、吊顶两种形态都是垂直关系），
     * 再向上（高杆：灯头在梁顶，信号机在梁底），最后才是四个水平方向（墙壁）。
     */
    public static BlockPos findSignal(Level level, BlockPos headPos) {
        BlockPos p = headPos.below();
        for (int i = 0; i < MAX_SEARCH && p.getY() >= level.getMinBuildHeight(); i++, p = p.below()) {
            if (isSignal(level, p)) return p;
            if (!isMastOrAir(level, p)) break;      // 撞到别的方块就别再往下找了
        }

        p = headPos.above();
        for (int i = 0; i < MAX_SEARCH && p.getY() < level.getMaxBuildHeight(); i++, p = p.above()) {
            if (isSignal(level, p)) return p;
            if (!isMastOrAir(level, p)) break;
        }

        for (Direction d : Direction.Plane.HORIZONTAL) {
            p = headPos.relative(d);
            for (int i = 0; i < MAX_SEARCH; i++, p = p.relative(d)) {
                if (isSignal(level, p)) return p;
                if (!isMastOrAir(level, p)) break;
            }
        }
        return null;
    }

    private static boolean isSignal(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof top.hibermod.ctdhost.block.CtdSignalBlock;
    }

    /** 梁段与空气可以穿过；其他方块视为「到头了」。 */
    private static boolean isMastOrAir(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.is(com.simibubi.create.AllBlocks.METAL_GIRDER.getBlock());
    }

    /**
     * 语义 id → 两个灯位的 ARGB（0 表示该灯位不亮）。
     *
     * <p>与 {@code NixieTubeRendererMixin} 里那份保持同一套颜色，
     * 避免「原生管子」与「新信号机」显示不一致。
     */
    public static int[] colorsOf(String aspect) {
        String path = aspect.contains(":") ? aspect.substring(aspect.indexOf(':') + 1) : aspect;
        return switch (path) {
            case "clear" -> new int[] { 0xFFFFFFFF, 0xFF30D158 };
            case "approach_limited" -> new int[] { 0xFFFFFFFF, 0xFFFFD60A };
            case "approach" -> new int[] { 0xFFFFFFFF, 0xFFFF453A };
            case "caution_diverging" -> new int[] { 0xFFFFFFFF, 0xFFFFD60A };
            case "stop" -> new int[] { 0xFFFF453A, 0xFFFF453A };
            case "call_on" -> new int[] { 0xFFFF453A, 0xFFFFD60A };
            case "cbtc_mode" -> new int[] { 0xFF0A84FF, 0 };
            case "shunt" -> new int[] { 0xFFFFFFFF, 0 };
            default -> null;
        };
    }
}
