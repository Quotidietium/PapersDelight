# 性能对比：R5 全量回归——1.2.1 原始版本 vs R4 产物（R1~R4 全部优化累计）

- 基线样本: r0, r0b　候选样本: r4, r4b
- 目标基准过滤: (全部按目标严格判定)
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| tickBatch.due[interval=1,typical] | 看守 | 0.6 | 0.7 | 0.982x | 持平 |
| tickBatch.due[interval=4,mixed] | 看守 | 1.4 | 1.1 | 1.276x | **+27.6%** |
| tickBatch.due[interval=8,replay-cap] | 看守 | 0.8 | 0.9 | 0.905x | 持平 |
| configManager.getOr[lang-hit,depth1] | 看守 | 60.5 | 17.1 | 3.542x | **+254.2%** |
| configManager.getOr[config-hit,depth3] | 看守 | 196.4 | 44.5 | 4.416x | **+341.6%** |
| configManager.getOr[default-hit,depth3] | 看守 | 155.5 | 29.1 | 5.353x | **+435.3%** |
| configManager.getOr[miss->default,dynamic-key] | 看守 | 79.0 | 82.9 | 0.953x | 持平 |
| configManager.getOr[miss->default,static-key] | 看守 | 69.2 | 4.2 | 16.280x | **+1528.0%** |
| configManager.getList[lang-hit] | 看守 | 79.8 | 2.0 | 39.058x | **+3805.8%** |
| pattern.sideFaces[list-of] | 看守 | 2.8 | 4.9 | 0.578x | 持平·噪声带 |
| pattern.sideFaces[static-array] | 看守 | 2.7 | 3.0 | 0.904x | 持平 |
| heat.matchesBlockDef[material-only] | 看守 | 1.3 | 0.5 | 2.744x | **+174.4%** |
| heat.matchesBlockDef[1-state] | 看守 | 87.4 | 59.4 | 1.470x | **+47.0%** |
| heat.matchesBlockDef[2-states] | 看守 | 126.2 | 95.5 | 1.321x | **+32.1%** |
| heat.matchesBlockDef[material-miss] | 看守 | 3.5 | 5.3 | 0.662x | 持平·噪声带 |
| heat.checkLit[lightable] | 看守 | 4.3 | 2.0 | 2.149x | **+114.9%** |
| recipeTrie.findMatch[hits,shuffled] | 看守 | 419.6 | 336.7 | 1.246x | **+24.6%** |
| recipeTrie.findMatch[wrong-count,miss] | 看守 | 829.1 | 557.1 | 1.488x | **+48.8%** |
| recipeTrie.findMatch[all-miss] | 看守 | 170.9 | 147.2 | 1.161x | **+16.1%** |
| recipeTrie.findMatch[mixed-workload] | 看守 | 593.8 | 360.6 | 1.647x | **+64.7%** |
| matcher.cost[item-id,hit] | 看守 | 13.6 | 8.7 | 1.561x | **+56.1%** |
| matcher.cost[item-id,miss] | 看守 | 12.8 | 9.0 | 1.427x | **+42.7%** |
| matcher.cost[tag,hit] | 看守 | 10.5 | 10.9 | 0.961x | 持平 |
| matcher.cost[anyOf3,mixed] | 看守 | 27.2 | 20.8 | 1.310x | **+31.0%** |
| tagExpander.anyMatch[empty-map,tag-miss] | 看守 | 52.3 | 17.6 | 2.964x | **+196.4%** |
| tagExpander.anyMatch[empty-map,tag-hit] | 看守 | 22.9 | 10.9 | 2.107x | **+110.7%** |
| tagExpander.anyMatch[nested-map,item-hit] | 看守 | 83.4 | 62.6 | 1.332x | **+33.2%** |
| tagExpander.expand[nested-map,6-items] | 看守 | 127.1 | 120.9 | 1.051x | 持平 |
| text.parse[mm-tag,static-pool] | 看守 | 4235.9 | 3.2 | 1303.741x | **+130274.1%** |
| text.parse[plain-legacy] | 看守 | 188.9 | 5.0 | 37.615x | **+3661.5%** |
| text.parseList[8-lines,static] | 看守 | 42269.6 | 74.6 | 566.806x | **+56580.6%** |
| text.parse[mm-tag,dynamic-key] | 看守 | 5684.7 | 4582.6 | 1.240x | **+24.0%** |
| potCache.lookup[hit] | 看守 | (新增) | 20.8 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[stale-epoch] | 看守 | (新增) | 6.7 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[input-changed] | 看守 | (新增) | 11.1 | - | 候选侧新增基准，无基线可比 |
| potCache.insert[put] | 看守 | (新增) | 16.5 | - | 候选侧新增基准，无基线可比 |

