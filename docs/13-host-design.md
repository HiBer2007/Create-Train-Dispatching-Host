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

---

## 11. 真实服务端实测记录（第一轮）

**环境**：`./gradlew runServer`，NeoForge 21.1.219 + Create 6.0.11-312 + catnip/ponder/flywheel（本地 jar）

### 已验证 ✅

| 项 | 证据 |
|---|---|
| 服务端能起来 | `Done (1.198s)! For help, type "help"` |
| 模组被识别 | Mod List 含 `Create Train Dispatching Host (ctdhost)` |
| `@Mod` 构造执行 | `[ctdhost/]: ctdhost: constructing (mod bus present: true)` |
| `ServerStartingEvent` 触发 | `[ctdhost] attached; 0 registry entries` |
| 自检机制有效 | 未生效时打出了「hook never fired」的 error，而不是静默 |

### 未通过 ❌

**`GlobalRailwayManager#tick` 的 Mixin 没有生效。**

排查过程与结论：

| 配置 | 结果 |
|---|---|
| 只有 `mods { ctdhost { ... } }`，toml 不声明 | 服务端正常，注入不生效，`require = 1` 也不崩 → 配置**没进 Mixin** |
| toml 里加 `[[mixins]] config = ...` | `IllegalArgumentException: Decoration with key 'fabric-modId' already exists on config ctdhost.mixins.json` |
| 只留 toml 声明 | 同上，仍然重复 |

`require = 1` 不崩是决定性证据：如果配置被 Mixin 读到但注入点匹配不上，`require = 1` 会硬崩。
没崩说明**配置根本没被 Mixin 处理**。

而 toml 声明会报「重复装饰」，说明**又确实有另一条路径在注册它**——但那条路径注册出来的配置没有被真正加载。
典型的 ModDevGradle 开发环境行为，产物 jar 里未必复现。

### 下一步（按优先级）

1. 看 `run/logs/debug.log`（不是 latest.log）里的 Mixin 细节——那里才有「Loaded config」「Could not find」这类行；
2. 试 `neoForge { mixin { config "..." } }`（部分版本的 ModDevGradle 提供显式入口）；
3. 若开发环境确实无法加载，**先打 jar 放进 `run/mods/` 验证**——这条最接近真实发布形态；
4. 仍不行则改用不依赖 Mixin 的入口：`LevelTickEvent.Post` + `Create.RAILWAYS.tick` 之后的 NeoForge 事件（精度差一帧，但零 Mixin 风险）。

### 附带发现

- `System.out.println` **不进** `logs/latest.log`（只到 stdout），必须用 SLF4J；
- `neoforge.mods.toml` 里 `version = "${file.jarVersion}"` 在 dev 下渲染成 `0.0NONE`，无害但要知道；
- Ponder 的 JarJar 已内置 catnip，**再单独加 catnip jar 会导致两个模块导出同一个包**（`ModuleLayerHandler` 报 `ResolutionException`）；
- `runServer` 任务需要显式声明 `runs { server { server() } }`，ModDevGradle 不会自动创建。

---

## 12. 真实服务端实测记录（第二轮 —— 链路打通）

**结论：宿主 → 内核 → 指令 → 应用 这条链已在真实服务端里跑通。**

日志（`./gradlew runServer`，Create 6.0.11-312 + NeoForge 21.1.219）：

```
[ctdhost] attached; 1 registry entries
ctdhost: dispatch ticking (gameTime=1, dim=minecraft:overworld)
[ctdhost/info] ctdhost: diagnostic kernel attached
[ctdhost] kernel ctdhost:diagnostic active (all trains)
ctdhost: diagnostic frames=60  trains=0 nodes=0 gameTime=20
ctdhost: diagnostic frames=120 trains=0 nodes=0 gameTime=40
...
ctdhost: diagnostic frames=420 trains=0 nodes=0 gameTime=140
```

**frames 与 gameTime 严格 3:1** —— 每刻一次 `onFrame`，不多不少。
`trains=0 / nodes=0` 是因为新世界既没铺轨也没有列车，镜像返回空快照而没有抛异常，
这本身就是拓扑镜像的一次有效验证（空图路径）。

