package top.hibermod.ctdhost.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.Telemetry;
import top.hibermod.ctdapi.ato.AtoEngine;
import top.hibermod.ctdapi.ato.AtoEngines;
import top.hibermod.ctdapi.interop.EnergyProvider;
import top.hibermod.ctdhost.execute.AtoPass;
import top.hibermod.ctdhost.execute.TrainAccess;

/**
 * C10 验收：ATO 接入主循环。
 *
 * <h2>为什么值得单独测</h2>
 * 这四条规则每条写错都不会报错，只会让车开得不对：
 *
 * <ol>
 *   <li><b>只能收紧</b>：ATO 与内核限速取 min。写反了 ATO 会压过调度权限。</li>
 *   <li><b>失败禁用</b>：同一引擎连续失败 10 次后不再调用，否则坏引擎每帧刷屏。</li>
 *   <li><b>手动驾驶不接管</b>：司机在开车时 ATO 不该抢方向盘。</li>
 *   <li><b>没有匹配制度就不接管</b>：列车没装对应车载设备时，ATO 应当缺席。</li>
 * </ol>
 *
 * <p>这些都只依赖 API 类型 + {@link TrainAccess} 抽象，不需要世界。
 */
public final class AtoPassTest {

    private AtoPassTest() {}

    private static final ResourceKey<Level> DIM = Level.OVERWORLD;
    /** 用内置制度：ATO 的 freeze 会校验「指向的制度必须已注册」。 */
    private static ResourceLocation SYSTEM = ResourceLocation.parse("ctdapi:mainline");

    private static void bootstrapSystems() {
        top.hibermod.ctdapi.signal.SignalRegistries.bootstrap();
        var systems = top.hibermod.ctdapi.signal.SignalRegistries.systems();
        if (!systems.isEmpty()) {
            SYSTEM = systems.iterator().next().id();
        }
    }