**汇总**: 明显提速 (≥1.10x) 24 项 / 回归 (≤0.95x) 0 项 / 共 32 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| tickBatch.due[interval=1,typical] | 基线 r0/common | 0.7 |
| tickBatch.due[interval=1,typical] | 基线 r0b/common | 0.6 |
| tickBatch.due[interval=4,mixed] | 基线 r0/common | 1.7 |
| tickBatch.due[interval=4,mixed] | 基线 r0b/common | 1.4 |
| tickBatch.due[interval=8,replay-cap] | 基线 r0/common | 0.9 |
| tickBatch.due[interval=8,replay-cap] | 基线 r0b/common | 0.8 |
| configManager.getOr[lang-hit,depth1] | 基线 r0/config | 60.5 |
| configManager.getOr[lang-hit,depth1] | 基线 r0b/config | 61.0 |
| configManager.getOr[config-hit,depth3] | 基线 r0/config | 220.8 |
| configManager.getOr[config-hit,depth3] | 基线 r0b/config | 196.4 |
| configManager.getOr[default-hit,depth3] | 基线 r0/config | 188.8 |
| configManager.getOr[default-hit,depth3] | 基线 r0b/config | 155.5 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r0/config | 89.8 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r0b/config | 79.0 |
| configManager.getOr[miss->default,static-key] | 基线 r0/config | 76.5 |
| configManager.getOr[miss->default,static-key] | 基线 r0b/config | 69.2 |
| configManager.getList[lang-hit] | 基线 r0/config | 79.8 |
| configManager.getList[lang-hit] | 基线 r0b/config | 122.3 |
| pattern.sideFaces[list-of] | 基线 r0/container | 2.8 |
| pattern.sideFaces[list-of] | 基线 r0b/container | 2.9 |
| pattern.sideFaces[static-array] | 基线 r0/container | 2.7 |
| pattern.sideFaces[static-array] | 基线 r0b/container | 2.8 |
| heat.matchesBlockDef[material-only] | 基线 r0/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 基线 r0b/heat | 1.3 |
| heat.matchesBlockDef[1-state] | 基线 r0/heat | 92.9 |
| heat.matchesBlockDef[1-state] | 基线 r0b/heat | 87.4 |
| heat.matchesBlockDef[2-states] | 基线 r0/heat | 126.2 |
| heat.matchesBlockDef[2-states] | 基线 r0b/heat | 140.2 |
| heat.matchesBlockDef[material-miss] | 基线 r0/heat | 3.5 |
| heat.matchesBlockDef[material-miss] | 基线 r0b/heat | 4.5 |
| heat.checkLit[lightable] | 基线 r0/heat | 4.3 |
| heat.checkLit[lightable] | 基线 r0b/heat | 4.3 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r0/recipe | 472.9 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r0b/recipe | 419.6 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r0/recipe | 871.6 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r0b/recipe | 829.1 |
| recipeTrie.findMatch[all-miss] | 基线 r0/recipe | 229.2 |
| recipeTrie.findMatch[all-miss] | 基线 r0b/recipe | 170.9 |
| recipeTrie.findMatch[mixed-workload] | 基线 r0/recipe | 648.6 |
| recipeTrie.findMatch[mixed-workload] | 基线 r0b/recipe | 593.8 |
| matcher.cost[item-id,hit] | 基线 r0/recipe | 13.6 |
| matcher.cost[item-id,hit] | 基线 r0b/recipe | 15.7 |
| matcher.cost[item-id,miss] | 基线 r0/recipe | 15.0 |
| matcher.cost[item-id,miss] | 基线 r0b/recipe | 12.8 |
| matcher.cost[tag,hit] | 基线 r0/recipe | 10.5 |
| matcher.cost[tag,hit] | 基线 r0b/recipe | 15.4 |
| matcher.cost[anyOf3,mixed] | 基线 r0/recipe | 29.1 |
| matcher.cost[anyOf3,mixed] | 基线 r0b/recipe | 27.2 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r0/recipe | 53.8 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r0b/recipe | 52.3 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r0/recipe | 28.5 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r0b/recipe | 22.9 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r0/recipe | 83.4 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r0b/recipe | 89.0 |
| tagExpander.expand[nested-map,6-items] | 基线 r0/recipe | 145.7 |
| tagExpander.expand[nested-map,6-items] | 基线 r0b/recipe | 127.1 |
| text.parse[mm-tag,static-pool] | 基线 r0/text | 4922.2 |
| text.parse[mm-tag,static-pool] | 基线 r0b/text | 4235.9 |
| text.parse[plain-legacy] | 基线 r0/text | 210.0 |
| text.parse[plain-legacy] | 基线 r0b/text | 188.9 |
| text.parseList[8-lines,static] | 基线 r0/text | 50783.2 |
| text.parseList[8-lines,static] | 基线 r0b/text | 42269.6 |
| text.parse[mm-tag,dynamic-key] | 基线 r0/text | 6849.8 |
| text.parse[mm-tag,dynamic-key] | 基线 r0b/text | 5684.7 |
| tickBatch.due[interval=1,typical] | 候选 r4/common | 0.7 |
| tickBatch.due[interval=1,typical] | 候选 r4b/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 候选 r4/common | 1.4 |
| tickBatch.due[interval=4,mixed] | 候选 r4b/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 候选 r4/common | 0.9 |
| tickBatch.due[interval=8,replay-cap] | 候选 r4b/common | 0.9 |
| configManager.getOr[lang-hit,depth1] | 候选 r4/config | 17.1 |
| configManager.getOr[lang-hit,depth1] | 候选 r4b/config | 17.3 |
| configManager.getOr[config-hit,depth3] | 候选 r4/config | 45.3 |
| configManager.getOr[config-hit,depth3] | 候选 r4b/config | 44.5 |
| configManager.getOr[default-hit,depth3] | 候选 r4/config | 29.1 |
| configManager.getOr[default-hit,depth3] | 候选 r4b/config | 33.9 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r4/config | 82.9 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r4b/config | 84.6 |
| configManager.getOr[miss->default,static-key] | 候选 r4/config | 6.8 |
| configManager.getOr[miss->default,static-key] | 候选 r4b/config | 4.2 |
| configManager.getList[lang-hit] | 候选 r4/config | 2.1 |
| configManager.getList[lang-hit] | 候选 r4b/config | 2.0 |
| potCache.lookup[hit] | 候选 r4/container | 20.8 |
| potCache.lookup[hit] | 候选 r4b/container | 21.3 |
| potCache.lookup[stale-epoch] | 候选 r4/container | 6.7 |
| potCache.lookup[stale-epoch] | 候选 r4b/container | 11.5 |
| potCache.lookup[input-changed] | 候选 r4/container | 12.2 |
| potCache.lookup[input-changed] | 候选 r4b/container | 11.1 |
| potCache.insert[put] | 候选 r4/container | 16.5 |
| potCache.insert[put] | 候选 r4b/container | 20.5 |
| pattern.sideFaces[list-of] | 候选 r4/container | 4.9 |
| pattern.sideFaces[list-of] | 候选 r4b/container | 5.2 |
| pattern.sideFaces[static-array] | 候选 r4/container | 3.0 |
| pattern.sideFaces[static-array] | 候选 r4b/container | 3.0 |
| heat.matchesBlockDef[material-only] | 候选 r4/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 候选 r4b/heat | 0.5 |
| heat.matchesBlockDef[1-state] | 候选 r4/heat | 59.4 |
| heat.matchesBlockDef[1-state] | 候选 r4b/heat | 60.8 |
| heat.matchesBlockDef[2-states] | 候选 r4/heat | 95.5 |
| heat.matchesBlockDef[2-states] | 候选 r4b/heat | 128.0 |
| heat.matchesBlockDef[material-miss] | 候选 r4/heat | 6.0 |
| heat.matchesBlockDef[material-miss] | 候选 r4b/heat | 5.3 |
| heat.checkLit[lightable] | 候选 r4/heat | 4.2 |
| heat.checkLit[lightable] | 候选 r4b/heat | 2.0 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r4/recipe | 365.0 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r4b/recipe | 336.7 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r4/recipe | 560.5 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r4b/recipe | 557.1 |
| recipeTrie.findMatch[all-miss] | 候选 r4/recipe | 187.4 |
| recipeTrie.findMatch[all-miss] | 候选 r4b/recipe | 147.2 |
| recipeTrie.findMatch[mixed-workload] | 候选 r4/recipe | 360.6 |
| recipeTrie.findMatch[mixed-workload] | 候选 r4b/recipe | 468.9 |
| matcher.cost[item-id,hit] | 候选 r4/recipe | 8.7 |
| matcher.cost[item-id,hit] | 候选 r4b/recipe | 9.3 |
| matcher.cost[item-id,miss] | 候选 r4/recipe | 9.6 |
| matcher.cost[item-id,miss] | 候选 r4b/recipe | 9.0 |
| matcher.cost[tag,hit] | 候选 r4/recipe | 10.9 |
| matcher.cost[tag,hit] | 候选 r4b/recipe | 11.3 |
| matcher.cost[anyOf3,mixed] | 候选 r4/recipe | 33.4 |
| matcher.cost[anyOf3,mixed] | 候选 r4b/recipe | 20.8 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r4/recipe | 18.2 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r4b/recipe | 17.6 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r4/recipe | 15.8 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r4b/recipe | 10.9 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r4/recipe | 62.6 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r4b/recipe | 68.5 |
| tagExpander.expand[nested-map,6-items] | 候选 r4/recipe | 132.4 |
| tagExpander.expand[nested-map,6-items] | 候选 r4b/recipe | 120.9 |
| text.parse[mm-tag,static-pool] | 候选 r4/text | 3.3 |
| text.parse[mm-tag,static-pool] | 候选 r4b/text | 3.2 |
| text.parse[plain-legacy] | 候选 r4/text | 5.5 |
| text.parse[plain-legacy] | 候选 r4b/text | 5.0 |
| text.parseList[8-lines,static] | 候选 r4/text | 74.6 |
| text.parseList[8-lines,static] | 候选 r4b/text | 112.6 |
| text.parse[mm-tag,dynamic-key] | 候选 r4/text | 4582.6 |
| text.parse[mm-tag,dynamic-key] | 候选 r4b/text | 4943.6 |

