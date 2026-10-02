# 性能对比：R12 收尾全量回归——1.2.1 原始版本 vs R10 最终产物（R1~R10 全部优化）

- 基线样本: r9base, r9base2　候选样本: r10, r10b
- 目标基准过滤: tickBatch, configManager, pattern, heat, recipeTrie, matcher, tagExpander, idResolve, versionParse, potCache, text
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| tickBatch.due[interval=1,typical] | 目标 | 0.7 | 0.6 | 1.014x | 持平 |
| tickBatch.due[interval=4,mixed] | 目标 | 1.1 | 1.1 | 0.996x | 持平 |
| tickBatch.due[interval=8,replay-cap] | 目标 | 0.7 | 0.8 | 0.845x | 持平·噪声带 |
| versionParse[per-call] | 目标 | 75.8 | 64.8 | 1.171x | **+17.1%** |
| versionParse[cached-read] | 目标 | 0.0 | 0.0 | 1.533x | **+53.3%** |
| configManager.getOr[lang-hit,depth1] | 目标 | 60.4 | 15.8 | 3.830x | **+283.0%** |
| configManager.getOr[config-hit,depth3] | 目标 | 147.3 | 39.7 | 3.708x | **+270.8%** |
| configManager.getOr[default-hit,depth3] | 目标 | 159.4 | 27.8 | 5.724x | **+472.4%** |
| configManager.getOr[miss->default,dynamic-key] | 目标 | 85.2 | 83.0 | 1.026x | 持平 |
| configManager.getOr[miss->default,static-key] | 目标 | 72.6 | 2.5 | 29.456x | **+2845.6%** |
| configManager.getList[lang-hit] | 目标 | 98.8 | 2.2 | 45.564x | **+4456.4%** |
| pattern.sideFaces[list-of] | 目标 | 2.8 | 2.9 | 0.976x | 持平 |
| pattern.sideFaces[static-array] | 目标 | 2.5 | 2.2 | 1.138x | **+13.8%** |
| heat.matchesBlockDef[material-only] | 目标 | 1.3 | 1.3 | 0.990x | 持平 |
| heat.matchesBlockDef[1-state] | 目标 | 86.9 | 66.4 | 1.310x | **+31.0%** |
| heat.matchesBlockDef[2-states] | 目标 | 139.4 | 115.5 | 1.207x | **+20.7%** |
| heat.matchesBlockDef[material-miss] | 目标 | 5.7 | 3.4 | 1.688x | **+68.8%** |
| heat.checkLit[lightable] | 目标 | 4.4 | 2.0 | 2.228x | **+122.8%** |
| recipeTrie.findMatch[hits,shuffled] | 目标 | 450.2 | 318.8 | 1.412x | **+41.2%** |
| recipeTrie.findMatch[wrong-count,miss] | 目标 | 788.1 | 734.7 | 1.073x | 持平 |
| recipeTrie.findMatch[all-miss] | 目标 | 211.4 | 152.4 | 1.387x | **+38.7%** |
| recipeTrie.findMatch[mixed-workload] | 目标 | 558.8 | 444.1 | 1.258x | **+25.8%** |
| matcher.cost[item-id,hit] | 目标 | 8.7 | 9.0 | 0.967x | 持平 |
| matcher.cost[item-id,miss] | 目标 | 15.3 | 14.7 | 1.042x | 持平 |
| matcher.cost[tag,hit] | 目标 | 14.9 | 10.5 | 1.414x | **+41.4%** |
| matcher.cost[anyOf3,mixed] | 目标 | 26.1 | 21.1 | 1.238x | **+23.8%** |
| tagExpander.anyMatch[empty-map,tag-miss] | 目标 | 43.5 | 16.6 | 2.615x | **+161.5%** |
| tagExpander.anyMatch[empty-map,tag-hit] | 目标 | 21.8 | 16.1 | 1.351x | **+35.1%** |
| tagExpander.anyMatch[nested-map,item-hit] | 目标 | 83.6 | 93.8 | 0.892x | **-10.8%** |
| tagExpander.expand[nested-map,6-items] | 目标 | 128.2 | 107.8 | 1.190x | **+19.0%** |
| idResolve.materialFromId[hit-vanilla] | 目标 | 41.9 | 3.0 | 13.819x | **+1281.9%** |
| idResolve.materialFromId[hit-mc-prefixed] | 目标 | 52.7 | 2.9 | 18.135x | **+1713.5%** |
| idResolve.materialFromId[miss-namespaced] | 目标 | 16.7 | 2.6 | 6.504x | **+550.4%** |
| idResolve.materialFromId[miss-bare] | 目标 | 694.6 | 2.6 | 272.177x | **+27117.7%** |
| idResolve.containsItem[tag-hit] | 目标 | 34.2 | 27.4 | 1.248x | **+24.8%** |
| idResolve.containsItem[tag-miss] | 目标 | 29.9 | 22.5 | 1.326x | **+32.6%** |
| idResolve.keyParse[ce-key] | 目标 | 53.0 | 38.8 | 1.366x | **+36.6%** |
| text.parse[mm-tag,static-pool] | 目标 | 4863.7 | 3.2 | 1496.998x | **+149599.8%** |
| text.parse[plain-legacy] | 目标 | 176.8 | 5.3 | 33.636x | **+3263.6%** |
| text.parseList[8-lines,static] | 目标 | 47681.5 | 81.8 | 582.754x | **+58175.4%** |
| text.parse[mm-tag,dynamic-key] | 目标 | 6794.5 | 4337.3 | 1.567x | **+56.7%** |
| potCache.lookup[hit] | 目标 | (新增) | 20.6 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[stale-epoch] | 目标 | (新增) | 7.5 | - | 候选侧新增基准，无基线可比 |
| potCache.lookup[input-changed] | 目标 | (新增) | 9.0 | - | 候选侧新增基准，无基线可比 |
| potCache.insert[put] | 目标 | (新增) | 17.4 | - | 候选侧新增基准，无基线可比 |
| idResolve.idCompare[toString-equals] | 目标 | (新增) | 10.7 | - | 候选侧新增基准，无基线可比 |
| idResolve.idCompare[split-compare] | 目标 | (新增) | 5.9 | - | 候选侧新增基准，无基线可比 |
| idResolve.cachedKey[hit] | 目标 | (新增) | 16.0 | - | 候选侧新增基准，无基线可比 |

