package top.hibermod.ctdhost.execute;

import java.util.List;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.ato.AtoEngine;
import top.hibermod.ctdapi.ato.AtoEngines;

/**
 * 把脚本注册的 ATO 接进每帧。
 *
 * <h2>为什么单独一趟</h2>
 * 内核决定「能走多远」（授权），ATO 决定「怎么走」（牵引/制动）。
 * 两者是不同的问题，混在一次调用里会让安全逻辑和驾驶策略互相污染。
 * 所以内核先跑、拿到授权；这一趟再跑，把授权翻译成目标速度。
 *
 * <h2>只能收紧</h2>
 * ATO 的输出与内核的限速取 {@code min}：它可以说「再慢一点」，
 * 不能说「比授权更快」。这条不变式和速度钳位是同一条——
 * 任何一层出问题，最坏结果是车停住，而不是超速。
 *
 * <h2>一列车可能有多套设备的 ATO</h2>
 * 按 {@code TrainState.signalSystems()} 逐套询问，谁的输出更严就听谁的。
 * 现实中多制式车载设备也是这样并存的。
 */
public final class AtoPass {

    /** 单列车单帧最多容忍几次异常；超了就跳过这个 ATO，不让它拖垮整帧。 */
    private static final int MAX_FAILURES = 10;

    private final java.util.Map<net.minecraft.resources.ResourceLocation, Integer> failures =
        new java.util.HashMap<>();

    /**
     * 跑一趟 ATO。
     *
     * @return 实际被 ATO 干预的列车数
     */
    /** 生产入口：直接改 Create 的列车。 */
    public int run(long gameTime, List<Telemetry.TrainState> states) {
        return run(gameTime, states, TrainAccess.createBacked());
    }

    /**
     * 可测试入口。
     *
     * <p>把「拿列车 / 改目标速度」换成 {@link TrainAccess}，
     * 这样「与内核取 min」「失败 10 次禁用」「手动不接管」都能在没有世界的情况下验。
     */
    public int run(long gameTime, List<Telemetry.TrainState> states, TrainAccess access) {
        if (states == null || states.isEmpty()) return 0;
        if (!AtoEngines.frozen()) return 0;            // 还没冻结说明注册阶段没结束

        int applied = 0;

        for (Telemetry.TrainState state : states) {
            if (state.signalSystems() == null || state.signalSystems().isEmpty()) continue;

            // 手动驾驶与脱轨时一律不接管：这两种状态下 Create 自己的逻辑才是对的
            if (!access.controllable(state.id())) continue;

            double best = Double.MAX_VALUE;

            for (var system : state.signalSystems()) {
                AtoEngine engine = AtoEngines.forSignalSystem(system).orElse(null);
                if (engine == null) continue;
                if (failures.getOrDefault(engine.id(), 0) >= MAX_FAILURES) continue;

                try {
                    AtoEngine.Decision decision = engine.decide(new AtoEngine.Context(
                        gameTime,
                        state,
                        state.energy(),
                        null,                                  // speedClass：运营侧未接
                        state.authority(),
                        state.targetSpeed(),                   // 信号侧限速的近似
                        Double.MAX_VALUE,                      // 停车点距离：未接
                        null,                                  // 停车点名称
                        false,                                 // atPlatform
                        false,                                 // doorsOpen
                        List.of(),                             // restrictions：未接
                        List.of()));                           // emergencies：未接

                    if (decision == null) continue;
                    best = Math.min(best, Math.max(0.0, decision.targetSpeed()));
                } catch (Throwable t) {
                    int n = failures.merge(engine.id(), 1, Integer::sum);
                    if (n == MAX_FAILURES) {
                        System.out.println("[ctdhost] ATO " + engine.id()
                            + " disabled after " + n + " failures: " + t);
                    }
                }
            }

            if (best == Double.MAX_VALUE) continue;

            // 只能收紧：与内核限速取 min
            access.setTargetSpeed(state.id(), Math.min(access.targetSpeed(state.id()), best));
            applied++;
        }

        return applied;
    }

    /** 供诊断：当前被禁用的 ATO。 */
    public java.util.Map<net.minecraft.resources.ResourceLocation, Integer> failures() {
        return java.util.Map.copyOf(failures);
    }
}