### 12.1 入口改成了双路

Mixin 至今没生效（见 §11），所以宿主改成**两条入口都收**：

| 入口 | 优先级 | 特点 |
|---|---|---|
| Mixin `GlobalRailwayManager#tick` HEAD | 首选 | 抢在清空占用之前，能看到上一帧真实占用 |
| NeoForge `LevelTickEvent.Post` | 兜底 | **零风险，已实测可用**；晚一帧（targetSpeed 本来也是下一帧才生效，实际无感） |

两条同时活着时按 `(维度, 游戏刻)` 去重，只会跑一次。所以 Mixin 哪天修好了，
自动就变回「更早一步」的语义，不需要改代码。

### 12.2 自检内核

`ctdhost:diagnostic`（默认开发环境开启、生产关闭）存在的意义：
**一个新世界既没轨也没车，工作正常的宿主和完全没工作的宿主长得一模一样。**
它每 60 帧打一行，把「链还活着」变成可观测事实。

### 12.3 仍然未验证的

| 项 | 为什么没验 | 怎么验 |
|---|---|---|
| 列车真的停/发 | 需要铺轨 + 造车 | 进图形客户端，或 `/summon` 后手动组装 |
| 拓扑镜像的非空路径 | 同上 | 铺一段轨后看 `nodes` 是否 > 0 |
| 信号写入 | 需要信号机 | 摆一个信号机，脚本下 `aspect` |
| 道岔物理扳动 | 需要 `railways:switch` 边点 | 装汽鸣铁道 + 摆岔 |

**下一步最有价值的事**：在 `runServer` 的存档里预置一小段轨道。可以用命令
`/create track` 之类，或在 `world/` 里放一个现成的测试存档。
只要 `nodes > 0`，镜像的非空路径就被验了。

---

## 13. 第三轮实测：运营身份链路

```
ctdhost: diagnostic queued assignment T600  (registry now 0)
ctdhost: diagnostic queued assignment T1200 (registry now 1)
```

第一行 `registry now 0` 是**正确的**：日志打在发布之前，同一帧末尾才落地；
第二行读到 1，说明上一帧确实进了注册表。

链路：`ControlChannel.assign` → `HostRuntime.applyControlChannel` → `Assignments.publish`。
这条路径**每帧都在跑**，现在有了实测证据。

### 仍未验证

`Assignments` 里的条目还没有驱动 Create 的时刻表。
「指派 → 列车按车次运行」这一步（G4 的后半段）需要真实列车，仍待图形客户端。

---

## 14. 第四轮实测：指派解析层

```
CTD host status
  ticks       : 2643
  assignments : 1
  resolved    : assignments=1 trains=0 unresolvedUnits=1 incomplete=1
  switches    : logical locks=0, railways=absent (logical only)
  ! topology empty — lay some track to exercise the mirror
```

诊断内核每 600 帧发一次指派，指向一个不存在的车辆编号 `CTD-DIAG`。
解析器如实报 `unresolvedUnits=1 / incomplete=1 / trains=0` —— **这正是期望行为**：
指向不存在的车是正常现象（车被拆了、编号改了），不该让调度停摆，也不该假装成功。

### 分层：解析 vs 应用

| 层 | 做什么 | 状态 |
|---|---|---|
| 注册 | `Assignments.publish` 记账 | ✅ 已实测 |
| **解析** | 编号 → `UUID` → 现有 `Train` | ✅ 已实测（本轮） |
| **应用** | 让列车真的按交路跑（改 `ScheduleRuntime`） | ❌ 未做 |

**为什么应用层还没写**：改 `ScheduleRuntime` 需要先观察真实的
`Schedule` / `GlobalStation` 行为——尤其是「同一列车换车次时旧 schedule 怎么清」。
没观察就写，多半会写出一个看起来能跑、实际不调度的东西。
分层之后，每一层都能单独验，不必等整套都对。

---

## 15. 第五轮实测：拓扑镜像被真实验证

无头环境铺不了铁轨（轨道方块需要贝塞尔数据，Create 也没有造轨命令），
但镜像读的是 `TrackGraph` 而不是方块。所以加了一条调试命令
`/ctd host debugtrack`：直接用 `TrackGraph.createNodeIfAbsent` +
`connectNodes` 往图里插一条边。