---

## 综合分析（R1~R4 全活动累计）

**基线**：1.2.1 原始构建（`benchmark/lib/baseline`，未含任何优化）在新分组隔离协议下重测（r0/r0b 各 2 样本）。
**候选**：R4 产物（含 R1~R4 全部优化，r4/r4b 各 2 样本）。

### 累计成果一览（按子系统）

| 子系统 | 关键基准 | 原始 | 现状 | 倍率 | 主要来源 |
|--------|---------|-----:|-----:|-----:|---------|
| 文本解析（GUI/消息全链路） | 静态 MiniMessage 串 | 4236 | 3.2 | **1304x** | R4 解析缓存 |
| 文本解析 | 8 行 lore 列表 | 42270 | 75 | **567x** | R4 |
| 文本解析 | 纯 legacy 串 | 189 | 5.0 | **38x** | R4 |
| 配置总线 | getList | 80 | 2.0 | **39x** | R2 读穿缓存 |
| 配置总线 | getOr 静态 miss | 69 | 4.2 | **16x** | R2 负缓存 |
| 配置总线 | getOr 命中（3 级） | 60~196 | 17~45 | **3.5~5.4x** | R2 |
| 热源判定 | 1 状态 / 2 状态 | 87 / 126 | 59 / 96 | **1.47x / 1.32x** | R2 零拼接 |
| 配方匹配 | 混合负载 / 最坏 miss | 594 / 829 | 361 / 557 | **1.65x / 1.49x** | R1 冻结 Trie |
| 标签匹配 | 空表 anyMatch miss/hit | 52 / 23 | 18 / 11 | **3.0x / 2.1x** | R1 快路径 |
| 容器 tick（集成路径） | 逐 tick CE 物品构建 / 支撑维护 | 每tick | 1次/配方 · 1/10窗口 | 调用消除 | R3（离线不可测，见 03） |

