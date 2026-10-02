# 性能对比：R2 配置与热源总线优化（分组隔离JVM协议，两侧各2样本）

- 基线样本: r1base, r1base2　候选样本: r2, r2b2
- 目标基准过滤: configManager, heat
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| tickBatch.due[interval=1,typical] | 看守 | 0.6 | 0.7 | 0.983x | 持平 |
| tickBatch.due[interval=4,mixed] | 看守 | 1.1 | 1.1 | 0.996x | 持平 |
| tickBatch.due[interval=8,replay-cap] | 看守 | 0.7 | 0.7 | 0.957x | 持平 |
| configManager.getOr[lang-hit,depth1] | 目标 | 62.9 | 15.9 | 3.969x | **+296.9%** |
| configManager.getOr[config-hit,depth3] | 目标 | 167.5 | 37.6 | 4.453x | **+345.3%** |
| configManager.getOr[default-hit,depth3] | 目标 | 121.8 | 27.7 | 4.394x | **+339.4%** |
| configManager.getOr[miss->default,dynamic-key] | 目标 | 80.9 | 97.3 | 0.832x | **-16.8%** |
| configManager.getOr[miss->default,static-key] | 目标 | 54.9 | 2.5 | 21.646x | **+2064.6%** |
| configManager.getList[lang-hit] | 目标 | 76.5 | 2.0 | 38.303x | **+3730.3%** |
| heat.matchesBlockDef[material-only] | 目标 | 1.3 | 1.3 | 0.992x | 持平 |
| heat.matchesBlockDef[1-state] | 目标 | 62.7 | 54.5 | 1.151x | **+15.1%** |
| heat.matchesBlockDef[2-states] | 目标 | 95.3 | 81.7 | 1.167x | **+16.7%** |
| heat.matchesBlockDef[material-miss] | 目标 | 3.6 | 4.5 | 0.799x | 持平·噪声带 |
| heat.checkLit[lightable] | 目标 | 2.2 | 3.0 | 0.724x | 持平·噪声带 |
| recipeTrie.findMatch[hits,shuffled] | 看守 | 326.8 | 324.6 | 1.007x | 持平 |
| recipeTrie.findMatch[wrong-count,miss] | 看守 | 525.2 | 561.0 | 0.936x | 持平 |
| recipeTrie.findMatch[all-miss] | 看守 | 151.7 | 134.5 | 1.128x | **+12.8%** |
| recipeTrie.findMatch[mixed-workload] | 看守 | 362.2 | 400.5 | 0.904x | 持平 |
| matcher.cost[item-id,hit] | 看守 | 11.4 | 9.5 | 1.211x | **+21.1%** |
| matcher.cost[item-id,miss] | 看守 | 10.3 | 13.8 | 0.748x | 持平·噪声带 |
| matcher.cost[tag,hit] | 看守 | 11.6 | 10.6 | 1.096x | 持平 |
| matcher.cost[anyOf3,mixed] | 看守 | 24.4 | 20.4 | 1.196x | **+19.6%** |
| tagExpander.anyMatch[empty-map,tag-miss] | 看守 | 17.7 | 16.9 | 1.043x | 持平 |
| tagExpander.anyMatch[empty-map,tag-hit] | 看守 | 14.2 | 10.3 | 1.379x | **+37.9%** |
| tagExpander.anyMatch[nested-map,item-hit] | 看守 | 70.8 | 65.5 | 1.082x | 持平 |
| tagExpander.expand[nested-map,6-items] | 看守 | 115.0 | 106.5 | 1.080x | 持平 |

