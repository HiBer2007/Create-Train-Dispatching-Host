# 13 — 宿主模组设计（ctd-host）

> 代码：`mods/ctdhost`（骨架已建，C 阶段进行中）
> 前置：`docs/05`（整体接管）、`docs/09`（注入冲突矩阵）、`docs/10`（API 契约）

---

## 0. 一句话

> **宿主只执行，不决策。** 它把 Create 的列车系统翻译成 API 的值对象，
> 再把内核写的指令翻译回 Create 的写入。

宿主里**不应该有**：寻路、闭塞判断、限速曲线、时刻表推进。
那些属于内核。宿主一旦开始「顺便优化一下」，内核就失去了可替换性。

---

## 1. 类结构

```
top.hibermod.ctdhost
├── CtdHost                  @Mod 主类；注册事件与命令
├── HostRuntime              单例：持有激活的内核、当前帧缓冲
├── dispatch/
│   ├── DispatchPass         每帧的编排：采集 → onFrame → 应用（失败降级）
│   ├── FrameContextImpl     FrameContext 的实现（拓扑 + 遥测 + 设施状态）
│   └── DispatchFrameImpl    DispatchFrame 的实现（收集指令，帧末统一应用）
├── topology/
│   ├── TopologyMirror       TrackGraph → Topology 的快照与增量
│   ├── StableIds            NodeId/EdgeId 与 Create 对象的双向映射
│   └── TopologyView         Topology 的只读实现
├── telemetry/
│   └── TelemetrySampler     Train → TrainState
├── execute/
│   ├── SpeedApplier         TrainCommand → train.speed（取 min + 钳位）
│   ├── SignalApplier        AspectCommand → SignalBoundary.cachedStates
│   ├── SwitchApplier        SwitchCommand → 汽鸣铁道 TrackSwitch / 逻辑锁
│   └── DeviceApplier        DeviceCommand / EnergyCommand / ServiceCommand
├── mixin/
│   ├── GlobalRailwayManagerMixin   ★ 唯一的调度入口
│   ├── TrainMixin                  （若需要，钳位速度）
│   └── SignalBoundaryAccessor      （public 方法的 Accessor，不做逻辑）
└── save/
    └── HostSavedData        KernelEnv.state / Restrictions / Emergency 持久化
```

---

## 2. Mixin 目标登记（R5 要求）

> 每条都必须写清「为什么是这里」和「为什么不是别处」。

| # | 目标 | 选择器 | 为什么是这里 | 冲突风险 |
|---|---|---|---|---|
| **H-1** | `GlobalRailwayManager#tick(Level)` | `@Inject(HEAD, remap=false)` | 整个铁路系统的唯一每帧入口；在 ① 清空占用**之前**跑，内核才能看到上一帧的真实占用 | S&R 在同方法的 `tickTrains` 上有 `@WrapOperation`（不同方法，不冲突） |
| **H-2** | `Train#tickPassiveSlowdown` | `@Inject(TAIL, remap=false)` | 无目的地时的自然减速会覆盖我们的授权结果；在 TAIL 处收紧速度是唯一能兜住所有直写的位置 | 无已知冲突 |
| **H-3** | `SignalBoundary#tickState` | `@Inject(TAIL, remap=false)` | 灯色的最终落点；只改 `cachedStates`，不碰判定 | S&R 未注入此方法 ✅ |
| **H-4** | `TrackGraph#addNode / connectNodes / disconnectNodes` | `@Inject(TAIL)` | 拓扑变更的唯一事实来源，用于增量更新镜像 | 无 |
| **H-5** | `EdgeData` | `@Shadow` 读取 `enabled`（不注入） | 汽鸣铁道把未开通股道置为 `enabled=false`，镜像必须尊重 | **只读，不注入** |

### 2.1 绝对不碰的地方（来自 `docs/09` §2）

| 目标 | 谁占了 | 后果 |
|---|---|---|
| `TrackEdge#canTravelTo` | 汽鸣铁道 `@Inject(HEAD, cancellable)` | 再注入必冲突，道岔门控会失效 |
| `Navigation#search` | CRN `@ModifyExpressionValue` ×2 | 锚点漂移，CRN 崩溃 |
| `Train#frontSignalListener` | 汽鸣铁道 `@Inject(RETURN, cancellable)` | 直接替换会丢掉连挂器逻辑（若确需，必须链式包装） |
| `ScheduleRuntime#tickConditions` | CRN + S&R 都已注入 | 三方混战，不参与 |

---

## 3. 一帧的时序

```
NeoForge LevelTickEvent.Post
└─ GlobalRailwayManager.tick(level)
   ├─ [H-1] DispatchPass.onTick(level)          ← 我们在这里
   │   ├─ ① 若拓扑有变化 → 更新镜像
   │   ├─ ② 采样遥测（上一帧的列车状态）
   │   ├─ ③ 构造 FrameContextImpl
   │   ├─ ④ kernel.onFrame(ctx, frame)          ← 唯一一次跨到内核
   │   ├─ ⑤ 应用 frame 里的指令（已按 min 钳位）
   │   └─ ⑥ 异常 → 丢弃整帧 → 不写任何东西
   ├─ ① 清空 SignalEdgeGroup 占用
   ├─ ② tickPoints(preTrains)   ← 信号分区重算
   ├─ ③ resolveIntersectingEdgeGroups
   ├─ ④ tickTrains              ← 物理推进（吃到我们刚写的速度）
   └─ ⑤ tickPoints(postTrains)  ← 灯色最终落点 [H-3]
```