**汇总**: 明显提速 (≥1.10x) 31 项 / 回归 (≤0.95x) 1 项 / 共 41 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。

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
| tickBatch.due[interval=1,typical] | 候选 r10/common | 0.6 |
| tickBatch.due[interval=1,typical] | 候选 r10b/common | 0.6 |
| tickBatch.due[interval=4,mixed] | 候选 r10/common | 1.2 |
| tickBatch.due[interval=4,mixed] | 候选 r10b/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 候选 r10/common | 0.8 |
| tickBatch.due[interval=8,replay-cap] | 候选 r10b/common | 0.8 |
| versionParse[per-call] | 候选 r10/common | 68.6 |
| versionParse[per-call] | 候选 r10b/common | 64.8 |
| versionParse[cached-read] | 候选 r10/common | 0.0 |
| versionParse[cached-read] | 候选 r10b/common | 0.0 |
| configManager.getOr[lang-hit,depth1] | 候选 r10/config | 16.0 |
| configManager.getOr[lang-hit,depth1] | 候选 r10b/config | 15.8 |
| configManager.getOr[config-hit,depth3] | 候选 r10/config | 40.0 |
| configManager.getOr[config-hit,depth3] | 候选 r10b/config | 39.7 |
| configManager.getOr[default-hit,depth3] | 候选 r10/config | 27.8 |
| configManager.getOr[default-hit,depth3] | 候选 r10b/config | 29.5 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r10/config | 83.0 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r10b/config | 91.7 |
| configManager.getOr[miss->default,static-key] | 候选 r10/config | 2.5 |
| configManager.getOr[miss->default,static-key] | 候选 r10b/config | 4.7 |
| configManager.getList[lang-hit] | 候选 r10/config | 3.2 |
| configManager.getList[lang-hit] | 候选 r10b/config | 2.2 |
| potCache.lookup[hit] | 候选 r10/container | 21.3 |
| potCache.lookup[hit] | 候选 r10b/container | 20.6 |
| potCache.lookup[stale-epoch] | 候选 r10/container | 8.2 |
| potCache.lookup[stale-epoch] | 候选 r10b/container | 7.5 |
| potCache.lookup[input-changed] | 候选 r10/container | 12.4 |
| potCache.lookup[input-changed] | 候选 r10b/container | 9.0 |
| potCache.insert[put] | 候选 r10/container | 23.2 |
| potCache.insert[put] | 候选 r10b/container | 17.4 |
| pattern.sideFaces[list-of] | 候选 r10/container | 2.9 |
| pattern.sideFaces[list-of] | 候选 r10b/container | 3.1 |
| pattern.sideFaces[static-array] | 候选 r10/container | 2.2 |
| pattern.sideFaces[static-array] | 候选 r10b/container | 2.8 |
| heat.matchesBlockDef[material-only] | 候选 r10/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 候选 r10b/heat | 1.3 |
| heat.matchesBlockDef[1-state] | 候选 r10/heat | 66.4 |
| heat.matchesBlockDef[1-state] | 候选 r10b/heat | 68.7 |
| heat.matchesBlockDef[2-states] | 候选 r10/heat | 115.5 |
| heat.matchesBlockDef[2-states] | 候选 r10b/heat | 119.0 |
| heat.matchesBlockDef[material-miss] | 候选 r10/heat | 6.0 |
| heat.matchesBlockDef[material-miss] | 候选 r10b/heat | 3.4 |
| heat.checkLit[lightable] | 候选 r10/heat | 4.2 |
| heat.checkLit[lightable] | 候选 r10b/heat | 2.0 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r10/recipe | 325.3 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r10b/recipe | 318.8 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r10/recipe | 734.7 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r10b/recipe | 771.2 |
| recipeTrie.findMatch[all-miss] | 候选 r10/recipe | 179.6 |
| recipeTrie.findMatch[all-miss] | 候选 r10b/recipe | 152.4 |
| recipeTrie.findMatch[mixed-workload] | 候选 r10/recipe | 545.4 |
| recipeTrie.findMatch[mixed-workload] | 候选 r10b/recipe | 444.1 |
| matcher.cost[item-id,hit] | 候选 r10/recipe | 13.9 |
| matcher.cost[item-id,hit] | 候选 r10b/recipe | 9.0 |
| matcher.cost[item-id,miss] | 候选 r10/recipe | 14.7 |
| matcher.cost[item-id,miss] | 候选 r10b/recipe | 15.0 |
| matcher.cost[tag,hit] | 候选 r10/recipe | 10.5 |
| matcher.cost[tag,hit] | 候选 r10b/recipe | 16.0 |
| matcher.cost[anyOf3,mixed] | 候选 r10/recipe | 21.1 |
| matcher.cost[anyOf3,mixed] | 候选 r10b/recipe | 33.2 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r10/recipe | 16.6 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r10b/recipe | 25.8 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r10/recipe | 16.1 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r10b/recipe | 16.5 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r10/recipe | 93.8 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r10b/recipe | 94.4 |
| tagExpander.expand[nested-map,6-items] | 候选 r10/recipe | 150.0 |
| tagExpander.expand[nested-map,6-items] | 候选 r10b/recipe | 107.8 |
| idResolve.materialFromId[hit-vanilla] | 候选 r10/recipe | 3.0 |
| idResolve.materialFromId[hit-vanilla] | 候选 r10b/recipe | 3.2 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r10/recipe | 2.9 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r10b/recipe | 3.3 |
| idResolve.materialFromId[miss-namespaced] | 候选 r10/recipe | 2.6 |
| idResolve.materialFromId[miss-namespaced] | 候选 r10b/recipe | 2.6 |
| idResolve.materialFromId[miss-bare] | 候选 r10/recipe | 2.6 |
| idResolve.materialFromId[miss-bare] | 候选 r10b/recipe | 2.7 |
| idResolve.containsItem[tag-hit] | 候选 r10/recipe | 27.4 |
| idResolve.containsItem[tag-hit] | 候选 r10b/recipe | 35.3 |
| idResolve.containsItem[tag-miss] | 候选 r10/recipe | 32.2 |
| idResolve.containsItem[tag-miss] | 候选 r10b/recipe | 22.5 |
| idResolve.keyParse[ce-key] | 候选 r10/recipe | 38.8 |
| idResolve.keyParse[ce-key] | 候选 r10b/recipe | 56.7 |
| idResolve.idCompare[toString-equals] | 候选 r10/recipe | 11.0 |
| idResolve.idCompare[toString-equals] | 候选 r10b/recipe | 10.7 |
| idResolve.idCompare[split-compare] | 候选 r10/recipe | 6.3 |
| idResolve.idCompare[split-compare] | 候选 r10b/recipe | 5.9 |
| idResolve.cachedKey[hit] | 候选 r10/recipe | 21.2 |
| idResolve.cachedKey[hit] | 候选 r10b/recipe | 16.0 |
| text.parse[mm-tag,static-pool] | 候选 r10/text | 5.0 |
| text.parse[mm-tag,static-pool] | 候选 r10b/text | 3.2 |
| text.parse[plain-legacy] | 候选 r10/text | 5.8 |
| text.parse[plain-legacy] | 候选 r10b/text | 5.3 |
| text.parseList[8-lines,static] | 候选 r10/text | 81.8 |
| text.parseList[8-lines,static] | 候选 r10b/text | 98.2 |
| text.parse[mm-tag,dynamic-key] | 候选 r10/text | 4573.1 |
| text.parse[mm-tag,dynamic-key] | 候选 r10b/text | 4337.3 |

