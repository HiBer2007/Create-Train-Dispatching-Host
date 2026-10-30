package top.hibermod.ctdhost.tools;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

import top.hibermod.ctdapi.Command;
import top.hibermod.ctdhost.execute.DeviceCommands;

/**
 * 设备指令的落点。
 *
 * <h2>它补的是什么</h2>
 * 设备指令以前只走 {@code OperationFeed} 并计入 {@code forwarded: devices=N}：
 * 脚本发「开站台门」，宿主数了一下，然后什么也没发生，事后也无从查证发过什么。
 *
 * <p>现在每条指令按设备 id 记下来，可以回答三个问题：
 * 「这台设备最后收到什么指令」/「一共接受了几条」/「有没有被拒绝的」。
 *
 * <h2>为什么不解释 payload</h2>
 * 门、屏、计轴器的语义属于各自的设备实现，宿主只保证「收到、存下、查得到」。
 */
public final class DeviceCommandsTest {

    private DeviceCommandsTest() {}

    public static void main(String[] args) {
        int failures = 0;

        DeviceCommands store = new DeviceCommands();
        UUID door = UUID.randomUUID();
        UUID screen = UUID.randomUUID();

        // ① 空输入
        failures += check("空输入返回 0", store.apply(List.of(), 100L) == 0);
        failures += check("空输入无记录", store.size() == 0 && store.accepted() == 0);

        // ② 记一条
        CompoundTag open = new CompoundTag();
        open.putString("door", "open");
        int applied = store.apply(List.of(new Command.DeviceCommand(door, open, 7L)), 100L);

        failures += check("接受一条", applied == 1 && store.accepted() == 1);
        failures += check("按设备可查", store.lastOf(door) != null);
        failures += check("时间戳记录", store.lastOf(door).gameTime() == 100L,
            "t=" + store.lastOf(door).gameTime());
        failures += check("指令内容可读", store.lastOf(door).payload().summary().contains("door=open"),
            store.lastOf(door).payload().summary());
        failures += check("holder 记录", store.lastOf(door).holder() == 7L);

        // ③ 同一设备后一条覆盖前一条
        CompoundTag close = new CompoundTag();
        close.putString("door", "close");
        store.apply(List.of(new Command.DeviceCommand(door, close, 8L)), 120L);

        failures += check("后一条覆盖前一条",
            store.lastOf(door).payload().summary().contains("door=close"),
            store.lastOf(door).payload().summary());
        failures += check("覆盖不增加设备数", store.size() == 1, "size=" + store.size());
        failures += check("接受计数仍然累加", store.accepted() == 2, "accepted=" + store.accepted());

        // ④ 多台设备互不影响
        CompoundTag show = new CompoundTag();
        show.putString("text", "G123 次");
        store.apply(List.of(new Command.DeviceCommand(screen, show, 9L)), 130L);

        failures += check("两台设备各自一条", store.size() == 2, "size=" + store.size());
        failures += check("各查各的",
            store.lastOf(door).payload().summary().contains("door=close")
                && store.lastOf(screen).payload().summary().contains("text="));

        // ⑤ 坏指令被拒绝且不污染已有记录
        int before = store.size();
        int appliedBad = store.apply(List.of(new Command.DeviceCommand(null, new CompoundTag(), 0L)), 140L);

        failures += check("空 deviceId 被拒绝", appliedBad == 0 && store.rejected() == 1,
            "rejected=" + store.rejected());
        failures += check("拒绝不影响已有记录", store.size() == before, "size=" + store.size());

        // ⑥ 空 payload 也能读（不能抛）
        CompoundTag empty = new CompoundTag();
        store.apply(List.of(new Command.DeviceCommand(UUID.randomUUID(), empty, 0L)), 150L);
        boolean readable = store.snapshot().stream().allMatch(r -> r.payload().summary() != null);
        failures += check("空 payload 可安全摘要", readable);

        // ⑦ 快照按设备 id 排序（稳定输出）
        var snap = store.snapshot();
        boolean sorted = true;
        for (int i = 1; i < snap.size(); i++) {
            if (snap.get(i - 1).deviceId().compareTo(snap.get(i).deviceId()) > 0) sorted = false;
        }
        failures += check("快照按 id 排序", sorted);

        // ⑧ 清空
        store.clear();
        failures += check("清空后无记录", store.size() == 0 && store.lastOf(door) == null);

        System.out.println();
        if (failures > 0) {
            System.out.println("设备指令落点验收失败: " + failures + " 项");
            System.exit(1);
        }
        System.out.println("设备指令落点验收通过：记录/覆盖/多设备/拒绝/空负载/排序/清空");
    }

    private static int check(String name, boolean ok) {
        return check(name, ok, "");
    }

    private static int check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  <- " + detail));
        return ok ? 0 : 1;
    }
}
