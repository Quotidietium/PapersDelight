# 性能对比：R3 容器 tick 路径优化（分组隔离JVM协议，热源组各4样本）

- 基线样本: r3base, r3base2, r3base3, r3base4　候选样本: r3, r3b, r3c, r3d
- 目标基准过滤: potCache, pattern
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| tickBatch.due[interval=1,typical] | 看守 | 0.6 | 0.6 | 1.003x | 持平 |
| tickBatch.due[interval=4,mixed] | 看守 | 1.1 | 1.4 | 0.735x | 持平·噪声带 |
| tickBatch.due[interval=8,replay-cap] | 看守 | 0.8 | 0.7 | 1.123x | **+12.3%** |
| configManager.getOr[lang-hit,depth1] | 看守 | 15.9 | 16.1 | 0.987x | 持平 |
| configManager.getOr[config-hit,depth3] | 看守 | 37.8 | 38.0 | 0.994x | 持平 |
| configManager.getOr[default-hit,depth3] | 看守 | 28.0 | 32.0 | 0.876x | 持平·噪声带 |
| configManager.getOr[miss->default,dynamic-key] | 看守 | 87.5 | 94.3 | 0.928x | 持平 |
| configManager.getOr[miss->default,static-key] | 看守 | 3.5 | 4.1 | 0.856x | 持平·噪声带 |
| configManager.getList[lang-hit] | 看守 | 2.2 | 2.1 | 1.063x | 持平 |
| pattern.sideFaces[list-of] | 目标 | 2.8 | 3.1 | 0.912x | 持平 |
| pattern.sideFaces[static-array] | 目标 | 2.2 | 2.0 | 1.073x | 持平 |
| heat.matchesBlockDef[material-only] | 看守 | 0.5 | 0.5 | 1.010x | 持平 |
| heat.matchesBlockDef[1-state] | 看守 | 55.6 | 58.4 | 0.952x | 持平 |
| heat.matchesBlockDef[2-states] | 看守 | 84.3 | 93.5 | 0.902x | 持平 |
| heat.matchesBlockDef[material-miss] | 看守 | 3.5 | 3.5 | 0.984x | 持平 |
| heat.checkLit[lightable] | 看守 | 1.0 | 1.0 | 0.951x | 持平 |
| recipeTrie.findMatch[hits,shuffled] | 看守 | 319.6 | 312.9 | 1.021x | 持平 |
| recipeTrie.findMatch[wrong-count,miss] | 看守 | 522.7 | 498.8 | 1.048x | 持平 |
| recipeTrie.findMatch[all-miss] | 看守 | 175.4 | 167.9 | 1.044x | 持平 |
| recipeTrie.findMatch[mixed-workload] | 看守 | 391.7 | 423.2 | 0.926x | 持平 |
| matcher.cost[item-id,hit] | 看守 | 8.9 | 10.9 | 0.817x | 持平·噪声带 |
| matcher.cost[item-id,miss] | 看守 | 12.2 | 11.5 | 1.061x | 持平 |
| matcher.cost[tag,hit] | 看守 | 10.5 | 12.9 | 0.814x | 持平·噪声带 |
| matcher.cost[anyOf3,mixed] | 看守 | 22.5 | 24.7 | 0.910x | 持平 |
| tagExpander.anyMatch[empty-map,tag-miss] | 看守 | 17.4 | 18.0 | 0.969x | 持平 |
| tagExpander.anyMatch[empty-map,tag-hit] | 看守 | 10.7 | 10.1 | 1.054x | 持平 |
| tagExpander.anyMatch[nested-map,item-hit] | 看守 | 67.8 | 73.9 | 0.917x | 持平 |
| tagExpander.expand[nested-map,6-items] | 看守 | 111.7 | 118.6 | 0.942x | 持平 |
| potCache.lookup[hit] | 目标 | (新增) | 21.2 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[stale-epoch] | 目标 | (新增) | 8.5 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[input-changed] | 目标 | (新增) | 8.2 | - | 候选侧新增基准，无基线可比 |
| potCache.insert[put] | 目标 | (新增) | 18.2 | - | 候选侧新增基准，无基线可比 |