## 分析：唯一标记行的字节级与分布级核查

`tagExpander.anyMatch[nested-map,item-hit]` 被标记 -10.8%（83.6 → 93.8）。该类在本轮战役的 R1 重写过（commit 1aeea48，新增空映射快速路径），基线与候选字节不同，不能直接套用"字节一致=噪声"判据，改用跨代分布核查：

**候选侧字节完全一致**（sha256 `6fb252d3…`，r9/r9b/r10/r10b 四个 jar 逐字节相同，R9 与 R12 均测同一份字节码）的全部历史样本：

| 样本来源 | ns/op |
|------|------:|
| r4/recipe | 62.6 |
| r4b/recipe | 68.5 |
| r6base/recipe（=r4 字节） | 74.6 |
| r6base2/recipe | 70.6 |
| r6/recipe | 94.7 |
| r6b/recipe | 73.9 |
| r9/recipe | 78.2 |
| r9b/recipe | 77.9 |
| r10/recipe（本轮） | 93.8 |
| r10b/recipe（本轮） | 94.4 |

同一字节码跨 JVM 运行跨度 **62.6–94.7 ns（32.1 ns）**，远超被标记的 10.2 ns 差值；单次运行内部 cv 0.06–0.15、min 58.9 / max 104.3，呈双峰分布。基线侧（原始字节，8 个样本）跨度 77.5–90.0 ns，与候选分布完全重叠。R9 同一份候选字节测得 77.9（当时判持平偏正向），本轮 JVM 恰好落在高档峰位；中位数取 2 样本无法区分该双峰。**结论：噪声带内，非代码回归**——同族其余三行（empty-map miss +161.5%、empty-map hit +35.1%、expand +19.0%）正是 R1 重写的目标路径，全部显著正向。