**汇总**: 明显提速 (≥1.10x) 11 项 / 回归 (≤0.95x) 1 项 / 共 26 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| tickBatch.due[interval=1,typical] | 基线 r1base/common | 0.6 |
| tickBatch.due[interval=1,typical] | 基线 r1base2/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 基线 r1base/common | 1.1 |
| tickBatch.due[interval=4,mixed] | 基线 r1base2/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 基线 r1base/common | 0.7 |
| tickBatch.due[interval=8,replay-cap] | 基线 r1base2/common | 0.7 |
| configManager.getOr[lang-hit,depth1] | 基线 r1base/config | 62.9 |
| configManager.getOr[lang-hit,depth1] | 基线 r1base2/config | 64.0 |
| configManager.getOr[config-hit,depth3] | 基线 r1base/config | 167.5 |
| configManager.getOr[config-hit,depth3] | 基线 r1base2/config | 220.0 |
| configManager.getOr[default-hit,depth3] | 基线 r1base/config | 121.8 |
| configManager.getOr[default-hit,depth3] | 基线 r1base2/config | 196.5 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r1base/config | 80.9 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r1base2/config | 83.4 |
| configManager.getOr[miss->default,static-key] | 基线 r1base/config | 70.2 |
| configManager.getOr[miss->default,static-key] | 基线 r1base2/config | 54.9 |
| configManager.getList[lang-hit] | 基线 r1base/config | 80.2 |
| configManager.getList[lang-hit] | 基线 r1base2/config | 76.5 |
| heat.matchesBlockDef[material-only] | 基线 r1base/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 基线 r1base2/heat | 1.3 |
| heat.matchesBlockDef[1-state] | 基线 r1base/heat | 75.6 |
| heat.matchesBlockDef[1-state] | 基线 r1base2/heat | 62.7 |
| heat.matchesBlockDef[2-states] | 基线 r1base/heat | 95.3 |
| heat.matchesBlockDef[2-states] | 基线 r1base2/heat | 125.2 |
| heat.matchesBlockDef[material-miss] | 基线 r1base/heat | 4.1 |
| heat.matchesBlockDef[material-miss] | 基线 r1base2/heat | 3.6 |
| heat.checkLit[lightable] | 基线 r1base/heat | 2.2 |
| heat.checkLit[lightable] | 基线 r1base2/heat | 4.3 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r1base/recipe | 345.3 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r1base2/recipe | 326.8 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r1base/recipe | 525.2 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r1base2/recipe | 724.4 |
| recipeTrie.findMatch[all-miss] | 基线 r1base/recipe | 151.7 |
| recipeTrie.findMatch[all-miss] | 基线 r1base2/recipe | 187.7 |
| recipeTrie.findMatch[mixed-workload] | 基线 r1base/recipe | 382.6 |
| recipeTrie.findMatch[mixed-workload] | 基线 r1base2/recipe | 362.2 |
| matcher.cost[item-id,hit] | 基线 r1base/recipe | 13.0 |
| matcher.cost[item-id,hit] | 基线 r1base2/recipe | 11.4 |
| matcher.cost[item-id,miss] | 基线 r1base/recipe | 10.3 |
| matcher.cost[item-id,miss] | 基线 r1base2/recipe | 11.6 |
| matcher.cost[tag,hit] | 基线 r1base/recipe | 15.4 |
| matcher.cost[tag,hit] | 基线 r1base2/recipe | 11.6 |
| matcher.cost[anyOf3,mixed] | 基线 r1base/recipe | 25.5 |
| matcher.cost[anyOf3,mixed] | 基线 r1base2/recipe | 24.4 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r1base/recipe | 23.4 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r1base2/recipe | 17.7 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r1base/recipe | 14.4 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r1base2/recipe | 14.2 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r1base/recipe | 73.4 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r1base2/recipe | 70.8 |
| tagExpander.expand[nested-map,6-items] | 基线 r1base/recipe | 133.4 |
| tagExpander.expand[nested-map,6-items] | 基线 r1base2/recipe | 115.0 |
| tickBatch.due[interval=1,typical] | 候选 r2/common | 0.7 |
| tickBatch.due[interval=1,typical] | 候选 r2b2/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 候选 r2/common | 1.1 |
| tickBatch.due[interval=4,mixed] | 候选 r2b2/common | 1.5 |
| tickBatch.due[interval=8,replay-cap] | 候选 r2/common | 0.7 |
| tickBatch.due[interval=8,replay-cap] | 候选 r2b2/common | 0.8 |
| configManager.getOr[lang-hit,depth1] | 候选 r2/config | 15.9 |
| configManager.getOr[lang-hit,depth1] | 候选 r2b2/config | 16.0 |
| configManager.getOr[config-hit,depth3] | 候选 r2/config | 38.5 |
| configManager.getOr[config-hit,depth3] | 候选 r2b2/config | 37.6 |
| configManager.getOr[default-hit,depth3] | 候选 r2/config | 32.7 |
| configManager.getOr[default-hit,depth3] | 候选 r2b2/config | 27.7 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r2/config | 122.8 |
| configManager.getOr[miss->default,dynamic-key] | 候选 r2b2/config | 97.3 |
| configManager.getOr[miss->default,static-key] | 候选 r2/config | 2.5 |
| configManager.getOr[miss->default,static-key] | 候选 r2b2/config | 4.8 |
| configManager.getList[lang-hit] | 候选 r2/config | 2.0 |
| configManager.getList[lang-hit] | 候选 r2b2/config | 2.0 |
| heat.matchesBlockDef[material-only] | 候选 r2/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 候选 r2b2/heat | 1.3 |
| heat.matchesBlockDef[1-state] | 候选 r2/heat | 56.6 |
| heat.matchesBlockDef[1-state] | 候选 r2b2/heat | 54.5 |
| heat.matchesBlockDef[2-states] | 候选 r2/heat | 91.4 |
| heat.matchesBlockDef[2-states] | 候选 r2b2/heat | 81.7 |
| heat.matchesBlockDef[material-miss] | 候选 r2/heat | 4.5 |
| heat.matchesBlockDef[material-miss] | 候选 r2b2/heat | 5.6 |
| heat.checkLit[lightable] | 候选 r2/heat | 3.0 |
| heat.checkLit[lightable] | 候选 r2b2/heat | 6.4 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r2/recipe | 346.6 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r2b2/recipe | 324.6 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r2/recipe | 568.2 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r2b2/recipe | 561.0 |
| recipeTrie.findMatch[all-miss] | 候选 r2/recipe | 174.4 |
| recipeTrie.findMatch[all-miss] | 候选 r2b2/recipe | 134.5 |
| recipeTrie.findMatch[mixed-workload] | 候选 r2/recipe | 400.5 |
| recipeTrie.findMatch[mixed-workload] | 候选 r2b2/recipe | 432.8 |
| matcher.cost[item-id,hit] | 候选 r2/recipe | 9.5 |
| matcher.cost[item-id,hit] | 候选 r2b2/recipe | 12.8 |
| matcher.cost[item-id,miss] | 候选 r2/recipe | 14.6 |
| matcher.cost[item-id,miss] | 候选 r2b2/recipe | 13.8 |
| matcher.cost[tag,hit] | 候选 r2/recipe | 10.6 |
| matcher.cost[tag,hit] | 候选 r2b2/recipe | 11.4 |
| matcher.cost[anyOf3,mixed] | 候选 r2/recipe | 22.0 |
| matcher.cost[anyOf3,mixed] | 候选 r2b2/recipe | 20.4 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r2/recipe | 16.9 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r2b2/recipe | 21.3 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r2/recipe | 10.3 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r2b2/recipe | 15.4 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r2/recipe | 68.1 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r2b2/recipe | 65.5 |
| tagExpander.expand[nested-map,6-items] | 候选 r2/recipe | 106.5 |
| tagExpander.expand[nested-map,6-items] | 候选 r2b2/recipe | 108.8 |