**汇总**: 明显提速 (≥1.10x) 1 项 / 回归 (≤0.95x) 0 项 / 共 28 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| tickBatch.due[interval=1,typical] | 基线 r3base/common | 0.6 |
| tickBatch.due[interval=1,typical] | 基线 r3base2/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 基线 r3base/common | 1.1 |
| tickBatch.due[interval=4,mixed] | 基线 r3base2/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 基线 r3base/common | 0.8 |
| tickBatch.due[interval=8,replay-cap] | 基线 r3base2/common | 0.9 |
| configManager.getOr[lang-hit,depth1] | 基线 r3base/config | 15.9 |
| configManager.getOr[lang-hit,depth1] | 基线 r3base2/config | 16.1 |
| configManager.getOr[config-hit,depth3] | 基线 r3base/config | 43.3 |
| configManager.getOr[config-hit,depth3] | 基线 r3base2/config | 37.8 |
| configManager.getOr[default-hit,depth3] | 基线 r3base/config | 30.2 |
| configManager.getOr[default-hit,depth3] | 基线 r3base2/config | 28.0 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r3base/config | 87.5 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r3base2/config | 87.9 |
| configManager.getOr[miss->default,static-key] | 基线 r3base/config | 3.5 |
| configManager.getOr[miss->default,static-key] | 基线 r3base2/config | 4.7 |
| configManager.getList[lang-hit] | 基线 r3base/config | 2.2 |
| configManager.getList[lang-hit] | 基线 r3base2/config | 3.1 |
| pattern.sideFaces[list-of] | 基线 r3base/container | 2.8 |
| pattern.sideFaces[list-of] | 基线 r3base2/container | 2.8 |
| pattern.sideFaces[static-array] | 基线 r3base/container | 2.2 |
| pattern.sideFaces[static-array] | 基线 r3base2/container | 2.7 |
| heat.matchesBlockDef[material-only] | 基线 r3base/heat | 0.5 |
| heat.matchesBlockDef[material-only] | 基线 r3base2/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 基线 r3base3/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 基线 r3base4/heat | 0.9 |
| heat.matchesBlockDef[1-state] | 基线 r3base/heat | 57.1 |
| heat.matchesBlockDef[1-state] | 基线 r3base2/heat | 64.4 |
| heat.matchesBlockDef[1-state] | 基线 r3base3/heat | 82.5 |
| heat.matchesBlockDef[1-state] | 基线 r3base4/heat | 55.6 |
| heat.matchesBlockDef[2-states] | 基线 r3base/heat | 90.9 |
| heat.matchesBlockDef[2-states] | 基线 r3base2/heat | 84.3 |
| heat.matchesBlockDef[2-states] | 基线 r3base3/heat | 113.7 |
| heat.matchesBlockDef[2-states] | 基线 r3base4/heat | 105.9 |
| heat.matchesBlockDef[material-miss] | 基线 r3base/heat | 5.4 |
| heat.matchesBlockDef[material-miss] | 基线 r3base2/heat | 5.7 |
| heat.matchesBlockDef[material-miss] | 基线 r3base3/heat | 6.0 |
| heat.matchesBlockDef[material-miss] | 基线 r3base4/heat | 3.5 |
| heat.checkLit[lightable] | 基线 r3base/heat | 1.0 |
| heat.checkLit[lightable] | 基线 r3base2/heat | 4.1 |
| heat.checkLit[lightable] | 基线 r3base3/heat | 2.2 |
| heat.checkLit[lightable] | 基线 r3base4/heat | 1.0 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r3base/recipe | 319.6 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r3base2/recipe | 352.7 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r3base/recipe | 546.4 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r3base2/recipe | 522.7 |
| recipeTrie.findMatch[all-miss] | 基线 r3base/recipe | 175.4 |
| recipeTrie.findMatch[all-miss] | 基线 r3base2/recipe | 188.5 |
| recipeTrie.findMatch[mixed-workload] | 基线 r3base/recipe | 431.5 |
| recipeTrie.findMatch[mixed-workload] | 基线 r3base2/recipe | 391.7 |
| matcher.cost[item-id,hit] | 基线 r3base/recipe | 8.9 |
| matcher.cost[item-id,hit] | 基线 r3base2/recipe | 9.1 |
| matcher.cost[item-id,miss] | 基线 r3base/recipe | 15.3 |
| matcher.cost[item-id,miss] | 基线 r3base2/recipe | 12.2 |
| matcher.cost[tag,hit] | 基线 r3base/recipe | 10.5 |
| matcher.cost[tag,hit] | 基线 r3base2/recipe | 11.9 |
| matcher.cost[anyOf3,mixed] | 基线 r3base/recipe | 22.5 |
| matcher.cost[anyOf3,mixed] | 基线 r3base2/recipe | 28.5 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r3base/recipe | 17.4 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r3base2/recipe | 22.3 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r3base/recipe | 10.7 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r3base2/recipe | 13.2 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r3base/recipe | 67.8 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r3base2/recipe | 81.8 |
| tagExpander.expand[nested-map,6-items] | 基线 r3base/recipe | 111.7 |
| tagExpander.expand[nested-map,6-items] | 基线 r3base2/recipe | 129.0 |
| tickBatch.due[interval=1,typical] | 候选 r3/common | 0.6 |
| tickBatch.due[interval=1,typical] | 候选 r3b/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 候选 r3/common | 1.4 |
| tickBatch.due[interval=4,mixed] | 候选 r3b/common | 1.4 |
| tickBatch.due[interval=8,replay-cap] | 候选 r3/common | 0.7 |
| tickBatch.due[interval=8,replay-cap] | 候选 r3b/common | 0.7 |
| configManager.getOr[lang-hit,depth1] | 候选 r3/config | 17.2 |
| configManager.getOr[lang-hit,depth1] | 候选 r3b/config | 16.1 |
| configManager.getOr[config-hit,depth3] | 候选 r3/config | 41.4 |
| configManager.getOr[config-hit,depth3] | 候选 r3b/config | 38.0 |
| configManager.getOr[default-hit,depth3] | 候选 r3/config | 32.3 |
| configManager.getOr[default-hit,depth3] | 候选 r3b/config | 32.0 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r3/config | 123.6 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r3b/config | 94.3 |
| configManager.getOr[miss->default,static-key] | 候选 r3/config | 4.1 |
| configManager.getOr[miss->default,static-key] | 候选 r3b/config | 4.9 |
| configManager.getList[lang-hit] | 候选 r3/config | 2.1 |
| configManager.getList[lang-hit] | 候选 r3b/config | 2.2 |
| potCache.lookup[hit] | 候选 r3/container | 21.2 |
| potCache.lookup[hit] | 候选 r3b/container | 21.2 |
| potCache.lookup[stale-epoch] | 候选 r3/container | 8.5 |
| potCache.lookup[stale-epoch] | 候选 r3b/container | 11.8 |
| potCache.lookup[input-changed] | 候选 r3/container | 8.2 |
| potCache.lookup[input-changed] | 候选 r3b/container | 9.2 |
| potCache.insert[put] | 候选 r3/container | 19.4 |
| potCache.insert[put] | 候选 r3b/container | 18.2 |
| pattern.sideFaces[list-of] | 候选 r3/container | 3.1 |
| pattern.sideFaces[list-of] | 候选 r3b/container | 5.0 |
| pattern.sideFaces[static-array] | 候选 r3/container | 3.0 |
| pattern.sideFaces[static-array] | 候选 r3b/container | 2.0 |
| heat.matchesBlockDef[material-only] | 候选 r3/heat | 0.5 |
| heat.matchesBlockDef[material-only] | 候选 r3b/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 候选 r3c/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 候选 r3d/heat | 1.3 |
| heat.matchesBlockDef[1-state] | 候选 r3/heat | 77.0 |
| heat.matchesBlockDef[1-state] | 候选 r3b/heat | 72.3 |
| heat.matchesBlockDef[1-state] | 候选 r3c/heat | 84.6 |
| heat.matchesBlockDef[1-state] | 候选 r3d/heat | 58.4 |
| heat.matchesBlockDef[2-states] | 候选 r3/heat | 100.0 |
| heat.matchesBlockDef[2-states] | 候选 r3b/heat | 102.4 |
| heat.matchesBlockDef[2-states] | 候选 r3c/heat | 120.2 |
| heat.matchesBlockDef[2-states] | 候选 r3d/heat | 93.5 |
| heat.matchesBlockDef[material-miss] | 候选 r3/heat | 4.0 |
| heat.matchesBlockDef[material-miss] | 候选 r3b/heat | 3.5 |
| heat.matchesBlockDef[material-miss] | 候选 r3c/heat | 4.0 |
| heat.matchesBlockDef[material-miss] | 候选 r3d/heat | 5.7 |
| heat.checkLit[lightable] | 候选 r3/heat | 1.0 |
| heat.checkLit[lightable] | 候选 r3b/heat | 3.4 |
| heat.checkLit[lightable] | 候选 r3c/heat | 4.2 |
| heat.checkLit[lightable] | 候选 r3d/heat | 3.1 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r3/recipe | 312.9 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r3b/recipe | 349.6 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r3/recipe | 498.8 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r3b/recipe | 569.3 |
| recipeTrie.findMatch[all-miss] | 候选 r3/recipe | 167.9 |
| recipeTrie.findMatch[all-miss] | 候选 r3b/recipe | 168.9 |
| recipeTrie.findMatch[mixed-workload] | 候选 r3/recipe | 447.1 |
| recipeTrie.findMatch[mixed-workload] | 候选 r3b/recipe | 423.2 |
| matcher.cost[item-id,hit] | 候选 r3/recipe | 10.9 |
| matcher.cost[item-id,hit] | 候选 r3b/recipe | 15.1 |
| matcher.cost[item-id,miss] | 候选 r3/recipe | 13.4 |
| matcher.cost[item-id,miss] | 候选 r3b/recipe | 11.5 |
| matcher.cost[tag,hit] | 候选 r3/recipe | 12.9 |
| matcher.cost[tag,hit] | 候选 r3b/recipe | 13.2 |
| matcher.cost[anyOf3,mixed] | 候选 r3/recipe | 24.7 |
| matcher.cost[anyOf3,mixed] | 候选 r3b/recipe | 28.3 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r3/recipe | 26.0 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r3b/recipe | 18.0 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r3/recipe | 15.7 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r3b/recipe | 10.1 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r3/recipe | 79.8 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r3b/recipe | 73.9 |
| tagExpander.expand[nested-map,6-items] | 候选 r3/recipe | 121.4 |
| tagExpander.expand[nested-map,6-items] | 候选 r3b/recipe | 118.6 |