    public static void main(String[] args) {
        int failures = 0;

        bootstrapSystems();

        // 注册表要先冻结，AtoPass 才肯工作
        AtoEngines.unfreeze();
        AtoEngines.resetForTests();

        UUID train = UUID.randomUUID();

        // ① ATO 比内核更紧：取 ATO
        FakeTrainAccess access = new FakeTrainAccess();
        access.put(train, 0.8, true);

        AtoEngines.register(engine("ctd:slow", 0.3));
        AtoEngines.freeze();

        AtoPass pass = new AtoPass();
        int applied = pass.run(1L, List.of(state(train, Set.of(SYSTEM))), access);

        failures += check("ATO 生效一次", applied == 1, "applied=" + applied);
        failures += check("ATO 比内核紧时取 ATO",
            Math.abs(access.targetOf(train) - 0.3) < 1e-9, "target=" + access.targetOf(train));

        // ② ATO 比内核松：保持内核值
        access.put(train, 0.2, true);
        pass.run(2L, List.of(state(train, Set.of(SYSTEM))), access);
        failures += check("ATO 比内核松时取内核",
            Math.abs(access.targetOf(train) - 0.2) < 1e-9, "target=" + access.targetOf(train));

        // ③ 手动驾驶：完全不碰
        access.put(train, 0.55, false);
        int appliedManual = pass.run(3L, List.of(state(train, Set.of(SYSTEM))), access);
        failures += check("手动驾驶不接管", appliedManual == 0, "applied=" + appliedManual);
        failures += check("手动驾驶时目标速度不变",
            Math.abs(access.targetOf(train) - 0.55) < 1e-9, "target=" + access.targetOf(train));

        // ④ 没有匹配的车载制度：不接管
        AtoPass pass2 = new AtoPass();
        access.put(train, 0.7, true);
        int appliedNoSystem = pass2.run(4L, List.of(state(train, Set.of())), access);
        failures += check("没有匹配制度时缺席", appliedNoSystem == 0,
            "applied=" + appliedNoSystem);

        // ⑤ 引擎抛异常：10 次后禁用
        AtoEngines.unfreeze();
        AtoEngines.resetForTests();
        AtoEngines.register(throwingEngine("ctd:broken"));
        AtoEngines.freeze();

        AtoPass pass3 = new AtoPass();
        for (int i = 0; i < 12; i++) {
            pass3.run(10L + i, List.of(state(train, Set.of(SYSTEM))), access);
        }

        int brokenFailures = pass3.failures().getOrDefault(
            ResourceLocation.parse("ctd:broken"), 0);
        failures += check("坏引擎计数到阈值后停止调用", brokenFailures == 10,
            "failures=" + brokenFailures);
        failures += check("坏引擎没有改动目标速度",
            Math.abs(access.targetOf(train) - 0.7) < 1e-9, "target=" + access.targetOf(train));

        // ⑥ 引擎返回 null：本帧不干预，也不算失败
        AtoEngines.unfreeze();
        AtoEngines.resetForTests();
        AtoEngines.register(nullEngine("ctd:idle"));
        AtoEngines.freeze();

        AtoPass pass4 = new AtoPass();
        access.put(train, 0.6, true);
        int appliedNull = pass4.run(30L, List.of(state(train, Set.of(SYSTEM))), access);
        failures += check("引擎返回 null 时不动", appliedNull == 0, "applied=" + appliedNull);
        failures += check("引擎返回 null 不算失败",
            pass4.failures().getOrDefault(ResourceLocation.parse("ctd:idle"), 0) == 0);

        AtoEngines.unfreeze();
        AtoEngines.resetForTests();

        System.out.println();
        if (failures > 0) {
            System.out.println("ATO 主循环验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("ATO 主循环验收通过：只收紧 / 手动不接管 / 无制度缺席 / 坏引擎禁用");
    }

    // ── 假实现 ────────────────────────────────────────────────────────────

    private static final class FakeTrainAccess implements TrainAccess {
        private final Map<UUID, Double> targets = new HashMap<>();
        private final Map<UUID, Boolean> controllable = new HashMap<>();

        void put(UUID id, double target, boolean controllableNow) {
            targets.put(id, target);
            controllable.put(id, controllableNow);
        }

        double targetOf(UUID id) {
            return targets.getOrDefault(id, 0.0);
        }

        @Override public boolean controllable(UUID trainId) {
            return controllable.getOrDefault(trainId, false);
        }

        @Override public double targetSpeed(UUID trainId) {
            return targetOf(trainId);
        }

        @Override public void setTargetSpeed(UUID trainId, double value) {
            targets.put(trainId, value);
        }
    }

    private static AtoEngine engine(String id, double speed) {
        return new StubEngine(ResourceLocation.parse(id), speed, false);
    }

    private static AtoEngine throwingEngine(String id) {
        return new StubEngine(ResourceLocation.parse(id), 0.0, true);
    }

    private static AtoEngine nullEngine(String id) {
        return new StubEngine(ResourceLocation.parse(id), Double.NaN, false) {
            @Override public AtoEngine.Decision decide(AtoEngine.Context in) {
                return null;
            }
        };
    }

    /** 简单桩：给定固定目标速度，或按需抛异常。 */
    private static class StubEngine implements AtoEngine {
        private final ResourceLocation id;
        private final double speed;
        private final boolean throwing;

        StubEngine(ResourceLocation id, double speed, boolean throwing) {
            this.id = id;
            this.speed = speed;
            this.throwing = throwing;
        }

        @Override public ResourceLocation id() {
            return id;
        }

        @Override public ResourceLocation signalSystemId() {
            return SYSTEM;
        }

        @Override public Decision decide(Context in) {
            if (throwing) throw new IllegalStateException("stub failure");
            return new Decision(0.0, 0.0, speed, DoorAction.NONE, false, null,
                Component.literal("stub"));
        }
    }

    // ── 造状态 ────────────────────────────────────────────────────────────

    private static Telemetry.TrainState state(UUID id, Set<ResourceLocation> systems) {
        Rail.NodeId a = new Rail.NodeId(DIM, 0, 64, 0, 0);
        Rail.NodeId b = new Rail.NodeId(DIM, 8, 64, 0, 0);

        return new Telemetry.TrainState(
            id,
            Component.literal("test"),
            8.0,
            new Rail.EdgeId(a, b),
            4.0,
            new Rail.EdgeId(a, b),
            4.0,
            List.of(),
            0.0,
            0.8,
            0.01,
            1.4,
            1.4,
            2,
            false,
            false,
            false,
            0.0,
            null,
            500.0,
            EnergyProvider.EnergySample.unknown(),
            systems);
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }

    @SuppressWarnings("unused")
    private static List<UUID> ids(UUID u) {
        List<UUID> l = new ArrayList<>();
        l.add(u);
        return l;
    }

    @SuppressWarnings("unused")
    private static Optional<AtoEngine> none() {
        return Optional.empty();
    }
}