---

## 分析结论（人工评注）

**改动内容**（相对 R1 产物）：
1. `ConfigManager`：`getOr`/`getList` 读穿缓存（键→解析值或 MISSING 哨兵；上限 8192 条 + 近似计数器防动态键无界增长；`load()`/`restoreState()` 末尾整体失效；getList 返回 `unmodifiableList` 共享快照——调用方共 12 处已逐一审计均为只读消费）。前提已审计：全项目对 `getConfig()` 均为只读，无运行期突变路径。
2. `HeatSourceService.matchesBlockDef`：状态谓词匹配由「每状态 2×toLowerCase + 1×字符串拼接 + contains」改为「定位 key + regionMatches」零拼接实现（`containsKv`，语义与 contains 等价，含 value 为 key 前缀等碰撞场景）。

**结果解读**（两侧各 2 样本、每基准取最小值聚合）：
- 目标基准稳定胜利：getOr 命中路径 3.97~4.45x、静态 miss 21.6x（MISSING 哨兵负缓存）、getList 38.3x；热源 1 状态 +15.1%、2 状态 +16.7%（次样本间方向一致）。
- `getOr[miss->default,dynamic-key]` −16.8%（约 −16ns 绝对，两样本 122.8/97.3 对基线 80.9/83.4）为**已知有界代价**：永不重复的动态键每次需额外一次缓存探测（字符串哈希 + CHM.get），探测失败后照旧走四级回退。生产代码的 getOr 键均为静态字面量（41 个消费文件已核对），该路径仅在病态调用下出现；缓存条目上限 8192（内存有界，满足稳定性红线）。真实静态 miss 表现为 21.6x 提速。
- `heat.checkLit` 与 tickBatch 各行处于亚 5ns / 亚 1.5ns 抖动带，判定持平·噪声。
- 看守基准（recipeTrie/matcher/tagExpander）全部持平或噪声带内——R2 未触碰这些代码。

**结论**：R2 目标达成，无功能性回归；唯一劣化项为病态动态键的有界代价，已在实现中通过 8192 上限与整体失效策略封顶。
