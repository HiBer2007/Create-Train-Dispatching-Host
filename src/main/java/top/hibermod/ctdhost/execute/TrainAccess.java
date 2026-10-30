package top.hibermod.ctdhost.execute;

import java.util.UUID;

/**
 * ATO 施加目标速度时要动的那两件事，抽成接口是为了能离线测。
 *
 * <h2>为什么值得为它加一层</h2>
 * {@link AtoPass} 的逻辑很短，但每一条都有后果：
 * 「与内核取 min」写错会让 ATO 压过调度；「连续失败 10 次禁用」写错会让一个
 * 坏引擎每帧抛异常把日志刷爆；「手动/脱轨不接管」写错会跟司机抢方向盘。
 *
 * <p>这些在没有世界的情况下完全能验证——只要把「从 Create 拿列车」这一步换成接口。
 * 在此之前 {@code AtoPass} 只能进游戏看，于是它一直停在 🟡。
 */
public interface TrainAccess {

    /** 这列车是否存在且可被接管。 */
    boolean controllable(UUID trainId);

    /** 当前目标速度。 */
    double targetSpeed(UUID trainId);

    /** 写入目标速度。 */
    void setTargetSpeed(UUID trainId, double value);

    /** 生产实现：查 {@code Create.RAILWAYS.trains}。 */
    static TrainAccess createBacked() {
        return new TrainAccess() {
            @Override
            public boolean controllable(UUID trainId) {
                var train = com.simibubi.create.Create.RAILWAYS.trains.get(trainId);
                return train != null && !train.manualTick && !train.derailed;
            }

            @Override
            public double targetSpeed(UUID trainId) {
                var train = com.simibubi.create.Create.RAILWAYS.trains.get(trainId);
                return train == null ? 0.0 : train.targetSpeed;
            }

            @Override
            public void setTargetSpeed(UUID trainId, double value) {
                var train = com.simibubi.create.Create.RAILWAYS.trains.get(trainId);
                if (train != null) train.targetSpeed = value;
            }
        };
    }
}
