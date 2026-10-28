package top.hibermod.ctdhost.execute;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.simibubi.create.content.trains.entity.Train;

import top.hibermod.ctdapi.Command;

/**
 * 把 {@link Command.TrainCommand} 落回 {@code train.targetSpeed}。
 *
 * <h2>三条不变式</h2>
 * <ol>
 *   <li><b>只能收紧，不能放宽</b>：结果取 {@code min(原生, 内核)}。内核没资格让车
 *       比 Create 自己想开的还快——那会绕开弯道限速与道岔保护。</li>
 *   <li><b>授权是硬上限</b>：即使内核给了更高的限速，也不得超过刹车曲线
 *       {@code v = sqrt(2aM)} 允许的速度。</li>
 *   <li><b>缺省不干预</b>：没有指令 = 不动 {@code targetSpeed}。</li>
 * </ol>
 *
 * <p>这三条合起来是「宿主只执行，不决策」的数学表达。任何一条被打破，
 * 一个写错的内核就能把列车开出轨。
 */
public final class SpeedApplier {

    private final Map<UUID, Double> appliedAuthority = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastAppliedTick = new ConcurrentHashMap<>();

    /** 本帧施加后的授权，供下一帧的遥测使用。 */
    public double authorityOf(UUID trainId) {
        Double v = appliedAuthority.get(trainId);
        return v == null ? Double.MAX_VALUE : v;
    }

    /** 上次被内核实际作用的游戏刻；用于发现「指令断流」。 */
    public int lastAppliedTick(UUID trainId) {
        Integer v = lastAppliedTick.get(trainId);
        return v == null ? Integer.MIN_VALUE : v;
    }

    public void clear(UUID trainId) {
        appliedAuthority.remove(trainId);
        lastAppliedTick.remove(trainId);
    }

    /**
     * 应用一批列车指令。
     *
     * @param trains  当前世界里的列车（id → Train）
     * @param gameTime 当前游戏刻
     * @return 成功应用的条数
     */
    public int apply(List<Command.TrainCommand> commands, Map<UUID, Train> trains, long gameTime) {
        int applied = 0;
        for (Command.TrainCommand c : commands) {
            Train train = trains.get(c.trainId());
            if (train == null) continue;              // 车已经没了，指令作废

            // 手动驾驶与脱轨时一律不接管：这两种状态下 Create 自己的逻辑才是对的
            if (train.manualTick || train.derailed) {
                clear(c.trainId());
                continue;
            }

            double kernelTarget = c.hold() ? 0.0 : Math.max(0.0, c.speedCap());
            double authority = Math.max(0.0, c.movementAuthority());

            // ② 授权换算成的硬上限
            double byAuthority = authority <= 0
                ? 0.0
                : Math.sqrt(2.0 * train.acceleration() * authority);

            // ① 取 min：只能收紧
            double target = Math.min(train.targetSpeed, Math.min(kernelTarget, byAuthority));
            target = Math.max(0.0, Math.min(target, train.maxSpeed()));

            train.targetSpeed = target;

            appliedAuthority.put(c.trainId(), authority);
            lastAppliedTick.put(c.trainId(), (int) gameTime);
            applied++;
        }
        return applied;
    }

    /** 一帧结束后重新采样参考速度，避免残留。 */
    public void trim(Map<UUID, Train> trains, long gameTime, int staleTicks) {
        for (Map.Entry<UUID, Integer> e : List.copyOf(lastAppliedTick.entrySet())) {
            if (gameTime - e.getValue() > staleTicks) {
                Train t = trains.get(e.getKey());
                if (t != null) {
                    // 不主动改速度：指令断流时应当退回原生，而不是急停
                }
                clear(e.getKey());
            }
        }
    }
}