### RCON 实测

```
# 造轨前
  topology    : nodes=0 edges=0
  ! topology empty — lay some track to exercise the mirror

# /ctd host debugtrack
debug track created: graph=f4388db3-... nodes=2

# 造轨后
  topology    : nodes=2 edges=1
```

**镜像看到了真实的 `TrackNode` / `TrackEdge` 对象。** 这一次验证覆盖了：

| 环节 | 结果 |
|---|---|
| 遍历 `graph.getNodes()` | ✅ 2 个节点 |
| `locateNode` 取回节点 | ✅ 没有返回 null |
| `StableIds.node` 转换 | ✅ 无异常 |
| `getConnectionsFrom` 边收集 | ✅ 1 条边 |
| 边去重（每条边从两端各见一次） | ✅ 1 而不是 2 |
| 度数计算 | ✅ 两端各 1 |

最后一行「topology empty」的警告也随之为空——**自检按设计消失了**。

### 这条命令是调试用的

它造出的图**没有对应的方块**，列车会在一条「看不见的轨道」上跑。
只在 `runServer` + RCON 的验证场景里用，不要在生产档里敲。

---

## 16. 第六轮实测：造出真列车，验到速度钳位

上一轮用 `/ctd host debugtrack` 直接往图里插边，验证了镜像。
这一轮顺着那条边**凭空造了一列车**。

### 怎么造的

`Train` 的构造器是 private（正常路径是从方块组装），调试命令用反射调用。
转向架方块从 `BuiltInRegistries.BLOCK` 里找第一个 `AbstractBogeyBlock`
——`AllBlocks.SMALL_BOGEY` 的返回类型是 Registrate 的 `BlockEntry`，
而 Registrate 不在本模组的编译路径上。

### 实测

```
# /ctd host debugtrain
debug train created: id=55d0b033-... name=CTD-DEBUG graph=b9ef2210-...

# 诊断内核
ctdhost: diagnostic frames=3300 trains=1 nodes=2 gameTime=1100

# /ctd host status
  topology    : nodes=2 edges=1
  units       : 1
  train       : CTD-DEBUG speed=0.000 target=0.400
```

一次验证了三层：

| 层 | 证据 |
|---|---|
| `TelemetrySampler` | `trains=1` —— 从真实 `Train` 采出 `TrainState` |
| `UnitIndex` | `units=1` —— `Train.name` → 车辆编号 |
| **`SpeedApplier`** | `target=0.400` |

### 速度钳位怎么验的

```
/ctd host debugtrain drive
→ 原生 targetSpeed 被强制设为 1.0
→ 同时下发 proceed(cap=0.4)
→ 下一帧读回 target=0.400
```

**这就是「只能收紧、不能放宽」的实证**：如果宿主允许指令放宽，
结果会是 1.000。这条不变式是整个宿主的安全基础——它保证一个写错的内核
没资格让车开得比 Create 自己想开的还快。

### 仍然没验的

| 项 | 为什么 |
|---|---|
| 列车真的在世界上跑 | 调试列车没有车厢实体，不会 tick 物理 |
| 信号灯真的变色 | 需要方块信号机 |
| 道岔物理扳动 | 需要汽鸣铁道的真实道岔 |

---

## 17. 第七轮实测：信号写入（并抓到一个真 bug）

### 怎么验的

把 `SignalBoundary` 挂到 `TrackGraph` 上会每帧走 `tickState`，而它会去查
`Create.RAILWAYS.signalEdgeGroups`——手动造的边界没有对应分组，会让**整个 tick** 出问题。
所以改用一个**不挂图**的 `SignalBoundary` 实例，验「写进去」这一步。

`./gradlew signalApplierTest`：12 项，跑的是真实的 Create 类。

### 抓到的 bug

```
FAIL 第一面读到 RED   <- GREEN
FAIL 第二面读到 GREEN <- RED
```

**`Couple.set(boolean, T)` 的 `true` 是「第一面」**，而我按「false = first」写了：

```java
return side != Topology.SignalSide.FIRST;   // ❌ 反了
```

