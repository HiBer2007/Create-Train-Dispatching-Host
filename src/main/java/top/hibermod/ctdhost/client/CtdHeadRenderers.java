package top.hibermod.ctdhost.client;

/**
 * 灯头渲染器的**懒注册**。
 *
 * <h2>为什么不放在启动事件里</h2>
 * 这个会话里试过四种挂法，全部「没被调用过」：
 * {@code @EventBusSubscriber}（默认 GAME 总线）、{@code @Mod(dist=CLIENT)}、
 * 反射挂载、mixin 静态初始化（只在有辉光管渲染时才触发）。
 *
 * <h2>懒注册为什么可靠</h2>
 * {@code BlockEntityRenderers.register} 背后是一个可变的 Map，
 * 只要在**第一次渲染之前**放进去就行。而灯头的 {@code getTicker}
 * 一定会被调用——方块存在，tick 就跑。于是把注册挂在那里，
 * 既不依赖事件总线，也不依赖类加载顺序。
 */
public final class CtdHeadRenderers {

    private CtdHeadRenderers() {}

    private static boolean done;

    /** 幂等；只在客户端调用。 */
    public static void ensureRegistered() {
        // 灯现在由**方块模型**画（signal_head_0..7 + 亮色贴图），不再运行时绘制。
        // 运行时那版画出来的永远是黑方块，已废弃；这里保留成空实现，
        // 免得删掉调用点再引入新的编译问题。
        }
    }
}
