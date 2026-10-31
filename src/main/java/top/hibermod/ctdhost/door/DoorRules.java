package top.hibermod.ctdhost.door;

/**
 * 车门自动判定的**纯规则**：给定一列车的几个可测量，回答「该不该开」。
 *
 * <h2>为什么把它从 {@link DoorService} 里拆出来</h2>
 * 原规则埋在 `isTrainStoppedAt` 里，读的是 `Create.RAILWAYS.trains` 与实体位置——
 * 没有真车就测不了。而「造一列真车」需要玩家装配，命令做不到。
 *
 * <p>拆出来之后，规则本身可以离线钉死（`k4DoorTest`），
 * 世界里只剩「取数」这一层。**能测的规则和不能测的取数分开**，
 * 是这个项目反复用的同一个办法。
 *
 * <h2>规则</h2>
 * 三条同时成立才开门：
 * <ol>
 *   <li>车厢实体离站台方块 ≤ 半径（平方比较，避免开方）；</li>
 *   <li>速度 ≤ 停稳阈值；</li>
 *   <li>没有脱轨。</li>
 * </ol>
 */
public final class DoorRules {

    private DoorRules() {}

    /**
     * 一列车的某节车厢是否让站台「可以开门」。
     *
     * @param distanceSq  车厢实体到站台方块中心的距离平方
     * @param speed       列车速度（格/刻）
     * @param derailed    是否脱轨
     * @param radius      允许半径（格）
     * @param stoppedSpeed 停稳阈值（格/刻）
     */
    public static boolean canOpenAt(double distanceSq, double speed, boolean derailed,
                                    double radius, double stoppedSpeed) {
        if (derailed) return false;
        if (speed > stoppedSpeed) return false;
        return distanceSq <= radius * radius;
    }

    /** 用 {@link DoorService} 的默认参数判定。 */
    public static boolean canOpenAt(double distanceSq, double speed, boolean derailed) {
        return canOpenAt(distanceSq, speed, derailed,
            DoorService.TRAIN_RADIUS, DoorService.STOPPED_SPEED);
    }

    /** 两点的距离平方（站台方块中心 = +0.5）。 */
    public static double distanceSq(double trainX, double trainY, double trainZ,
                                    int stationX, int stationY, int stationZ) {
        double dx = trainX - (stationX + 0.5);
        double dy = trainY - (stationY + 0.5);
        double dz = trainZ - (stationZ + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }
}