后果：**每一条信号指令都会作用到错误的那一面**。
表现为「设了绿灯结果对面的红灯变了」，或者「信号怎么不动」。

修好之后：

```
ok   STOP → RED              ok   第一面写入成功
ok   DARK → RED              ok   第二面写入成功
ok   CAUTION → YELLOW        ok   第一面读到 RED
ok   ADVANCE_CAUTION → YELLOW ok  第二面读到 GREEN
ok   PROCEED → GREEN         ok   同面二次写入覆盖
ok   SHUNT → GREEN
ok   CALL_ON → GREEN
```

### 为什么这个 bug 值钱

它**不可能**靠读代码发现——`Couple` 的 boolean 参数没有文档，
`set(false, x)` 到底写哪一面，只能试。而这个测试的成本是十几行。

同时也说明：**「等进游戏再验」是有代价的**。如果等到有信号机才发现，
排查路径会是「信号机制 → 事件 → 同步 → 最后才怀疑到 Couple」。

### 顺带加的

`SignalApplier.readState(boundary, side)`：读回灯色也走反射，
把所有 `Catnip` 相关的访问收在一个类里，其它地方不用知道 Catnip 存在。

---

## 18. 第八轮实测：指派驱动时刻表（G4 完成）

### 先发现一个 API 缺口

实现 G4 时发现 `Service`（交路）**没有停站序列**——只有 id / 车次号 / 信号制度 /
速度等级 / 优先级。而要把车次落成 Create 的 `Schedule`，必须知道停哪几站、按什么顺序。

**没有这个字段，「指派驱动运行」在实现层根本无从下手。**

所以给 `Service` 加了 `List<ResourceLocation> stops`（元素是车站 id，不是世界对象——
API 层不持有 `GlobalStation`）。

### 落地方式

`ScheduleRuntime.schedule` 是**公开字段**，所以落地就是「构造 Schedule 赋进去」：

```java
DestinationInstruction ins = new DestinationInstruction();
ins.getData().putString("Text", stopId.toString());
entries.add(new ScheduleEntry(ins, List.of()));
train.runtime.schedule = new Schedule(entries, false, 0);
```

**只写 schedule，不碰 `currentEntry` / `state`。** 那些由
`ScheduleRuntime.tick` 自己推进；宿主手动设 `state = ACTIVE` 会和它的状态机打架，
表现会是「车不动，但时刻表显示在跑」。

### RCON 实测

```
train       : CTD-DIAG speed=0.000 target=0.000 schedule=2
resolved    : assignments=1 trains=1 unresolvedUnits=0 incomplete=0
schedule    : appliedTrains=1 entries=2 noService=0 noStops=0
```

整条链路，无一环使用 mock：

| 环节 | 证据 |
|---|---|
| 诊断内核发指派 | `assignments=1` |
| 编号 → `UUID` → `Train` | `trains=1 unresolvedUnits=0` |
| 构造 Create `Schedule` | `entries=2` |
| 写进 `train.runtime.schedule` | `schedule=2` |

### 仍然没验的

**列车不会真的按时刻表跑起来。** 调试列车没有车厢实体，不 tick 物理，
所以 `ScheduleRuntime.tick` 里依赖实体与 `DiscoveredPath` 的部分不会真正执行。

「Schedule 写对了」和「列车真的去跑」是两件事——前者已验，后者需要真实组装的列车。

---

## 19. 第九轮实测：假列车撑不过物理（负结果）

### 问题

`/ctd host debugtrain` 造出的列车**不参与物理 tick**：给了速度也纹丝不动。

### 查到的原因

`GlobalRailwayManager.tickTrains` 遍历的是 **`waitingTrains` / `movingTrains` 两个持久列表**，
不是 `trains` 这个 map。直接把车塞进 map，它永远不会被 tick。

### 试了「那就塞进列表」

用反射把车加进 `waitingTrains` 之后：

```
train : CTD-DIAG ...        ← 之前一直在
（几秒后）
units : 0                   ← 车消失了
```

**它确实开始 tick 了，然后被 `Train.tick` 判为 `invalid`，
由 `tickTrains` 从 `trains` 里移除。**

代码路径很直接：

