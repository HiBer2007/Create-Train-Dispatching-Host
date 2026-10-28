# 运营身份：车辆编号 / 车组 / 车次

> 代码：`mods/ctdapi/src/main/java/top/hibermod/ctdapi/operation/`

---

## 0. 三个概念不能混

| 概念 | 类型 | 生命周期 | 例子 |
|---|---|---|---|
| **车辆编号** | `UnitNumber` | 与物理列车同生共死 | `CRH2A-2013` |
| **车组** | `UnitSet` | 编组关系，可重组 | `重联-01`（2013 + 2014） |
| **车次** | `Run` | 一次运营任务 | `G1234`，今天 08:00 发车 |
| **交路** | `Service`（已有） | 长期存在 | 「京沪上行」，含停站表与速度等级 |

最常见的建模错误是**把车辆编号当车次**。那样「同一列车换车次」就变成了「换了一列车」，
车厢、里程、故障历史全断。反过来把车次当身份，则「重联」无法表达。

---

## 1. 交路 vs 车次

| | `Service`（交路） | `Run`（车次） |
|---|---|---|
| 变更频率 | 一年几次 | 每天都不一样 |
| 内容 | 停站序列、速度等级 | 具体发车时刻、担当车组 |
| 关系 | 1 | N |

合成一个概念的话，改一趟车就要动交路定义——所以它们是两个类型，`Run` 持有 `serviceId`。

---

## 2. 开行授权 `Assignment`

```java
new Assignment(
    id,                       // 指派标识，用于撤销
    new Run(serviceId, "G1234", plannedDeparture),
    List.of(unitSet),         // 一组车组，有序
    Source.KERNEL,            // KERNEL / EXTERNAL / OPERATOR
    issuedAt)
```

**为什么是一组车组而不是一列车**：重联、摘挂、分段运行都会让「一个车次」对应多组车。
只支持单车的话，重联就得做成「两列车各跑一个车次」，运行图与实际完全对不上。

**`Source` 的意义**：宿主据此决定优先级。
`OPERATOR`（玩家）应压过 `KERNEL`（脚本）——否则玩家手动改派会被脚本下一帧覆盖回去。

---

## 3. `Assignments` 注册表

```java
Assignments.publish(assignment);          // 发布/覆盖，返回被覆盖的旧值
Assignments.revoke(id);                    // 撤销
Assignments.ofUnit(new UnitNumber("CRH2A-2013"));  // 这辆车在跑什么
Assignments.ofRun("G1234");                // 这个车次由谁担当
```

### 3.1 唯一性：后者覆盖前者，不抛异常

同一辆车同一时刻只能属于一个指派。重复指派时**后者覆盖前者**并返回旧值，
而不是抛异常——因为**改派是调度算法的正常行为**。把改派做成异常，
会逼每个内核写一遍「先撤销再指派」的样板代码。

### 3.2 一辆车被两个指派引用

后发布者赢：从旧指派里把这辆车摘掉。旧指派的其余车组不受影响。

### 3.3 谁负责持久化

**宿主**。与 `Restrictions` / `Emergency` 同一形态：API 只提供进程内注册表，
存档格式属于宿主与世界的约定。`HostSavedData` 应当导出 `Assignments.all()`。

---

## 4. 内核怎么用

```python
from ctd import operation

unit = operation.unit("CRH2A-2013")
group = operation.set("mymod", "relay_01", "重联-01", [unit])
run = operation.run("ctdapi:jinghu_up", "G1234", depart_at)
out.assign(operation.assignment("mymod", "job_1", run, [group]))
```

内核只表达「这些车跑这个车次」。**谁该跑哪个车次、怎么排运行图，是调度算法的事**——
API 提供能力，不提供策略。

---

## 5. `DispatchFrame#assign` 是 default 方法

```java
default void assign(Assignment assignment) { }
```

理由与其他可选通道一致：不支持的宿主不该因此编译不过。
**支持指派是宿主的能力，不是义务**——一个只做渲染的宿主可以完全不理会车次。
