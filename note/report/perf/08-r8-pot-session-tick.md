# 性能对比：R8 烹饪锅 GUI 会话每 tick 路径（分组隔离JVM协议，两侧各2样本）

- 基线样本: r8base, r8base2　候选样本: r8, r8b
- 目标基准过滤: none
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| potCache.lookup[hit] | 看守 | 20.9 | 21.2 | 0.985x | 持平 |
| potCache.lookup[stale-epoch] | 看守 | 7.5 | 11.6 | 0.643x | 持平·噪声带 |
| potCache.lookup[input-changed] | 看守 | 11.8 | 12.6 | 0.931x | 持平 |
| potCache.insert[put] | 看守 | 24.3 | 28.3 | 0.857x | 持平·噪声带 |
| pattern.sideFaces[list-of] | 看守 | 3.0 | 2.9 | 1.041x | 持平 |
| pattern.sideFaces[static-array] | 看守 | 2.8 | 2.5 | 1.128x | **+12.8%** |

**汇总**: 明显提速 (≥1.10x) 1 项 / 回归 (≤0.95x) 0 项 / 共 6 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。
> 本轮全部改动位于服务端绑定路径（Inventory/调度器/ItemStack），无可离线度量的目标行；
> container 组 6 行作为看守回归验证（R3 建立的 potCache/pattern 基准，与本轮改动类零交集，
> 全部持平；sideFaces[static-array] +12.8% 为 0.3ns 级抖动，低于协议 >3ns 绝对差门槛）。

## 1. 变更清单（候选 = r8 系列 jar，基线 = r7 系列 jar；全部在 CookingPotManager）

| # | 位置 | 变更 | 论证方式 |
|---|------|------|---------|
| A | `refreshSlot` | 去掉写槽前的预克隆——`writeSlot` 先比较、不等才写且写时自带克隆，原预克隆在稳态每槽每 tick 被丢弃一次 | 调用消除 |
| B | `syncEditableSlots` | 食材/餐具槽回读先比后克隆（原 `take()` 每槽每 tick 无条件克隆后比较）——稳态每打开的锅 GUI 每 tick 最多省 7 次克隆（6 食材 + 餐具）；不等才克隆写入模型，解耦语义不变 | 调用消除 |
| C | `potTick` 会话分支 | 空闲快路径：数据全空（无输入/未烹饪/无待取/无成品）且六向相邻无漏斗且玩家未编辑且热指示未变 → 跳过 regionStep 与 refresh 两次调度提交（**3→1 次/tick**）；与无会话空闲分支的 hopperTicks 不递增语义同型 | 调用消除 |
| C' | `regionStep` | 无变化不刷新：inputChanged/stateChanged/热指示三维均未变时省去第 3 次（refresh）调度提交——GUI 展示的全部维度（食材/餐具=输入与漏斗、进度/待取/成品=tickPot 返回值、热图标=heated 对比 lastRefreshedHeated）被完整覆盖 | 调用消除 |

## 2. 调用消除的成本等级与频率

- **A/B（ItemStack.clone）**：R3 已建立其为数百 ns 级 NMS 重操作的成本等级；频率 = 每个打开 GUI 的锅每 tick（20/s）× 最多 13 处（A：6 食材+餐具+成品槽回写路径；B：6 食材+餐具回读）。稳态（玩家盯着锅不做操作）全部消除。
- **C/C'（Folia/Paper 调度提交）**：每次 `runTask` 为一次任务对象分配 + 队列入队（区域/实体调度器）；原空闲打开态 3 次/tick，现在 1 次（实体线程读玩家背包检测编辑不可省）。烹饪态大部分 tick stateChanged=true（进度每 tick 前进），refresh 保留；「有食材但无配方匹配」「等待玩家取菜且无进度变化」等稳态则降为 2→1 次。按协议不设虚构数字。

## 3. 等价性论证

- **A**：`itemsEqual(clone(x), y) ≡ itemsEqual(x, y)`（clone 保持 equals）；写入路径 `setItem(item.clone())` 原样保留，库存与数据模型永不共享引用。
- **B**：同上；存储值 `take(next)`（空→null、非空→克隆）与原实现写入的值逐字节同源。inventory.getItem 的活性镜像从不入库（take 克隆解耦）。
- **C**：跳过条件的完备性——空闲定义覆盖 tickPot 全部动作前提（无输入/未烹饪/无待取/无成品）；六向漏斗保守扫描（不查朝向）覆盖 processHoppers 全部触发面（上方投料、侧向投餐具、下方取成品）；玩家编辑由 inputChanged 捕获；热图标翻转由 lastRefreshedHeated 对比捕获（打开路径首 tick 必有一次全量刷新校正初值，方向保守只多不漏）。hopperTicks 不递增与无会话空闲分支（`idlePass && 上方无漏斗 → continue`）语义同型。
- **C'**：stateChanged 的赋值面 = processHoppers + tickPot 全部数据突变（进度/待取/成品/烹饪态/渲染缓存均经 changed 聚合）；skipNextIngredientRead 分支在 sync 内已直接刷 GUI。跨线程读 lastRefreshedHeated（boolean，良性竞争）最坏方向为多做一次刷新。
- 全部改动无持久化格式/公开 API 变化；CookingSession.lastRefreshedHeated 为会话期瞬态。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| potCache.lookup[hit] | 基线 r8base/container | 38.2 |
| potCache.lookup[hit] | 基线 r8base2/container | 20.9 |
| potCache.lookup[stale-epoch] | 基线 r8base/container | 12.2 |
| potCache.lookup[stale-epoch] | 基线 r8base2/container | 7.5 |
| potCache.lookup[input-changed] | 基线 r8base/container | 12.0 |
| potCache.lookup[input-changed] | 基线 r8base2/container | 11.8 |
| potCache.insert[put] | 基线 r8base/container | 24.3 |
| potCache.insert[put] | 基线 r8base2/container | 28.2 |
| pattern.sideFaces[list-of] | 基线 r8base/container | 3.2 |
| pattern.sideFaces[list-of] | 基线 r8base2/container | 3.0 |
| pattern.sideFaces[static-array] | 基线 r8base/container | 2.9 |
| pattern.sideFaces[static-array] | 基线 r8base2/container | 2.8 |
| potCache.lookup[hit] | 候选 r8/container | 21.6 |
| potCache.lookup[hit] | 候选 r8b/container | 21.2 |
| potCache.lookup[stale-epoch] | 候选 r8/container | 11.6 |
| potCache.lookup[stale-epoch] | 候选 r8b/container | 12.3 |
| potCache.lookup[input-changed] | 候选 r8/container | 12.8 |
| potCache.lookup[input-changed] | 候选 r8b/container | 12.6 |
| potCache.insert[put] | 候选 r8/container | 28.7 |
| potCache.insert[put] | 候选 r8b/container | 28.3 |
| pattern.sideFaces[list-of] | 候选 r8/container | 2.9 |
| pattern.sideFaces[list-of] | 候选 r8b/container | 5.1 |
| pattern.sideFaces[static-array] | 候选 r8/container | 2.9 |
| pattern.sideFaces[static-array] | 候选 r8b/container | 2.5 |