```java
if (train.invalid) {
    iterator.remove();
    trains.remove(train.id);
    CatnipServices.NETWORK.sendToAllClients(new RemoveTrainPacket(train));
    continue;
}
```

原因是没有车厢实体的列车撑不过一轮物理。

### 所以撤回了

调试列车**刻意不加入那两个列表**，只做静态观测：
遥测、编号、时刻表写入、速度钳位——这些都不需要它动。

「列车真的按时刻表跑」必须用真实组装的列车验。**这条是环境边界，不是实现偷懒。**

### 一个过程教训

这次用脚本改 Java 源码，又一次把文件切坏（重复代码 + 旧代码残留），
不得不整文件重写。**这已经是第三次**（`HostStatusCommand`、`HostRuntime`、本例）。

规律很稳定：**超过两处插入就该整文件重写**。脚本替换适合改一行，不适合改一段。

---

## 20. 第十轮：修掉「时刻表永远导航失败」

### 上一轮漏了什么

上一轮把 `schedule=2` 当成「G4 完成」。但读 `DestinationInstruction.start` 才发现：

```java
for (GlobalStation s : train.graph.getPoints(EdgePointType.STATION))
    if (!s.name.matches(regex)) continue;
```

**指令里写的是正则，匹配的是车站名。** 我们写的是 `ctdapi:alpha` 这种 id，
一个站也匹配不到 → 列车会反复 `failedNavigationNoTarget` + 冷却。

**比不写时刻表更糟。**

### 加了 StationResolver

- 停站 id 的 path 段与车站名做忽略大小写精确匹配；
- 匹配上后用 `Pattern.quote` 包起来再写（车站名可能含正则元字符）；
- **匹配不到就整条跳过**，不退化成写 id。

### 现在的行为

```
schedule : appliedTrains=0 entries=0 unresolvedStops=1 worldStations=0
```

`worldStations=0` 表示世界里还没有车站——于是**不写时刻表**，并明确报出来。

这比之前「写了条永远失败的指令」正确得多。

### 修正一条记录

§18 里写的 `schedule=2` 只证明了**写入路径可用**，不代表那张时刻表能用。
真实可用的前提是：世界里存在与停站 id 对应的 `GlobalStation`。
而车站需要玩家摆方块并命名——又一个环境边界。

---

## 24. 第十四轮：为什么调试列车不能参与物理（两次尝试的确切原因）

上一轮说「假列车撑不过物理 tick」，这轮把原因挖到底了，两条都来自真实源码：

### 第一次尝试：空实体 NBT

```
CarriageEntityHandler.createEntity(level, ...):
    Entity entity = EntityType.create(serialisedEntity, level).orElse(null);
    if (!(entity instanceof CarriageContraptionEntity cce)) {
        train.invalid = true;      // ← 就这里
        return;
    }
```

`Carriage` 构造器把 `serialisedEntity` 初始化成**空 CompoundTag**，
`EntityType.create` 自然返回 null，于是列车被判 invalid 并从注册表移除。

### 第二次尝试：补上实体 NBT + 空构造

构造出真实的 `CarriageContraptionEntity` 存一份 NBT，再加一份
`CarriageContraption(Direction.UP).writeNBT(...)` 作为 "Contraption" 字段。

结果：列车**确实开始 tick 了**，然后——

```
java.lang.NullPointerException: Cannot invoke
  "com.simibubi.create.content.contraptions.Contraption.readNBT(...)"
  because "contraption" is null
```

**整个服务端崩掉。**

### 结论

一辆能被 tick 的列车需要的不是「一个实体」，而是一个**真正从方块组装出来的车厢构造**——
`Contraption` 里的锚点、方块集合、包围盒都来自玩家的组装动作。

**这是环境边界，不是可以绕过的实现细节。** 已经在 `DebugTrainCommand` 的注释里写清楚，
避免下一个人重走这两步。

### 顺带确认（脚本链路依然成立）

```
[demo] id='e41785c3-...' name=CTD-DIAG
train : CTD-DIAG speed=0.000 target=0.350
```

原生被抬到 1.0、debug 指令要 0.4、脚本要 0.35 → **0.350**。脚本的指令在真实链路上生效。

---

