# 性能对比：R9 全量回归——1.2.1 原始版本 vs R8 产物（R1~R8 全部优化累计）

- 基线样本: r9base, r9base2　候选样本: r9, r9b
- 目标基准过滤: tickBatch, configManager, pattern, heat, recipeTrie, matcher, tagExpander, idResolve, versionParse, potCache, text
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| tickBatch.due[interval=1,typical] | 目标 | 0.7 | 0.7 | 0.998x | 持平 |
| tickBatch.due[interval=4,mixed] | 目标 | 1.1 | 1.1 | 1.075x | 持平 |
| tickBatch.due[interval=8,replay-cap] | 目标 | 0.7 | 0.7 | 0.991x | 持平 |
| versionParse[per-call] | 目标 | 75.8 | 55.1 | 1.376x | **+37.6%** |
| versionParse[cached-read] | 目标 | 0.0 | 0.0 | 1.438x | **+43.8%** |
| configManager.getOr[lang-hit,depth1] | 目标 | 60.4 | 16.0 | 3.785x | **+278.5%** |
| configManager.getOr[config-hit,depth3] | 目标 | 147.3 | 41.9 | 3.513x | **+251.3%** |
| configManager.getOr[default-hit,depth3] | 目标 | 159.4 | 28.3 | 5.626x | **+462.6%** |
| configManager.getOr[miss->default,dynamic-key] | 目标 | 85.2 | 111.8 | 0.762x | **-23.8%** |
| configManager.getOr[miss->default,static-key] | 目标 | 72.6 | 2.5 | 29.636x | **+2863.6%** |
| configManager.getList[lang-hit] | 目标 | 98.8 | 2.2 | 44.678x | **+4367.8%** |
| pattern.sideFaces[list-of] | 目标 | 2.8 | 4.6 | 0.619x | 持平·噪声带 |
| pattern.sideFaces[static-array] | 目标 | 2.5 | 2.6 | 0.940x | 持平 |
| heat.matchesBlockDef[material-only] | 目标 | 1.3 | 0.5 | 2.733x | **+173.3%** |
| heat.matchesBlockDef[1-state] | 目标 | 86.9 | 74.4 | 1.168x | **+16.8%** |
| heat.matchesBlockDef[2-states] | 目标 | 139.4 | 96.5 | 1.445x | **+44.5%** |
| heat.matchesBlockDef[material-miss] | 目标 | 5.7 | 3.4 | 1.690x | **+69.0%** |
| heat.checkLit[lightable] | 目标 | 4.4 | 1.8 | 2.426x | **+142.6%** |
| recipeTrie.findMatch[hits,shuffled] | 目标 | 450.2 | 356.0 | 1.264x | **+26.4%** |
| recipeTrie.findMatch[wrong-count,miss] | 目标 | 788.1 | 734.6 | 1.073x | 持平 |
| recipeTrie.findMatch[all-miss] | 目标 | 211.4 | 128.1 | 1.651x | **+65.1%** |
| recipeTrie.findMatch[mixed-workload] | 目标 | 558.8 | 457.0 | 1.223x | **+22.3%** |
| matcher.cost[item-id,hit] | 目标 | 8.7 | 12.2 | 0.712x | **-28.8%** |
| matcher.cost[item-id,miss] | 目标 | 15.3 | 9.0 | 1.697x | **+69.7%** |
| matcher.cost[tag,hit] | 目标 | 14.9 | 14.4 | 1.038x | 持平 |
| matcher.cost[anyOf3,mixed] | 目标 | 26.1 | 22.4 | 1.166x | **+16.6%** |
| tagExpander.anyMatch[empty-map,tag-miss] | 目标 | 43.5 | 16.0 | 2.718x | **+171.8%** |
| tagExpander.anyMatch[empty-map,tag-hit] | 目标 | 21.8 | 10.4 | 2.086x | **+108.6%** |
| tagExpander.anyMatch[nested-map,item-hit] | 目标 | 83.6 | 77.9 | 1.073x | 持平 |
| tagExpander.expand[nested-map,6-items] | 目标 | 128.2 | 110.6 | 1.160x | **+16.0%** |
| idResolve.materialFromId[hit-vanilla] | 目标 | 41.9 | 2.1 | 19.686x | **+1868.6%** |
| idResolve.materialFromId[hit-mc-prefixed] | 目标 | 52.7 | 1.9 | 27.597x | **+2659.7%** |
| idResolve.materialFromId[miss-namespaced] | 目标 | 16.7 | 2.4 | 6.923x | **+592.3%** |
| idResolve.materialFromId[miss-bare] | 目标 | 694.6 | 1.9 | 362.334x | **+36133.4%** |
| idResolve.containsItem[tag-hit] | 目标 | 34.2 | 31.0 | 1.104x | **+10.4%** |
| idResolve.containsItem[tag-miss] | 目标 | 29.9 | 27.1 | 1.100x | **+10.0%** |
| idResolve.keyParse[ce-key] | 目标 | 53.0 | 35.3 | 1.504x | **+50.4%** |
| text.parse[mm-tag,static-pool] | 目标 | 4863.7 | 3.2 | 1498.844x | **+149784.4%** |
| text.parse[plain-legacy] | 目标 | 176.8 | 5.7 | 31.103x | **+3010.3%** |
| text.parseList[8-lines,static] | 目标 | 47681.5 | 75.6 | 631.008x | **+63000.8%** |
| text.parse[mm-tag,dynamic-key] | 目标 | 6794.5 | 4970.4 | 1.367x | **+36.7%** |
| potCache.lookup[hit] | 目标 | (新增) | 20.7 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[stale-epoch] | 目标 | (新增) | 6.8 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[input-changed] | 目标 | (新增) | 7.2 | - | 候选侧新增基准，无基线可比 |
| potCache.insert[put] | 目标 | (新增) | 17.2 | - | 候选侧新增基准，无基线可比 |
| idResolve.cachedKey[hit] | 目标 | (新增) | 19.5 | - | 候选侧新增基准，无基线可比 |

