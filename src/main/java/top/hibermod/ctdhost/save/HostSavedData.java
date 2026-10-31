package top.hibermod.ctdhost.save;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

import top.hibermod.ctdapi.Rail;
import top.hibermod.ctdapi.status.Emergency;
import top.hibermod.ctdapi.status.Restrictions;

/**
 * 宿主的存档载体。
 *
 * <p><b>为什么存档归宿主，不归 API</b>：存档格式是宿主与世界的约定，不属于契约层。
 * API 只提供 {@code KernelEnv#state()} 这样一个「给你块 NBT，自己保管」的接口，
 * 这样内核的状态格式可以随便改，不用动 API 的版本号。
 *
 * <p>目前持久化四类东西：
 * <ol>
 *   <li>内核私有状态（每个内核一个 {@link CompoundTag}）；</li>
 *   <li>线路限制（施工封闭、临时限速）；</li>
 *   <li>应急指令；</li>
 *   <li>无汽鸣铁道时的逻辑道岔锁。</li>
 * </ol>
 *
 * <p>限制与应急在 API 里是**进程内静态注册表**，宿主负责在关服时导出、开服时导入。
 * 不这么做的话，重启一次「线路封闭」就消失了——那是安全隐患，不是小瑕疵。
 */
public final class HostSavedData extends SavedData {

    public static final String FILE_ID = "ctdhost";

    private final CompoundTag kernels = new CompoundTag();
    private final List<Restrictions.Restriction> restrictions = new ArrayList<>();
    private final List<Emergency.Order> emergencies = new ArrayList<>();
    private final CompoundTag switchLocks = new CompoundTag();

    /** 显式门许可：站台坐标 → 允许/禁止。 */
    private final CompoundTag doorPermissions = new CompoundTag();

    public HostSavedData() {}

    // ── 内核状态 ──────────────────────────────────────────────────────────

    public CompoundTag kernelState(ResourceLocation kernelId) {
        return kernels.contains(kernelId.toString(), Tag.TAG_COMPOUND)
            ? kernels.getCompound(kernelId.toString())
            : new CompoundTag();
    }

    public void putKernelState(ResourceLocation kernelId, CompoundTag tag) {
        kernels.put(kernelId.toString(), tag == null ? new CompoundTag() : tag);
        setDirty();
    }

    // ── 限制 ──────────────────────────────────────────────────────────────

    public List<Restrictions.Restriction> restrictions() {
        return List.copyOf(restrictions);
    }

    public void setRestrictions(List<Restrictions.Restriction> list) {
        restrictions.clear();
        restrictions.addAll(list);
        setDirty();
    }

    // ── 应急 ──────────────────────────────────────────────────────────────

    public List<Emergency.Order> emergencies() {
        return List.copyOf(emergencies);
    }

    public void setEmergencies(List<Emergency.Order> list) {
        emergencies.clear();
        emergencies.addAll(list);
        setDirty();
    }

    // ── 逻辑道岔锁 ────────────────────────────────────────────────────────

    /** 显式门许可（坐标字符串 → boolean）。 */
    public CompoundTag doorPermissions() {
        return doorPermissions;
    }

    public CompoundTag switchLocks() {
        return switchLocks;
    }

    // ── 序列化 ────────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Kernels", kernels.copy());

        ListTag rl = new ListTag();
        for (Restrictions.Restriction r : restrictions) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", r.id());
            t.putString("kind", r.kind().name());
            t.putString("edge", r.edge().encode());
            t.putDouble("from", r.from());
            t.putDouble("to", r.to());
            t.putDouble("speedLimit", r.speedLimit());
            t.putLong("validFrom", r.validFrom());
            t.putLong("validUntil", r.validUntil());
            t.putString("reasonId", r.reasonId().toString());
            t.putString("note", r.note().getString());
            t.putLong("issuer", r.issuer());
            rl.add(t);
        }
        tag.put("Restrictions", rl);

        ListTag el = new ListTag();
        for (Emergency.Order o : emergencies) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", o.id());
            t.putString("action", o.action().name());
            if (o.edge() != null) t.putString("edge", o.edge().encode());
            if (o.trainId() != null) t.putUUID("train", o.trainId());
            t.putLong("issuedAt", o.issuedAt());
            t.putLong("validUntil", o.validUntil());
            t.putLong("issuer", o.issuer());
            t.putString("reason", o.reason().getString());
            el.add(t);
        }
        tag.put("Emergencies", el);

        tag.put("SwitchLocks", switchLocks.copy());
        tag.put("DoorPermissions", doorPermissions.copy());
        return tag;
    }

    public static HostSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        HostSavedData d = new HostSavedData();

        if (tag.contains("Kernels", Tag.TAG_COMPOUND)) {
            d.kernels.merge(tag.getCompound("Kernels"));
        }

        ListTag rl = tag.getList("Restrictions", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundTag t = rl.getCompound(i);
            Rail.EdgeId edge = Rail.EdgeId.tryDecode(t.getString("edge"));
            if (edge == null) continue;                 // 承载它的轨道已删，静默丢弃
            try {
                d.restrictions.add(new Restrictions.Restriction(
                    t.getUUID("id"),
                    Restrictions.Kind.valueOf(t.getString("kind")),
                    edge,
                    t.getDouble("from"),
                    t.getDouble("to"),
                    t.getDouble("speedLimit"),
                    t.getLong("validFrom"),
                    t.getLong("validUntil"),
                    ResourceLocation.parse(t.getString("reasonId")),
                    Component.literal(t.getString("note")),
                    t.getLong("issuer")));
            } catch (RuntimeException e) {
                // 单条坏了不该让整份存档读不出来
            }
        }

        ListTag el = tag.getList("Emergencies", Tag.TAG_COMPOUND);
        for (int i = 0; i < el.size(); i++) {
            CompoundTag t = el.getCompound(i);
            try {
                Rail.EdgeId edge = t.contains("edge", Tag.TAG_STRING)
                    ? Rail.EdgeId.tryDecode(t.getString("edge")) : null;
                UUID train = t.hasUUID("train") ? t.getUUID("train") : null;
                d.emergencies.add(new Emergency.Order(
                    t.getUUID("id"),
                    Emergency.Action.valueOf(t.getString("action")),
                    edge,
                    train,
                    t.getLong("issuedAt"),
                    t.getLong("validUntil"),
                    t.getLong("issuer"),
                    Component.literal(t.getString("reason"))));
            } catch (RuntimeException e) {
                // 同上
            }
        }

        if (tag.contains("SwitchLocks", Tag.TAG_COMPOUND)) {
            d.switchLocks.merge(tag.getCompound("SwitchLocks"));
        }
        if (tag.contains("DoorPermissions", Tag.TAG_COMPOUND)) {
            d.doorPermissions.merge(tag.getCompound("DoorPermissions"));
        }

        return d;
    }

    // ── 读取入口 ──────────────────────────────────────────────────────────

    /**
     * 主世界存档里的那一份。没有就新建。
     *
     * <p>早先这个类写好了 save/load，但**从来没有被实例化过**：
     * 内核的 {@code KernelEnv#state()} 返回的是一个临时 {@code CompoundTag}，
     * {@code markStateDirty()} 是空实现。于是「重启之后内核还在原地」这件事
     * 从未成立——状态每次重启都是空的，而且没有任何报错。
     */
    public static HostSavedData get(net.minecraft.server.MinecraftServer server) {
        if (server == null) return new HostSavedData();
        var overworld = server.overworld();
        if (overworld == null) return new HostSavedData();

        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(HostSavedData::new, HostSavedData::load),
            FILE_ID);
    }
}