## 25. 第十五轮：把 `assign` 实现出来，它立刻抓到一个「沉默的契约」

### 问题

`DispatchFrame#assign` 在 API 里是**默认空实现**：

```java
default void assign(top.hibermod.ctdapi.operation.Assignment assignment) { }
```

而**没有任何宿主覆盖它**。按文档写内核的人会调用它，然后什么都不会发生——
连日志都没有。

### 修复

宿主的 `DispatchFrameImpl` 现在收集指派，帧末与其它指令一起提交：

```java
for (var assignment : assignments) {
    Assignments.publish(assignment);
}
```

脚本侧也开了口：`out.assign(...)`。它和 `operation.publish(...)` 的区别在**时序**——
后者立刻生效，前者等所有内核跑完后按「后写覆盖」决出胜负。

### 立刻暴露的第二个问题

第一个用它的内核就被校验拒绝了：

```
IllegalArgumentException: assignment demo:run900 must reference at least one unit set
```

这正是空实现一直在掩盖的东西。校验是对的。

### 实测（真实服务端）

```
assignments: 2
  demo:run900      run=T900  sets=1 units=1 source=KERNEL
  ctdhost:diag_run run=T4200 sets=1 units=1 source=KERNEL
  resolved demo:run900      trains=1 missing=0
  resolved ctdhost:diag_run trains=1 missing=0
```

`demo:run900` 来自一个 Python 内核调 `out.assign(...)`，并且解析到了调试列车。

### 一个没查清的疑点（如实记录）

日志里仍有一条 `dispatch tick failed ... must reference at least one unit set`，
而注册表里那份 `demo:run900` 是合法的、也解析成功。
说明某个路径上还多发布了一次空集合的副本。**未查清，不下结论。**

另外加了 `/ctd host assignments`，下次排查直接从这条命令看。

---

## 26. 第十六轮：改派最后一辆车会崩——找到并修掉

### 上一轮那个「没查清的疑点」

```
dispatch tick failed: IllegalArgumentException:
  assignment demo:run900 must reference at least one unit set
```

注册表里那份 `demo:run900` 明明是合法的，却报「空指派」。追下去发现根因在 `Assignments.publish`：

```java
if (old != null) {
    BY_ID.put(other, withoutUnit(old, u));   // ← 这里
}
```

`withoutUnit` 把车从旧指派里摘出来，**摘空了照样 new 一个 Assignment**。
而 `Assignment` 的构造契约是「至少一个车组」：

```java
if (sets.isEmpty()) {
    throw new IllegalArgumentException("assignment " + id + " must reference at least one unit set");
}
```

### 触发条件极其普通

只要「把某列车的**唯一**车组改派给另一个指派」就会踩到。
真实场景就是**把车从 A 交路改派到 B 交路**。

后果不崩服，但**整帧调度作废**，日志只有一行 `dispatch tick failed`。

### 修法

```java
Assignment reduced = withoutUnit(old, u);
if (reduced == null) {
    BY_ID.remove(other);                 // 空指派不存在，那就撤掉
    OperationFeed.assignmentRevoked(old);
} else {
    BY_ID.put(other, reduced);
}
```

空指派在契约里根本不是一种状态，所以正确答案是**撤销**，不是造一个「空但合法」的壳。

### 回归测试

`./gradlew assignmentsTest`：9 项，覆盖首发、唯一车组改派、多车组部分摘除、撤销后重新占用。全绿。

### 实测

脚本每 200 帧重新发布一次指派，和诊断内核抢同一列车：

```
assignments: 1
  demo:run900 run=T900 sets=1 units=1 source=KERNEL
  resolved demo:run900 trains=1 missing=0
```

**后发布者胜，输的那个被撤销，日志无错误。**

### 过程教训

上一轮我看到这条错误时报「未查清，不下结论」——按住了编故事的冲动。
这一轮从 `publish` 读到底，发现它和日志里那个 id 完全对得上：
报的是**被摘空的那一方**，不是发布方。如果当时急着下结论，很可能会去改校验器。

---

## 27. 第十七轮：六个「静默丢弃」的指令口

### 怎么发现的

上一轮修 `DispatchFrame#assign`（默认空实现，没人覆盖）时想到：同类问题会不会还有？

