# 性能对比：R7 展示实体刷新与炉灶/餐食呈现路径（分组隔离JVM协议，两侧各2样本）

- 基线样本: r7base, r7base2　候选样本: r7, r7b
- 目标基准过滤: versionParse
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| tickBatch.due[interval=1,typical] | 看守 | 0.7 | 0.6 | 1.045x | 持平 |
| tickBatch.due[interval=4,mixed] | 看守 | 1.4 | 1.1 | 1.246x | **+24.6%** |
| tickBatch.due[interval=8,replay-cap] | 看守 | 0.8 | 0.8 | 1.009x | 持平 |
| versionParse[per-call] | 目标 | 57.6 | 64.6 | 0.892x | **-10.8%** |
| versionParse[cached-read] | 目标 | 0.0 | 0.0 | 1.000x | 持平 |

**汇总**: 明显提速 (≥1.10x) 1 项 / 回归 (≤0.95x) 1 项 / 共 5 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。
> 表格解读见 §2：versionParse 两行均为**基准本地复刻件**（两侧代码相同，跨侧差值属 JIT 噪声），
> 本轮的量化对象是两行的**行间差值**（每次调用被消除的解析成本）；tickBatch[interval=4] 的 +24.6% 为 0.3ns 级抖动（低于协议 >3ns 绝对差判定门槛，记录为噪声）。

## 1. 变更清单（候选 = r7 系列 jar，基线 = r6 系列 jar）

| # | 位置 | 变更 | 离线可测 |
|---|------|------|---------|
| A | `SkilletManager.updateDisplayInPlace` + `displayedSignatures` | 显示签名 diff 门：物品未变（含数量）且实体齐全时跳过逐实体 `clone + setItemStack`；有效性扫描由 stream 改普通循环（零分配）；spawn/remove/stopAll 同步维护签名 | ❌（需 ItemDisplay，见 §3） |
| A' | `SkilletBlockEntityController.storedStackDirect` + `tickSkillet` | tick 路径直取内部存储堆引用（只读），省去 `getStoredStack()` 每 tick 防御性克隆 | ❌（同上） |
| B | `StoveManager.tickStove` | `dropLoc` 惰性分配（仅真正掉落时）；`isBlockedAbove` 仅在非空时检查（提取私有方法） | ❌（需 Block/World） |
| C | `MealLoreUtil.applyMealLore` | 份数条编码并入同一遍 getItemMeta/setItemMeta（原分两遍，每次上菜多一对 meta 复制）；`supportsTooltipDisplay` 版本判定缓存 volatile Boolean（原每次 split+3×parseInt） | 部分：C 的解析消除以基准本地复刻件量化（§2） |

## 2. versionParse 基准解读（唯一离线可测项）

`versionParse[per-call]`（复刻原 supportsTooltipDisplay 每次 split+parse+比较）4 样本 57.6~74.5ns；
`versionParse[cached-read]`（volatile Boolean 读）≈0.03ns（35M~66M ops/ms，JIT 接近完全折叠）。
**行间差值 ≈ 57~75ns/次即 R7-C 消除的每次调用成本**；调用频率 = 每次上菜/餐食容器呈现 1 次。
两行代码在两侧 jar 上完全相同（基准本地复刻件，R3 pattern.sideFaces 模式），表格中 per-call 的 -10.8%（57.6 vs 64.6，基线含一次 57.6 低样本）与跨侧行间比较无意义，按 §0 噪声规则记为持平；cached-read 两侧行为一致。

## 3. 不可离线度量项的调用消除论证

- **A/A'（煎锅展示刷新）**：原实现每 tick（20/s）每个有物煎锅执行 `getStoredStack()` 克隆 + `stream().noneMatch` + 每个展示实体 1 次 `ItemStack.clone` + 1 次 `ItemDisplay.setItemStack`（触发跟踪客户端的元数据包）。稳态（物品静置烹饪，占绝大多数 tick）全部被签名门跳过，仅保留零分配的有效性循环与一次 `equals`。变动率路径（放料/出菜减数）行为不变：数量变化 → equals 失败 → 原刷新+裁剪逻辑照走。ItemStack.clone 为 NMS 重操作（数百 ns 级，R3 已建立该成本等级），ItemDisplay.setItemStack 另有网络侧成本——按协议以调用消除论证记录，不设虚构数字。
- **B（炉灶 tick）**：点燃但空的炉子每 tick 省 1 次 `getRelative+getType`（方块状态读）与 1 次 Location 克隆+加法；有物炉子在无完成槽的 tick 省 Location 分配。行为等价：dropLoc 仅在两个掉落点使用处按需构建；遮蔽判定决策序不变（同一 tick 补偿循环内方块不可能变化）。
- **C（餐食呈现）**：每次上菜省 1 对 getItemMeta/setItemMeta（meta 复制往返，R3 证其成本等级）+ 57~75ns 版本解析（§2 实测）。合并后语义等价：份量条与隐藏数字行的触发条件（版本支持 && Damageable）与原两遍版完全一致。

## 4. 安全性/兼容性论证

- **A**：签名为**私有快照**（`item.clone()`）——控制器会在 cookAndOutput 中对存储堆**原地减数**（setAmount），持内部引用会漏检变化，快照规避；签名随 spawnDisplayEntity 记录、removeAllDisplayEntities/stopAll 清除，生命周期与 displayEntities 严格同步。equals 含数量与 meta，覆盖全部可视变化维度；实体被外部清除（isValid=false）仍走重建路径。threading：tick 在区域线程执行，CHM 与既有 displayEntities 同一并发等级。
- **A'**：包私有直取注释明确「只读、快照自行 clone」；全调用面（getModelCount/equals/clone/ getType.ordinal）均只读。
- **B**：纯惰性化重排，无新分支语义。
- **C**：volatile Boolean 双检读（无锁、发布安全）；版本号运行期恒定，缓存无失效需求。Damageable 分支条件合并后与原逻辑真值表一致。
- 全部改动不触碰 Bukkit/CE 公开行为与持久化格式（displayedSignatures/storedStackDirect/tooltipDisplaySupported 均为运行期瞬态）。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| tickBatch.due[interval=1,typical] | 基线 r7base/common | 0.7 |
| tickBatch.due[interval=1,typical] | 基线 r7base2/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 基线 r7base/common | 1.4 |
| tickBatch.due[interval=4,mixed] | 基线 r7base2/common | 1.4 |
| tickBatch.due[interval=8,replay-cap] | 基线 r7base/common | 0.8 |
| tickBatch.due[interval=8,replay-cap] | 基线 r7base2/common | 0.8 |
| versionParse[per-call] | 基线 r7base/common | 74.5 |
| versionParse[per-call] | 基线 r7base2/common | 57.6 |
| versionParse[cached-read] | 基线 r7base/common | 0.0 |
| versionParse[cached-read] | 基线 r7base2/common | 0.0 |
| tickBatch.due[interval=1,typical] | 候选 r7/common | 0.6 |
| tickBatch.due[interval=1,typical] | 候选 r7b/common | 0.6 |
| tickBatch.due[interval=4,mixed] | 候选 r7/common | 1.4 |
| tickBatch.due[interval=4,mixed] | 候选 r7b/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 候选 r7/common | 0.8 |
| tickBatch.due[interval=8,replay-cap] | 候选 r7b/common | 0.9 |
| versionParse[per-call] | 候选 r7/common | 71.2 |
| versionParse[per-call] | 候选 r7b/common | 64.6 |
| versionParse[cached-read] | 候选 r7/common | 0.0 |
| versionParse[cached-read] | 候选 r7b/common | 0.0 |
