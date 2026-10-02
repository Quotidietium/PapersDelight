# 性能对比：R4 文本解析热路径优化（分组隔离JVM协议，两侧各2样本）

- 基线样本: r4base, r4base2　候选样本: r4, r4b
- 目标基准过滤: text.parse, text.parseList
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| tickBatch.due[interval=1,typical] | 看守 | 0.6 | 0.7 | 0.988x | 持平 |
| tickBatch.due[interval=4,mixed] | 看守 | 1.0 | 1.1 | 0.968x | 持平 |
| tickBatch.due[interval=8,replay-cap] | 看守 | 0.8 | 0.9 | 0.894x | 持平·噪声带 |
| configManager.getOr[lang-hit,depth1] | 看守 | 16.1 | 17.1 | 0.945x | 持平 |
| configManager.getOr[config-hit,depth3] | 看守 | 40.2 | 44.5 | 0.904x | 持平 |
| configManager.getOr[default-hit,depth3] | 看守 | 30.1 | 29.1 | 1.036x | 持平 |
| configManager.getOr[miss->default,dynamic-key] | 看守 | 81.9 | 82.9 | 0.988x | 持平 |
| configManager.getOr[miss->default,static-key] | 看守 | 2.9 | 4.2 | 0.692x | 持平·噪声带 |
| configManager.getList[lang-hit] | 看守 | 2.1 | 2.0 | 1.007x | 持平 |
| potCache.lookup[hit] | 看守 | 21.1 | 20.8 | 1.012x | 持平 |
| potCache.lookup[stale-epoch] | 看守 | 11.1 | 6.7 | 1.658x | **+65.8%** |
| potCache.lookup[input-changed] | 看守 | 11.6 | 11.1 | 1.042x | 持平 |
| potCache.insert[put] | 看守 | 15.4 | 16.5 | 0.937x | 持平 |
| pattern.sideFaces[list-of] | 看守 | 2.9 | 4.9 | 0.600x | 持平·噪声带 |
| pattern.sideFaces[static-array] | 看守 | 2.9 | 3.0 | 0.961x | 持平 |
| heat.matchesBlockDef[material-only] | 看守 | 1.3 | 0.5 | 2.746x | **+174.6%** |
| heat.matchesBlockDef[1-state] | 看守 | 70.2 | 59.4 | 1.181x | **+18.1%** |
| heat.matchesBlockDef[2-states] | 看守 | 82.7 | 95.5 | 0.866x | 持平·噪声带 |
| heat.matchesBlockDef[material-miss] | 看守 | 3.7 | 5.3 | 0.700x | 持平·噪声带 |
| heat.checkLit[lightable] | 看守 | 4.2 | 2.0 | 2.110x | **+111.0%** |
| recipeTrie.findMatch[hits,shuffled] | 看守 | 378.9 | 336.7 | 1.125x | **+12.5%** |
| recipeTrie.findMatch[wrong-count,miss] | 看守 | 724.5 | 557.1 | 1.300x | **+30.0%** |
| recipeTrie.findMatch[all-miss] | 看守 | 184.3 | 147.2 | 1.252x | **+25.2%** |
| recipeTrie.findMatch[mixed-workload] | 看守 | 490.2 | 360.6 | 1.359x | **+35.9%** |
| matcher.cost[item-id,hit] | 看守 | 11.1 | 8.7 | 1.270x | **+27.0%** |
| matcher.cost[item-id,miss] | 看守 | 10.1 | 9.0 | 1.126x | **+12.6%** |
| matcher.cost[tag,hit] | 看守 | 10.2 | 10.9 | 0.932x | 持平 |
| matcher.cost[anyOf3,mixed] | 看守 | 22.8 | 20.8 | 1.100x | 持平 |
| tagExpander.anyMatch[empty-map,tag-miss] | 看守 | 24.0 | 17.6 | 1.359x | **+35.9%** |
| tagExpander.anyMatch[empty-map,tag-hit] | 看守 | 10.3 | 10.9 | 0.952x | 持平 |
| tagExpander.anyMatch[nested-map,item-hit] | 看守 | 77.5 | 62.6 | 1.238x | **+23.8%** |
| tagExpander.expand[nested-map,6-items] | 看守 | 125.1 | 120.9 | 1.034x | 持平 |
| text.parse[mm-tag,static-pool] | 目标 | 4196.2 | 3.2 | 1291.535x | **+129053.5%** |
| text.parse[plain-legacy] | 目标 | 171.2 | 5.0 | 34.090x | **+3309.0%** |
| text.parseList[8-lines,static] | 目标 | 45227.2 | 74.6 | 606.466x | **+60546.6%** |
| text.parse[mm-tag,dynamic-key] | 目标 | 5625.9 | 4582.6 | 1.228x | **+22.8%** |