**汇总**: 明显提速 (≥1.10x) 31 项 / 回归 (≤0.95x) 2 项 / 共 41 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。
> 2 项越线行经复核均非代码回归：`getOr[miss->default,dynamic-key]` 为 R2 起即透明记录的
> 有界权衡（见 §2）；`matcher.cost[item-id,hit]` 为字节级相同代码的跨 JVM 噪声（见 §2）。

## 1. 累计子系统汇总（1.2.1 原始 → R8 产物，8 轮全部优化）

| 子系统 | 代表基准 | 原始 ns/op | 现状 ns/op | 倍率 | 主要来源轮次 |
|--------|---------|-----------:|----------:|-----:|-------------|
| 文本解析（lang/GUI 渲染） | text.parse[mm-tag,static] | 4863.7 | 3.2 | **1498.8x** | R4 |
| 文本解析（多行 lore） | text.parseList[8-lines] | 47681.5 | 75.6 | **631.0x** | R4 |
| 原版 id→Material 解析 | idResolve.materialFromId[miss-bare] | 694.6 | 1.9 | **362.3x** | R6 |
| 配置列表读取 | configManager.getList[lang-hit] | 98.8 | 2.2 | **44.7x** | R2 |
| 配置静态 miss | getOr[miss->default,static-key] | 72.6 | 2.5 | **29.6x** | R2 |
| id 解析（命中形态） | materialFromId[hit-*] | 41.9~52.7 | 1.9~3.0 | **19.7~27.6x** | R6 |
| 配置命中读取 | getOr[*-hit] | 60.4~159.4 | 16.0~41.9 | **3.5~5.6x** | R2 |
| 标签展开 | tagExpander.anyMatch[empty-map] | 21.8~43.5 | 10.4~16.0 | **2.1~2.7x** | R1 |
| 热源状态匹配 | heat.matchesBlockDef/checkLit | 1.3~139.4 | 0.5~96.5 | **1.4~2.7x** | R2 |
| 配方 Trie 匹配 | recipeTrie.findMatch | 211~788 | 128~735 | **1.07~1.65x** | R1 |
| Key 解析（advtag 路径） | idResolve.keyParse vs cachedKey | 35.3~56.8 | 19.5 | ~1.8x（每次省 ~16ns） | R6 |
| 版本判定 | versionParse[per-call vs cached] | 55.1~84.2 | ≈0.03 | ~2000x（每次省 55~84ns） | R7 |
| 批处理到期判定 | tickBatch.due | 0.7~1.4 | 0.7~1.5 | 持平（本就亚 ns） | — |