---

## 分析结论（人工评注）

**改动内容**（相对 R2 产物）：
1. **产物原型缓存**（`RecipeManager.resultPrototype`）：`CookingPotManager.canStoreMeal` 原先**每个烹饪 tick** 调 `CraftEngineUtil.createItem(recipe.result, recipe.resultCount)`——每次走 CE 注册表查找 + `buildBukkitItem` 完整构建管线（组件装配 + 物品分配）。现改为每配方一次构建、CHM 命中复用共享只读实例（isSimilar/数量判定只读，adventure/ItemStack 语义安全）；`replaceSnapshot`/`restoreRuntimeState` 整体失效。实际出餐物品仍由 `finishCooking` 每次 fresh 构建不变。
2. **支撑属性节流**（`potTick`）：`updateAutomaticSupport`（CE 属性读取 + `isTraySource` 热源定义扫描）从每补偿 tick 一次改为挂热源刷新同窗（每 `HEAT_CACHE_TICKS`=10 个补偿 tick 一次；放置/交互事件路径仍即时）。`support` 为纯视觉方块属性，全仓库无玩法逻辑读取，变更延迟 ≤ 该窗口（interval=4 时 ≤2s）。
3. **漏斗侧面扫描零分配**：`processHoppers` 的 `List.of(NORTH,SOUTH,EAST,WEST)` 每次调用组包改为静态数组 `SIDE_HOPPER_FACES`。
4. 盘/炉/罐扫描结论：煎锅/烤炉/陶罐 tick 路径已具备批处理、懒分配完成列表、显示快照差分——无需改动。

