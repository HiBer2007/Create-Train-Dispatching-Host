package top.hibermod.ctdhost.execute;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdhost.telemetry.StationWatcher;

/**
 * 鸣笛：进入鸣笛区段时让列车真的响一声。
 *
 * <h2>为什么由宿主做</h2>
 * 「这里是鸣笛区段」是线路属性（{@code Restrictions.Kind.HORN}），
 * 「什么时候鸣」是行车规则。规则不该依赖某个脚本装没装——
 * 没装内核的车在有人的道口前必须鸣笛。
 *
 * <h2>去重</h2>
 * 一条边只鸣一次。否则列车停在鸣笛区段里会每秒响一次，
 * 那不叫鸣笛，叫卡带。
 *
 * <p>离开该边（或该边限制撤销）后重新武装，这样往返同一段会各鸣一次。
 */
public final class HornWatcher {

    /** 已经为哪条边鸣过（按列车分别记）。 */
    private static final Set<String> HONKED = new HashSet<>();

    private HornWatcher() {}

    /** 每帧调用，返回本帧鸣笛的列车数。 */
    public static int tick(long gameTime) {
        int honked = 0;

        for (Train train : Create.RAILWAYS.trains.values()) {
            Rail.EdgeId edge = RestrictionsApplier.edgeOf(train);
            if (edge == null) continue;

            String key = train.id + "@" + edge.encode();

            boolean required = RestrictionsApplier.hornRequired(edge, gameTime);
            if (!required) {
                HONKED.remove(key);        // 离开后重新武装
                continue;
            }
            if (!HONKED.add(key)) continue;  // 这条边已经鸣过

            if (honk(train)) honked++;
        }

        HONKED.removeIf(k -> !Create.RAILWAYS.trains.containsKey(UUID.fromString(k.substring(0, 36))));
        return honked;
    }

    private static boolean honk(Train train) {
        if (train.carriages.isEmpty()) return false;
        var carriage = train.carriages.isEmpty() ? null : train.carriages.get(0);
        var entity = carriage == null ? null : carriage.anyAvailableEntity();
        if (entity == null) {
            // 列车还没实体化（未组装/未加载区块）：决定是对的，只是没东西可响。
            // 记下来，否则「没声音」会被误判成逻辑没跑
            System.out.println("[ctdhost] horn required but train "
                + (train.name == null ? train.id.toString() : train.name.getString())
                + " has no entity (unassembled or unloaded)");
            return false;
        }
        if (!(entity.level() instanceof ServerLevel level)) return false;

        Vec3 at = entity.position();
        level.playSound(null, at.x, at.y, at.z,
            AllSoundEvents.WHISTLE_TRAIN_MANUAL.getMainEvent(),
            SoundSource.NEUTRAL, 1.5f, 1.0f);

        System.out.println("[ctdhost] horn: "
            + (train.name == null ? train.id.toString() : train.name.getString())
            + " at " + String.format("%.0f, %.0f, %.0f", at.x, at.y, at.z));
        return true;
    }

    /** 自检用。 */
    public static int armed() {
        return HONKED.size();
    }

    public static void reset() {
        HONKED.clear();
        // 顺带清掉车站观察器，避免两处状态不同步
        StationWatcher.reset();
    }
}
