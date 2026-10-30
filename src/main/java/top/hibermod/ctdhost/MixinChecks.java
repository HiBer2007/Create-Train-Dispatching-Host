package top.hibermod.ctdhost;

import java.util.ArrayList;
import java.util.List;

/**
 * 注入点自检 —— 启动时逐条确认，缺一条就**拒绝启动**。
 *
 * <h2>为什么不能只打日志</h2>
 * 注入失效有两种样子：注入点找不到（{@code require = 1} 会直接崩），
 * 以及整个 mixin 配置没被加载（不崩、不报错、行为全是原版）。
 * 后者极难从现象上分辨——「信号机还是原生显示」「调度指令不生效」
 * 都会被误当成逻辑 bug 排查很久。
 *
 * <p>所以这里对**必要的**注入做硬校验：缺了就抛异常。
 * 一条明确的启动失败，好过一套看起来在跑、实际什么都没做的系统。
 *
 * <h2>为什么不能只靠 {@code require = 1}</h2>
 * {@code require} 只保证「注解在类里且目标方法找得到」，
 * 它管不到「配置文件有没有被 mods.toml 加载」。两道都要。
 */
final class MixinChecks {

    private MixinChecks() {}

    /** 启动时调用；任何必要注入缺失都会抛出。 */
    static void run() {
        List<String> missing = new ArrayList<>();

        if (!boundary()) missing.add("SignalBoundary (signal system NBT/buffer round-trip)");


        // 渲染是客户端专属：专用服务端连 PoseStack 都加载不了，
        // 在那里查它只会把「分发环境」误报成「注入失败」
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            if (!tubeRenderer()) missing.add("NixieTubeRenderer (signal tubes show our aspects)");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "ctdhost: required mixins are not applied -> " + String.join("; ", missing)
                + ". The mod would run without any effect. Check that neoforge.mods.toml "
                + "declares [[mixins]] config = \"ctdhost.mixins.json\" and that client-only "
                + "mixins are listed under \"client\", not \"mixins\".");
        }
    }

    /** 信号边点：接口是否被织进去。 */
    private static boolean boundary() {
        try {
            return top.hibermod.ctdapi.signal.SignalSystemHolder.class
                .isAssignableFrom(
                    com.simibubi.create.content.trains.signal.SignalBoundary.class);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 辉光管渲染器：注入的方法是否出现在目标类里。 */
    private static boolean tubeRenderer() {
        try {
            return hasInjectedMethod(
                com.simibubi.create.content.redstone.nixieTube.NixieTubeRenderer.class,
                "ctd$");
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasInjectedMethod(Class<?> target, String prefix) {
        for (var method : target.getDeclaredMethods()) {
            if (method.getName().startsWith(prefix)) return true;
        }
        return false;
    }
    /**
     * 寻路之后的注入：内核设的目标速度必须在这里落地。
     *
     * <p>它失效时的现象极难认：内核日志显示已下发，遥测里也能看到目标速度，
     * 但车速永远是 0——因为 Create 在之后又把 {@code targetSpeed} 覆盖回去了。
     */
    private static boolean navigationTail() {
        try {
            return hasInjectedMethod(
                com.simibubi.create.content.trains.entity.Navigation.class, "ctd$");
        } catch (Throwable t) {
            return false;
        }
    }

}