## 战役总结（R1–R12，1.2.1 原始 vs R10 最终产物）

| 子系统 | 优化内容（轮次） | 最终幅度 |
|------|------|------|
| 文本解析 MiniMessage | 静态键缓存池（R3/R5） | parse 1497.0x / parseList 582.8x（动态键路径仍 +56.7%） |
| 配置读取 ConfigManager | 预合并扁平键 + 双缓存（R2） | static-key 29.5x / getList 45.6x；动态键 miss 为透明有界代价（持平，-2.6% 噪声带内） |
| ID 解析 | materialFromId 缓存（R6）+ Key 拆分比较（R10） | 13.8x–272.2x；containsItem +24.8%~+32.6% |
| 版本判定 | versionAtLeast 惰性缓存（R7） | per-call +17.1%，缓存读 +53.3% |
| 热源匹配 | matchesBlockDef 预解析（R4） | 1-state +31.0% / 2-states +20.7% / material-miss +68.8% / checkLit +122.8% |
| 配方匹配 | RecipeTrie 前缀树（R1/R4） | hits +41.2% / all-miss +38.7% / mixed +25.8% |
| 标签展开 | TagExpander 空映射快速路径（R1） | miss +161.5% / hit +35.1% / expand +19.0% |
| 匹配器成本 | tag 路径缓存 Key（R6）+ anyOf 短路（R1） | tag,hit +41.4% / anyOf3 +23.8% |
| 周期任务 | tickBatch 批处理（R1）+ 会话空闲快路径（R7/R8：煎锅显示差分、炖锅 idleNoHopper、加热态门控） | 0.6–1.1 ns/op 量级（持平，其收益在调度次数而非单次判定） |
| 附带产物 | 装水罐 compare-before-clone（R6）、锅刷差分、TimedEffect 标题秒级缓存（R10） | 不在离线基准覆盖内，逻辑等价性由自检+代码审查保证 |

- **31 项可测显著提速，0 项实锤回归**（唯一标记行经跨代分布核查为同字节码双峰 JIT 噪声）。
- 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。
- 红线核验：无功能删除、无语义变更（等价性证明见各轮报告：Key.toString 拆分比较 8 语料 + 10k 种子模糊测试、jug/pot 防御性克隆差分门控、bossbar 标题秒级粒度与 ticks/20 对齐）。
- 产物一致性：`dist/PapersDelight-1.2.1-CE.jar` ≡ `build/libs/…` ≡ `benchmark/lib/r10/plugin.jar`（sha256 前缀 14b48d6205b6432a）。