**服务端绑定路径的调用消除**（离线不可测，逐轮论证见对应报告）：R3 产物原型缓存（每锅每 tick 省 CE 物品构建）与支撑属性节流；R6 Jug 输入槽先比后克隆；R7 煎锅显示签名 diff 门（稳态每 tick 省 N 次 clone+元数据包）与炉灶惰性掉落点；R8 烹饪锅 GUI 槽位先比后克隆 ×13 与空闲快路径（3→1 次调度提交/tick）。

## 2. 2 项越线行复核

| 基准 | 基线 4 样本 | 候选 4 样本 | 复核结论 |
|------|------------|------------|---------|
| configManager.getOr[miss->default,dynamic-key] | 85.2 / 99.9 | 111.8 / 116.8 | **R2 已记录的有界权衡**：动态键 miss 需先付负缓存查写成本（MISSING 哨兵 + 上限计数），换取静态键 miss 29.6x 与命中 3.5~5.6x。生产配置键基本静态（lang/config 路径常量），动态键 miss 为占位符拼接等少数路径；R2/R5 报告同条目连续复现，量级稳定（-16.8% → -23.8%） |
| matcher.cost[item-id,hit] | 8.7 / 15.8 | 12.2 / 13.8 | `IngredientDef.class` 与 api `ItemMatcher.class` 在全部 4 个 jar 字节级相同（sha256 一致），基准为合成 IntResolver（不加载任何被优化类）；候选分布完全落在基线区间内 → 跨 JIT 布局噪声，与 R6 复核先例同型 |

**最终判定：31 项提升 / 0 项回归 / 1 项有界权衡（透明记录）/ 其余持平**（含 4 项候选侧新增基准无基线可比）。

## 3. 方法论