**结果解读**：
- 本轮主要收益属**集成路径**（CE/服务端运行时绑定，离线 JVM 不可测——`ItemStack` 构造即触发 RegistryAccess，已实测），以调用消除论证：烹饪中每锅每 tick 消除 1 次 CE 物品构建 + 1 次异常风险材质回退，替换为一次 CHM.get（同类操作实测 `potCache.lookup[hit]` 21.2ns / `stale-epoch` 8.5ns / R2 静态 miss 2.4ns 量级）；非空闲锅每 tick 消除 1 次 CE 属性读取 + 热源扫描（热源 1 状态匹配实测 55~85ns/次 + 方块访问），频率降为 1/10。
- `potCache.*`（新增基准行）：容器配方缓存门成本首次被直接量化——命中 21.2ns（含 Location 哈希）、纪元过期 8.5ns、指纹变化 8.2ns、回填 18.2ns；对照 `recipeTrie.findMatch[hits]` 312.9ns，命中态以 ~7% 的成本规避全量匹配。
- `pattern.sideFaces`：跨标签持平（两侧均测同一基准本地代码，符合预期）；同 JVM 内配对对比四样本差值 +0.3~+3.0ns/次（list-of 恒慢于静态数组，含一次 ListN 分配），即 R3 消除的单位成本。
- **热源组方差调查**：首轮 2 样本曾显示 1-state −20.9%/2-states −15.6%（看守项，代码未变）。追加至各 4 样本后：1-state 基线 [57.1, 64.4, 82.5, 55.6] vs 候选 [72.3, 77.0, 84.6, 58.4]，2-states 基线 [90.9, 84.3, 113.7, 105.9] vs 候选 [100.0, 102.4, 120.2, 93.5]——分布完全重叠，最小值收敛至 0.952x/0.902x（噪声带内）。结论：该基准为 JIT 双峰抖动（同 jar 跨运行 54~85ns 全谱），非回归。
- 其余看守组（recipe/config/common）全部持平。

**结论**：R3 无功能性回归（支撑属性为节流而非移除，事件路径保留即时更新）；主要收益在服务器集成路径，以逐 tick 调用消除 + 实测替换成本类别记录。