查 `CommandApplier`：

```java
@Override public void applyDevices(List<Command.DeviceCommand> commands) { }
@Override public void applyEnergies(List<Command.EnergyCommand> commands) { }
@Override public void applyServices(List<Command.ServiceCommand> commands) { }
@Override public void applyHud(List<Hud.HudPanel> panels) { }
@Override public void applyBoards(List<Pis.BoardModel> boards) { }
@Override public void applyEvents(List<RailwayEvent> events) { }
```

**六个全是空的。** 脚本调 `out.device(...)`、`out.hud(...)` 之类，
调用成功、无异常、无日志——作者会以为生效了。

### 为什么这是错的（而不是「还没做」）

按设计，这些效果本来就属于**别的模组**：设备协议、供电、PIS 渲染。
宿主不该自己实现它们。

但宿主也不该**丢**它们。空实现和「转交但没人订阅」在调用者眼里完全一样，
只有后者的数据还在。

### 改法

`OperationFeed.Listener` 增加六个钩子 + 六个静态发射方法，
`CommandApplier` 转发并计数：

```
forwarded : devices=5 energy=0 service=0 hud=0 boards=0 events=0
```

数字进了 `/ctd host status`，所以「发了但没人收」变成可观测的事。

### 顺带发现的一个陷阱

`out.device(deviceId, payload)` 的第一个参数是 **UUID**，不是命名空间 id：

```java
UUID id = uuid(deviceId);
if (id == null) { dropped++; env.log("warn", ...); return; }
```

我第一次传了 `"ctdapi:platform_display"`，指令被丢弃——**这次有 warn**，
比静默好，但文档里没写清楚。已经在调用点补了说明。

---

## 28. 第十八轮：把最后一个静默丢弃点堵上

### 上一轮之后还剩什么

`FrameOutput` 里六个方法已经有 warn + 计数，但有四个是**直接 return**：

```java
public void hold(String trainId, String reason) {
    UUID id = uuid(trainId);
    if (id == null) return;          // ← 无声无息
    ...
}
```

`hold` / `proceed` / `command` / `aspect` 都是这个形状。
脚本作者写错 id 格式，看到的是「调用成功、没有异常」。

### 改法

统一走 `requireId(raw, what)`：解析失败就 `dropped++` 并 `env.log("warn", ...)`。

脚本自己也能读：`out.dropped()`。

### 一个必须修的设计缺陷

`dropped` 是 `FrameOutput` 的实例字段，而 `FrameOutput` **每帧新建**——
脚本读到的永远是 0。所以计数必须累加到内核上：

```java
int droppedNow = fo.droppedCount();
if (droppedNow > 0) {
    totalDropped += droppedNow;
    if (totalDropped == droppedNow || totalDropped % 50 < droppedNow) {
        env.log("warn", "ctd-script: kernel {} has dropped {} command(s) ...",
            id, totalDropped);
    }
}
```

### 实测

脚本每 100 帧故意传两个坏 id：

```
[ctdhost/warn] ctd-script: bad train id 'not-a-uuid'
[ctdhost/warn] ctd-script: bad signal id 'also-bad'
[script] bad calls dropped so far: 2
[ctdhost/warn] ctd-script: kernel demo:badargs has dropped 2 command(s) with bad arguments so far
[ctdhost/warn] ctd-script: kernel demo:badargs has dropped 50 command(s) ...
```

**三层反馈都有了**：调用点、脚本自检、内核汇总。

### 小结：这一类问题的清单

| 位置 | 症状 | 修法 |
|---|---|---|
| `DispatchFrame#assign` | 默认空实现，没人覆盖 | 宿主实现 + 脚本口 |
| `CommandApplier` 六个 sink | 空方法体 | 转交 `OperationFeed` + 计数 |
| `FrameOutput` 四个方法 | 坏参数直接 return | `requireId` + 计数 + 汇总 |
| `out.device(...)` 参数 | 传命名空间 id 会被丢 | 文档写明要 UUID |

**共同点：都不崩、不报错、不留痕。** 这四类修完之后，
「脚本写了但没生效」应当只剩一种可能——**逻辑写错了**，而不是接口吞了。