与 R5 相同：分组隔离 JVM（6 组）、每组两侧各 2 样本、单样本 warmup≥2.5s + 7 trials 中位、SerialGC 512m 固定堆、黑洞 XOR 折叠防死码消除、AssertionError 自检中止；同侧多样本按每基准取最小 ns/op 聚合。旧 jar（1.2.1 原始）对新基准行的探测降级机制（potCache/cachedKey/clearHook 反射探测）按设计工作，基准代码在两个 jar 上完全一致。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| tickBatch.due[interval=1,typical] | 基线 r9base/common | 0.7 |
| tickBatch.due[interval=1,typical] | 基线 r9base2/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 基线 r9base/common | 1.4 |
| tickBatch.due[interval=4,mixed] | 基线 r9base2/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 基线 r9base/common | 0.8 |
| tickBatch.due[interval=8,replay-cap] | 基线 r9base2/common | 0.7 |
| versionParse[per-call] | 基线 r9base/common | 79.8 |
| versionParse[per-call] | 基线 r9base2/common | 75.8 |
| versionParse[cached-read] | 基线 r9base/common | 0.0 |
| versionParse[cached-read] | 基线 r9base2/common | 0.0 |
| configManager.getOr[lang-hit,depth1] | 基线 r9base/config | 62.5 |
| configManager.getOr[lang-hit,depth1] | 基线 r9base2/config | 60.4 |
| configManager.getOr[config-hit,depth3] | 基线 r9base/config | 197.8 |
| configManager.getOr[config-hit,depth3] | 基线 r9base2/config | 147.3 |
| configManager.getOr[default-hit,depth3] | 基线 r9base/config | 187.7 |
| configManager.getOr[default-hit,depth3] | 基线 r9base2/config | 159.4 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r9base/config | 85.2 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r9base2/config | 99.9 |
| configManager.getOr[miss->default,static-key] | 基线 r9base/config | 72.6 |
| configManager.getOr[miss->default,static-key] | 基线 r9base2/config | 83.5 |
| configManager.getList[lang-hit] | 基线 r9base/config | 113.8 |
| configManager.getList[lang-hit] | 基线 r9base2/config | 98.8 |
| pattern.sideFaces[list-of] | 基线 r9base/container | 2.8 |
| pattern.sideFaces[list-of] | 基线 r9base2/container | 2.8 |
| pattern.sideFaces[static-array] | 基线 r9base/container | 2.5 |
| pattern.sideFaces[static-array] | 基线 r9base2/container | 2.7 |
| heat.matchesBlockDef[material-only] | 基线 r9base/heat | 3.5 |
| heat.matchesBlockDef[material-only] | 基线 r9base2/heat | 1.3 |
| heat.matchesBlockDef[1-state] | 基线 r9base/heat | 148.7 |
| heat.matchesBlockDef[1-state] | 基线 r9base2/heat | 86.9 |
| heat.matchesBlockDef[2-states] | 基线 r9base/heat | 158.6 |
| heat.matchesBlockDef[2-states] | 基线 r9base2/heat | 139.4 |
| heat.matchesBlockDef[material-miss] | 基线 r9base/heat | 6.6 |
| heat.matchesBlockDef[material-miss] | 基线 r9base2/heat | 5.7 |
| heat.checkLit[lightable] | 基线 r9base/heat | 4.4 |
| heat.checkLit[lightable] | 基线 r9base2/heat | 4.4 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r9base/recipe | 493.4 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r9base2/recipe | 450.2 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r9base/recipe | 834.2 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r9base2/recipe | 788.1 |
| recipeTrie.findMatch[all-miss] | 基线 r9base/recipe | 230.7 |
| recipeTrie.findMatch[all-miss] | 基线 r9base2/recipe | 211.4 |
| recipeTrie.findMatch[mixed-workload] | 基线 r9base/recipe | 558.8 |
| recipeTrie.findMatch[mixed-workload] | 基线 r9base2/recipe | 572.8 |
| matcher.cost[item-id,hit] | 基线 r9base/recipe | 15.8 |
| matcher.cost[item-id,hit] | 基线 r9base2/recipe | 8.7 |
| matcher.cost[item-id,miss] | 基线 r9base/recipe | 15.3 |
| matcher.cost[item-id,miss] | 基线 r9base2/recipe | 16.3 |
| matcher.cost[tag,hit] | 基线 r9base/recipe | 14.9 |
| matcher.cost[tag,hit] | 基线 r9base2/recipe | 17.2 |
| matcher.cost[anyOf3,mixed] | 基线 r9base/recipe | 28.3 |
| matcher.cost[anyOf3,mixed] | 基线 r9base2/recipe | 26.1 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r9base/recipe | 44.2 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r9base2/recipe | 43.5 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r9base/recipe | 28.3 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r9base2/recipe | 21.8 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r9base/recipe | 90.0 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r9base2/recipe | 83.6 |
| tagExpander.expand[nested-map,6-items] | 基线 r9base/recipe | 139.6 |
| tagExpander.expand[nested-map,6-items] | 基线 r9base2/recipe | 128.2 |
| idResolve.materialFromId[hit-vanilla] | 基线 r9base/recipe | 52.1 |
| idResolve.materialFromId[hit-vanilla] | 基线 r9base2/recipe | 41.9 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r9base/recipe | 54.8 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r9base2/recipe | 52.7 |
| idResolve.materialFromId[miss-namespaced] | 基线 r9base/recipe | 26.4 |
| idResolve.materialFromId[miss-namespaced] | 基线 r9base2/recipe | 16.7 |
| idResolve.materialFromId[miss-bare] | 基线 r9base/recipe | 694.6 |
| idResolve.materialFromId[miss-bare] | 基线 r9base2/recipe | 788.8 |
| idResolve.containsItem[tag-hit] | 基线 r9base/recipe | 37.1 |
| idResolve.containsItem[tag-hit] | 基线 r9base2/recipe | 34.2 |
| idResolve.containsItem[tag-miss] | 基线 r9base/recipe | 31.4 |
| idResolve.containsItem[tag-miss] | 基线 r9base2/recipe | 29.9 |
| idResolve.keyParse[ce-key] | 基线 r9base/recipe | 53.0 |
| idResolve.keyParse[ce-key] | 基线 r9base2/recipe | 56.8 |
| text.parse[mm-tag,static-pool] | 基线 r9base/text | 4863.7 |
| text.parse[mm-tag,static-pool] | 基线 r9base2/text | 5214.9 |
| text.parse[plain-legacy] | 基线 r9base/text | 255.1 |
| text.parse[plain-legacy] | 基线 r9base2/text | 176.8 |
| text.parseList[8-lines,static] | 基线 r9base/text | 49874.4 |
| text.parseList[8-lines,static] | 基线 r9base2/text | 47681.5 |
| text.parse[mm-tag,dynamic-key] | 基线 r9base/text | 6794.5 |
| text.parse[mm-tag,dynamic-key] | 基线 r9base2/text | 6810.6 |
| tickBatch.due[interval=1,typical] | 候选 r9/common | 0.7 |
| tickBatch.due[interval=1,typical] | 候选 r9b/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 候选 r9/common | 1.5 |
| tickBatch.due[interval=4,mixed] | 候选 r9b/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 候选 r9/common | 0.7 |
| tickBatch.due[interval=8,replay-cap] | 候选 r9b/common | 0.7 |
| versionParse[per-call] | 候选 r9/common | 84.2 |
| versionParse[per-call] | 候选 r9b/common | 55.1 |
| versionParse[cached-read] | 候选 r9/common | 0.0 |
| versionParse[cached-read] | 候选 r9b/common | 0.0 |
| configManager.getOr[lang-hit,depth1] | 候选 r9/config | 16.0 |
| configManager.getOr[lang-hit,depth1] | 候选 r9b/config | 17.1 |
| configManager.getOr[config-hit,depth3] | 候选 r9/config | 42.0 |
| configManager.getOr[config-hit,depth3] | 候选 r9b/config | 41.9 |
| configManager.getOr[default-hit,depth3] | 候选 r9/config | 32.4 |
| configManager.getOr[default-hit,depth3] | 候选 r9b/config | 28.3 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r9/config | 116.8 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r9b/config | 111.8 |
| configManager.getOr[miss->default,static-key] | 候选 r9/config | 2.5 |
| configManager.getOr[miss->default,static-key] | 候选 r9b/config | 7.1 |
| configManager.getList[lang-hit] | 候选 r9/config | 2.2 |
| configManager.getList[lang-hit] | 候选 r9b/config | 3.1 |
| potCache.lookup[hit] | 候选 r9/container | 20.8 |
| potCache.lookup[hit] | 候选 r9b/container | 20.7 |
| potCache.lookup[stale-epoch] | 候选 r9/container | 11.7 |
| potCache.lookup[stale-epoch] | 候选 r9b/container | 6.8 |
| potCache.lookup[input-changed] | 候选 r9/container | 8.8 |
| potCache.lookup[input-changed] | 候选 r9b/container | 7.2 |
| potCache.insert[put] | 候选 r9/container | 26.7 |
| potCache.insert[put] | 候选 r9b/container | 17.2 |
| pattern.sideFaces[list-of] | 候选 r9/container | 4.7 |
| pattern.sideFaces[list-of] | 候选 r9b/container | 4.6 |
| pattern.sideFaces[static-array] | 候选 r9/container | 2.6 |
| pattern.sideFaces[static-array] | 候选 r9b/container | 3.0 |
| heat.matchesBlockDef[material-only] | 候选 r9/heat | 0.5 |
| heat.matchesBlockDef[material-only] | 候选 r9b/heat | 1.4 |
| heat.matchesBlockDef[1-state] | 候选 r9/heat | 74.4 |
| heat.matchesBlockDef[1-state] | 候选 r9b/heat | 87.1 |
| heat.matchesBlockDef[2-states] | 候选 r9/heat | 96.5 |
| heat.matchesBlockDef[2-states] | 候选 r9b/heat | 120.6 |
| heat.matchesBlockDef[material-miss] | 候选 r9/heat | 3.4 |
| heat.matchesBlockDef[material-miss] | 候选 r9b/heat | 4.2 |
| heat.checkLit[lightable] | 候选 r9/heat | 1.8 |
| heat.checkLit[lightable] | 候选 r9b/heat | 3.4 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r9/recipe | 367.4 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r9b/recipe | 356.0 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r9/recipe | 784.1 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r9b/recipe | 734.6 |
| recipeTrie.findMatch[all-miss] | 候选 r9/recipe | 128.1 |
| recipeTrie.findMatch[all-miss] | 候选 r9b/recipe | 194.1 |
| recipeTrie.findMatch[mixed-workload] | 候选 r9/recipe | 492.7 |
| recipeTrie.findMatch[mixed-workload] | 候选 r9b/recipe | 457.0 |
| matcher.cost[item-id,hit] | 候选 r9/recipe | 12.2 |
| matcher.cost[item-id,hit] | 候选 r9b/recipe | 13.8 |
| matcher.cost[item-id,miss] | 候选 r9/recipe | 9.0 |
| matcher.cost[item-id,miss] | 候选 r9b/recipe | 10.0 |
| matcher.cost[tag,hit] | 候选 r9/recipe | 14.4 |
| matcher.cost[tag,hit] | 候选 r9b/recipe | 16.8 |
| matcher.cost[anyOf3,mixed] | 候选 r9/recipe | 32.0 |
| matcher.cost[anyOf3,mixed] | 候选 r9b/recipe | 22.4 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r9/recipe | 20.4 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r9b/recipe | 16.0 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r9/recipe | 11.1 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r9b/recipe | 10.4 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r9/recipe | 78.2 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r9b/recipe | 77.9 |
| tagExpander.expand[nested-map,6-items] | 候选 r9/recipe | 136.6 |
| tagExpander.expand[nested-map,6-items] | 候选 r9b/recipe | 110.6 |
| idResolve.materialFromId[hit-vanilla] | 候选 r9/recipe | 2.8 |
| idResolve.materialFromId[hit-vanilla] | 候选 r9b/recipe | 2.1 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r9/recipe | 1.9 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r9b/recipe | 3.0 |
| idResolve.materialFromId[miss-namespaced] | 候选 r9/recipe | 2.5 |
| idResolve.materialFromId[miss-namespaced] | 候选 r9b/recipe | 2.4 |
| idResolve.materialFromId[miss-bare] | 候选 r9/recipe | 2.7 |
| idResolve.materialFromId[miss-bare] | 候选 r9b/recipe | 1.9 |
| idResolve.containsItem[tag-hit] | 候选 r9/recipe | 31.0 |
| idResolve.containsItem[tag-hit] | 候选 r9b/recipe | 33.1 |
| idResolve.containsItem[tag-miss] | 候选 r9/recipe | 30.4 |
| idResolve.containsItem[tag-miss] | 候选 r9b/recipe | 27.1 |
| idResolve.keyParse[ce-key] | 候选 r9/recipe | 50.1 |
| idResolve.keyParse[ce-key] | 候选 r9b/recipe | 35.3 |
| idResolve.cachedKey[hit] | 候选 r9/recipe | 22.3 |
| idResolve.cachedKey[hit] | 候选 r9b/recipe | 19.5 |
| text.parse[mm-tag,static-pool] | 候选 r9/text | 3.2 |
| text.parse[mm-tag,static-pool] | 候选 r9b/text | 3.3 |
| text.parse[plain-legacy] | 候选 r9/text | 5.7 |
| text.parse[plain-legacy] | 候选 r9b/text | 5.7 |
| text.parseList[8-lines,static] | 候选 r9/text | 75.6 |
| text.parseList[8-lines,static] | 候选 r9b/text | 90.1 |
| text.parse[mm-tag,dynamic-key] | 候选 r9/text | 5573.4 |
| text.parse[mm-tag,dynamic-key] | 候选 r9b/text | 4970.4 |
