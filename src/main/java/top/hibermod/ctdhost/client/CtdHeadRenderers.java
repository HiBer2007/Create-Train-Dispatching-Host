package top.hibermod.ctdhost.client;

/**
 * 已废弃：灯改由方块模型绘制（signal_head_0..7 + 亮色贴图 + blockstate 切换），
 * 与 Create-Train-Lights 同一路子。运行时手画顶点方块那版永远画成黑方块，已停用。
 *
 * 保留空实现是为了不删调用点，避免再引入一轮编译问题。
 */
public final class CtdHeadRenderers {

    private CtdHeadRenderers() {}

    public static void ensureRegistered() {
        // 什么都不做：灯是模型画的。
    }
}