**关键点**：我们的 pass 在**清空占用之前**。这样内核看到的是「上一帧物理跑完之后」的占用，
而不是被清空过的空集——`docs/01` 专门记过这个坑（占用集合每帧重建）。

---

## 4. 拓扑镜像

### 4.1 稳定 id

Create 的 `TrackNode` / `TrackEdge` 是**会重建**的对象（每次读档、每次编辑）。
镜像必须用值语义 id：

```
NodeId = (dimension, floor(x*2), floor(y)*2, floor(z*2), yOffsetPixels)
EdgeId = 两个端点按 compareTo 排序后配对
```

字符串形式（已实现，`railCodecTest` 验证）：
```
minecraft:overworld@12,-64,34,0
minecraft:overworld@12,-64,34,0|minecraft:overworld@14,-64,34,0
```

### 4.2 增量还是全量

**首次全量，之后增量**。全量遍历 10 万条边在 50ms 预算里是不可接受的；
而 `TrackGraph` 的增删事件（H-4）能覆盖绝大部分变更。

兜底：每 N 秒（默认 30s）做一次全量校验，发现不一致就以全量为准并记一条 warn。
**镜像错了比镜像慢危险得多。**

### 4.3 `enabled`

汽鸣铁道的道岔把未开通的股道置 `EdgeData.enabled = false`。
镜像必须把这个状态带进 `Topology.Edge.enabled()`，否则内核会排进已扳开的股道。

---

## 5. 速度执行与安全钳位

```java
double nativeTarget = train.targetSpeed;      // Create 本帧的意图
double kernelTarget = command.hold() ? 0 : command.speedCap();
double applied = Math.min(nativeTarget, kernelTarget);

// 授权是硬约束：绝不能越过
double allowedByAuthority = Math.sqrt(2 * train.acceleration() * Math.max(0, command.movementAuthority()));
applied = Math.min(applied, allowedByAuthority);

train.targetSpeed = applied;
```

**三条不变式**：

1. 指令只能收紧，不能放宽（`min`）；
2. 授权是硬上限（刹车曲线）；
3. 宿主算出来的速度**永远不大于** Create 原生想给的速度。

第 3 条是「宿主不决策」的数学表达——宿主可以踩刹车，但没资格踩油门超过原生。

---

## 6. 道岔

| 情况 | 行为 |
|---|---|
| 装了汽鸣铁道 | `SwitchCommand` → `TrackSwitch#setSwitchState(NORMAL / REVERSE_LEFT / REVERSE_RIGHT)`，**以它为准** |
| 没装 | 纯逻辑锁：记录「某岔口某进路锁定某股道」，内核查询时返回；不改变物理 |
| 被人工锁闭（`isLocked()`） | 拒绝调度改位，记一条 warn |

**绝不同时维护两套岔位状态**——一定会分叉，表现是「灯显示开通但岔没扳」。

---

## 7. 失败与降级

| 失败 | 行为 |
|---|---|
| 内核抛异常 | 丢弃该帧全部指令，不退化为「全部停车」——**零指令 = Create 原生** |
| 内核连续失败 N 次 | 停用该内核（宿主保留实例，不再调用），记 error |
| 拓扑镜像异常 | 用上一次的快照，只读服务继续；记 error |
| 单个指令非法（越界/未知 id） | 丢弃该条，其余照常 |

**为什么不是「异常就全停」**：全停会让一台坏脚本把整条线网弄瘫。
退回原生至少还能跑——这正是 API「零指令 = 不干预」的设计意图。

---

## 8. 存档

```java
class HostSavedData extends SavedData {
    CompoundTag kernelState;      // KernelEnv.state() 的落点
    List<Restriction> restrictions;
    List<Order> emergencies;
    CompoundTag switchLocks;      // 无 S&R 时的逻辑锁
}
```

**注意**：`Restrictions` / `Emergency` 在 API 里是静态注册表，宿主负责把它们持久化并
在读档时恢复。API 刻意不碰存档——存档格式是宿主的事。

---

## 9. 不做什么

- ❌ 不寻路（内核给 `RouteRequest`，宿主只翻译成 `navigation.currentPath`）
- ❌ 不判断闭塞（内核给授权）
- ❌ 不算限速曲线（内核给 `speedCap`）
- ❌ 不做 PIS 展示（只把 `BoardModel` 交给渲染方）
- ❌ 不实现任何信号制度（那是注册表 + 资源包的事）

---

## 10. 验收

| 阶段 | 判据 |
|---|---|
| C1 | `compileJava` 通过（依赖 Create 编译期可用） |
| C2 | 每帧 `DispatchPass.onTick` 被调用（日志可证） |
| C3 | `/ctd list` 能看到节点数与边数，且与游戏内轨道数一致 |
| C4 | 采样到的速度/位置与列车实际一致 |
| C5 | 脚本给 0 授权 → 列车真的停；给满授权 → 不超原生速度 |
| C6 | 脚本设红灯 → 灯真的变红 |
| C7 | 脚本锁岔 → 汽鸣铁道道岔真的扳过去 |
| C8 | 重启后内核 state 与限制/应急指令不丢 |
| C9 | 故意让脚本抛异常 → 列车继续按原生行为跑，不失控 |