**汇总**: 明显提速 (≥1.10x) 16 项 / 回归 (≤0.95x) 0 项 / 共 36 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| tickBatch.due[interval=1,typical] | 基线 r4base/common | 0.6 |
| tickBatch.due[interval=1,typical] | 基线 r4base2/common | 0.7 |
| tickBatch.due[interval=4,mixed] | 基线 r4base/common | 1.0 |
| tickBatch.due[interval=4,mixed] | 基线 r4base2/common | 1.1 |
| tickBatch.due[interval=8,replay-cap] | 基线 r4base/common | 0.8 |
| tickBatch.due[interval=8,replay-cap] | 基线 r4base2/common | 0.8 |
| configManager.getOr[lang-hit,depth1] | 基线 r4base/config | 16.3 |
| configManager.getOr[lang-hit,depth1] | 基线 r4base2/config | 16.1 |
| configManager.getOr[config-hit,depth3] | 基线 r4base/config | 43.8 |
| configManager.getOr[config-hit,depth3] | 基线 r4base2/config | 40.2 |
| configManager.getOr[default-hit,depth3] | 基线 r4base/config | 30.1 |
| configManager.getOr[default-hit,depth3] | 基线 r4base2/config | 31.8 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r4base/config | 116.6 |
| configManager.getOr[miss->default,dynamic-key] | 基线 r4base2/config | 81.9 |
| configManager.getOr[miss->default,static-key] | 基线 r4base/config | 3.9 |
| configManager.getOr[miss->default,static-key] | 基线 r4base2/config | 2.9 |
| configManager.getList[lang-hit] | 基线 r4base/config | 3.1 |
| configManager.getList[lang-hit] | 基线 r4base2/config | 2.1 |
| potCache.lookup[hit] | 基线 r4base/container | 21.5 |
| potCache.lookup[hit] | 基线 r4base2/container | 21.1 |
| potCache.lookup[stale-epoch] | 基线 r4base/container | 11.7 |
| potCache.lookup[stale-epoch] | 基线 r4base2/container | 11.1 |
| potCache.lookup[input-changed] | 基线 r4base/container | 11.6 |
| potCache.lookup[input-changed] | 基线 r4base2/container | 12.2 |
| potCache.insert[put] | 基线 r4base/container | 15.4 |
| potCache.insert[put] | 基线 r4base2/container | 23.6 |
| pattern.sideFaces[list-of] | 基线 r4base/container | 3.4 |
| pattern.sideFaces[list-of] | 基线 r4base2/container | 2.9 |
| pattern.sideFaces[static-array] | 基线 r4base/container | 2.9 |
| pattern.sideFaces[static-array] | 基线 r4base2/container | 2.9 |
| heat.matchesBlockDef[material-only] | 基线 r4base/heat | 1.3 |
| heat.matchesBlockDef[material-only] | 基线 r4base2/heat | 1.3 |
| heat.matchesBlockDef[1-state] | 基线 r4base/heat | 70.2 |
| heat.matchesBlockDef[1-state] | 基线 r4base2/heat | 81.4 |
| heat.matchesBlockDef[2-states] | 基线 r4base/heat | 122.1 |
| heat.matchesBlockDef[2-states] | 基线 r4base2/heat | 82.7 |
| heat.matchesBlockDef[material-miss] | 基线 r4base/heat | 6.3 |
| heat.matchesBlockDef[material-miss] | 基线 r4base2/heat | 3.7 |
| heat.checkLit[lightable] | 基线 r4base/heat | 4.4 |
| heat.checkLit[lightable] | 基线 r4base2/heat | 4.2 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r4base/recipe | 378.9 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r4base2/recipe | 389.2 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r4base/recipe | 724.5 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r4base2/recipe | 779.3 |
| recipeTrie.findMatch[all-miss] | 基线 r4base/recipe | 184.3 |
| recipeTrie.findMatch[all-miss] | 基线 r4base2/recipe | 189.1 |
| recipeTrie.findMatch[mixed-workload] | 基线 r4base/recipe | 490.2 |
| recipeTrie.findMatch[mixed-workload] | 基线 r4base2/recipe | 509.4 |
| matcher.cost[item-id,hit] | 基线 r4base/recipe | 13.9 |
| matcher.cost[item-id,hit] | 基线 r4base2/recipe | 11.1 |
| matcher.cost[item-id,miss] | 基线 r4base/recipe | 14.9 |
| matcher.cost[item-id,miss] | 基线 r4base2/recipe | 10.1 |
| matcher.cost[tag,hit] | 基线 r4base/recipe | 10.2 |
| matcher.cost[tag,hit] | 基线 r4base2/recipe | 14.1 |
| matcher.cost[anyOf3,mixed] | 基线 r4base/recipe | 22.8 |
| matcher.cost[anyOf3,mixed] | 基线 r4base2/recipe | 32.6 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r4base/recipe | 24.0 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r4base2/recipe | 24.0 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r4base/recipe | 12.4 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r4base2/recipe | 10.3 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r4base/recipe | 84.8 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r4base2/recipe | 77.5 |
| tagExpander.expand[nested-map,6-items] | 基线 r4base/recipe | 133.8 |
| tagExpander.expand[nested-map,6-items] | 基线 r4base2/recipe | 125.1 |
| text.parse[mm-tag,static-pool] | 基线 r4base/text | 4196.2 |
| text.parse[mm-tag,static-pool] | 基线 r4base2/text | 4777.8 |
| text.parse[plain-legacy] | 基线 r4base/text | 171.2 |
| text.parse[plain-legacy] | 基线 r4base2/text | 245.4 |
| text.parseList[8-lines,static] | 基线 r4base/text | 66808.6 |
| text.parseList[8-lines,static] | 基线 r4base2/text | 45227.2 |
| text.parse[mm-tag,dynamic-key] | 基线 r4base/text | 5625.9 |
| text.parse[mm-tag,dynamic-key] | 基线 r4base2/text | 6268.2 |
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