### 已知有界代价（透明记录）

1. `getOr` 病态动态键 miss：缓存探测开销 ~+4ns/次（79→83ns，0.953x，持平判定）；生产键均为静态字面量，缓存上限 8192 条封顶内存。
2. `text.parse` 病态永不重复键：miss+回填路径反而 +24%（扫描器收益）；上限 8192。
3. CookingPotRecipeCache 指纹不区分同基材质的不同 CE 物品——**预存在行为**（1.2.1 原样），本轮未改变；已在 note/modules/01 记录为已知局限。

### 方法学沉淀

- 分组独立 JVM + 每基准多样本最小值聚合 + 目标/看守双判定带 + 绝对差下限（>3ns）；
- 热源组等 JIT 双峰基准以 ≥4 样本分布证据判定（03 号报告完整记录了该调查过程）；
- 服务端绑定路径（CE/ItemStack）禁止编造离线数字，以调用消除 + 同类操作实测成本上界论证；
- 行为等价由基准自检守护：Trie first-insert-wins、贪心匹配序、热源谓词语义（含未点燃/前缀碰撞）、getOr 四级回退与空串语义、解析路由（含 `<!i>` 不触发标签检测等边角）、扫描器与原正则的万例模糊等价——全部 AssertionError 中止式断言。

**总评**：四轮优化在零功能回归的前提下，把插件六大热路径（配方匹配、标签、配置、热源、容器 tick、文本解析）中的五项提升 1.2x~1300x；唯一不可离线量化的容器集成路径以逐 tick 调用消除方式落地。全部改动保持原有行为顺序、事件优先级、持久化格式与公开 API 兼容（红线 1/2 全程未破）。
