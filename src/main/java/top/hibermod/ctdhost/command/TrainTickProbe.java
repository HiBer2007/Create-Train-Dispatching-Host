package top.hibermod.ctdhost.command;

import com.simibubi.create.content.trains.entity.Train;

/**
 * 诊断：这列车到底在不在 Create 的 tick 列表里。
 *
 * <p>Create 只 tick {@code movingTrains} 与 {@code waitingTrains} 两个列表里的车。
 * 不在其中时，{@code Train.tick} 根本不会被调用——车看起来「在线」，却什么都不做。
 * 这两个列表是私有的，所以用反射读，读不到就报 {@code ?}。
 */
final class TrainTickProbe {

    private TrainTickProbe() {}

    static String ticked(Train train) {
        if (train == null) return "?";
        try {
            var manager = com.simibubi.create.Create.RAILWAYS;
            for (String field : new String[] { "waitingTrains", "movingTrains" }) {
                var f = manager.getClass().getDeclaredField(field);
                f.setAccessible(true);
                Object list = f.get(manager);
                if (list instanceof java.util.List<?> l && l.contains(train)) {
                    return field.equals("waitingTrains") ? "waiting" : "moving";
                }
            }
            return "NO";
        } catch (Throwable t) {
            return "?";
        }
    }
}