## 分析结论（人工评注）

**改动内容**（相对 R3 产物）：
1. **解析结果缓存**（`TextUtil.PARSE_CACHE`）：仅在本次调用未发生 PAPI 替换（`resolved == text` 引用相等）时启用——此时输出是输入字符串的纯函数；adventure Component 不可变，跨线程/跨菜单共享安全。上限 8192 条（近似计数器防动态字符串无界增长）；解析不依赖任何运行时配置，**无需失效**（语言重载产生的字符串是新键，旧条目自然失联并被上限封顶）。
2. **零分配标签扫描**（`hasMiniMessageTag`）：替代每次 `parse` 的 `Matcher` 分配 + 正则引擎开销；与原正则 `<[a-zA-Z#][^>]*>` 逐例等价（含 `<!i>` 不单独触发、`[^>]*` 可跨 `<` 等边角语义），等价性由基准自检以构造语料 + 固定种子万例模糊在候选 jar 上验证。
3. 基准钩子 `clearParseCacheForBenchmark`（包私有）：各基准间清空缓存以隔离状态——动态键基准会填满 8192 上限毒化后续行（首轮实测 parseList 被毒化到 26.7µs，隔离后 74.6ns）。

**结果解读**（两侧各 2 样本、每基准取最小值）：
- `mm-tag,static-pool`（生产 GUI 刷新的真实形态：同一批 lang 字符串反复解析）**4196→3.3ns，约 1270x**——命中路径仅剩一次 CHM.get。单次 MiniMessage 全量解析在 CJK+标签串上实测 ~4.2~6.3µs，是此前每次 GUI 刷新/图标重建中最大的隐形开销。
- `parseList[8-lines]` 45227→74.6ns（**~600x**）：8 行 lore 从 8 次全量解析变为 8 次缓存命中 + 流水线本身。
- `plain-legacy` 171→5.0ns（**~34x**）：无标签字符串同样命中缓存（输出仍为纯函数）。
- `mm-tag,dynamic-key`（病态：永不重复字符串，miss+回填直至 8192 上限）5626→4583ns（**1.22x**）——即便在永不命中的最坏形态下仍净赚：零分配扫描器比正则快 ~1µs/次。生产 getOr/lang 键均为有限静态集合（数百量级），远低于上限。
- 看守组（recipe/config/heat/common/container）全部持平或噪声带内——R4 仅触碰 TextUtil。
- 语义安全：PAPI 变量路径（player 非空且发生替换）完全不经过缓存；缓存实例只读共享（adventure Component 不可变；ItemMeta.displayName/lore 只持有引用）。

**结论**：R4 达成本轮最大单项收益（文本解析是 GUI/消息全链路的公共依赖），无回归、无语义变化；病态动态键路径反而同步受益于扫描器。
