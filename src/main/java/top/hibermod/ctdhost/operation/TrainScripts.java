package top.hibermod.ctdhost.operation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;

/**
 * 列车 → 脚本调度的绑定。
 *
 * <h2>为什么不是「全局一个内核」</h2>
 * 一条线网里，干线、支线、调车场的调度规则往往不同，甚至由不同的人维护。
 * 强制全局只有一个内核，等于逼所有人往同一份脚本里塞分支——那是脚本地狱的起点。
 *
 * <p>所以：**同一时刻可以激活多个内核，每列车归属其中一个**。
 * 未归属任何脚本的列车不受调度干预（退回 Create 原生），这是安全的默认值。
 *
 * <h2>绑定的是什么</h2>
 * 绑的是**内核 id**（`DispatchKernels` 里注册的那个），不是文件路径。
 * 脚本热重载时 id 不变，绑定因此不会失效——这正是「算法可热重载」的前提。
 */
public final class TrainScripts {

    private static final Map<UUID, ResourceLocation> BY_TRAIN = new HashMap<>();

    private TrainScripts() {}

    public static void bind(UUID trainId, ResourceLocation kernelId) {
        BY_TRAIN.put(trainId, kernelId);
    }

    public static void unbind(UUID trainId) {
        BY_TRAIN.remove(trainId);
    }

    public static Optional<ResourceLocation> kernelOf(UUID trainId) {
        return Optional.ofNullable(BY_TRAIN.get(trainId));
    }

    /** 属于某个内核的全部列车。 */
    public static java.util.Set<UUID> trainsOf(ResourceLocation kernelId) {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        for (Map.Entry<UUID, ResourceLocation> e : BY_TRAIN.entrySet()) {
            if (e.getValue().equals(kernelId)) out.add(e.getKey());
        }
        return out;
    }

    public static Map<UUID, ResourceLocation> all() {
        return Map.copyOf(BY_TRAIN);
    }

    public static int size() {
        return BY_TRAIN.size();
    }

    public static void clear() {
        BY_TRAIN.clear();
    }
}
