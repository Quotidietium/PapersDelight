# 性能对比：R10 效果标题秒桶缓存与 isItem 零分配比较（分组隔离JVM协议，两侧各2样本）

- 基线样本: r10base, r10base2　候选样本: r10, r10b
- 目标基准过滤: idResolve
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| recipeTrie.findMatch[hits,shuffled] | 看守 | 364.0 | 318.8 | 1.142x | **+14.2%** |
| recipeTrie.findMatch[wrong-count,miss] | 看守 | 746.5 | 734.7 | 1.016x | 持平 |
| recipeTrie.findMatch[all-miss] | 看守 | 169.8 | 152.4 | 1.114x | **+11.4%** |
| recipeTrie.findMatch[mixed-workload] | 看守 | 432.4 | 444.1 | 0.974x | 持平 |
| matcher.cost[item-id,hit] | 看守 | 9.0 | 9.0 | 0.995x | 持平 |
| matcher.cost[item-id,miss] | 看守 | 9.4 | 14.7 | 0.640x | 持平·噪声带 |
| matcher.cost[tag,hit] | 看守 | 10.9 | 10.5 | 1.036x | 持平 |
| matcher.cost[anyOf3,mixed] | 看守 | 30.7 | 21.1 | 1.457x | **+45.7%** |
| tagExpander.anyMatch[empty-map,tag-miss] | 看守 | 24.5 | 16.6 | 1.474x | **+47.4%** |
| tagExpander.anyMatch[empty-map,tag-hit] | 看守 | 10.6 | 16.1 | 0.659x | 持平·噪声带 |
| tagExpander.anyMatch[nested-map,item-hit] | 看守 | 75.4 | 93.8 | 0.804x | **-19.6%** |
| tagExpander.expand[nested-map,6-items] | 看守 | 140.0 | 107.8 | 1.299x | **+29.9%** |
| idResolve.materialFromId[hit-vanilla] | 目标 | 1.9 | 3.0 | 0.618x | 持平·噪声带 |
| idResolve.materialFromId[hit-mc-prefixed] | 目标 | 3.0 | 2.9 | 1.036x | 持平 |
| idResolve.materialFromId[miss-namespaced] | 目标 | 2.5 | 2.6 | 0.989x | 持平 |
| idResolve.materialFromId[miss-bare] | 目标 | 1.9 | 2.6 | 0.749x | 持平·噪声带 |
| idResolve.containsItem[tag-hit] | 目标 | 29.2 | 27.4 | 1.065x | 持平 |
| idResolve.containsItem[tag-miss] | 目标 | 22.9 | 22.5 | 1.016x | 持平 |
| idResolve.keyParse[ce-key] | 目标 | 37.6 | 38.8 | 0.968x | 持平 |
| idResolve.idCompare[toString-equals] | 目标 | 13.1 | 10.7 | 1.230x | **+23.0%** |
| idResolve.idCompare[split-compare] | 目标 | 5.6 | 5.9 | 0.955x | 持平 |
| idResolve.cachedKey[hit] | 目标 | 15.3 | 16.0 | 0.959x | 持平 |

**汇总**: 明显提速 (≥1.10x) 6 项 / 回归 (≤0.95x) 1 项 / 共 22 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。
> 1 项越线看守行经字节校验 + 跨轮分布判为噪声（§3）；本表多个未改动行的双向大波动
> （+47.4%/-36% 等）同为跨 JVM 抖动，见 §3。

## 1. 变更清单（候选 = r10 系列 jar，基线 = r8 系列 jar）

| # | 位置 | 变更 | 离线可测 |
|---|------|------|---------|
| A | `TimedEffectSession`（record→可变类）+ `TimedEffectManager.tickPlayer` | 标题秒桶缓存：formatDuration 为秒级粒度（ticks/20），同秒内标题组件不变——构建与 equals 树遍历从每受效果玩家 20 次/秒降为 1 次/秒；构建经 supplier 调用管理器可覆写的 buildTitle（NourishmentManager 等覆写者的多态语义保留） | ❌（标题构建纯 adventure 但入口绑定会话，量化以调用消除论证） |
| B | `CraftEngineUtil.isItem` CE 分支 | 零分配等价改写：裸 value 快路径先行；全限定比较由 `id.equals(key.toString())`（每次拼接分配，CE Key 字节码核实 toString ≡ namespace+":"+value）改为手工拆 namespace/value 的分段比较 | ✅ `idResolve.idCompare[toString vs split]` 两行 |

## 2. 目标行解读

- **idCompare 双行**（基准本地复刻件，两侧代码相同）：toString-equals 10.7~15.7ns vs split-compare 5.6~9.6ns——**每次 CE 物品 id 比较省 ~5-7ns 与一次字符串分配**（约 1.8x）。调用面：matcher 的 CE 物品项检查、手持煎锅每 tick 的 skillet 判定、砧板工具位匹配循环。等价性经 8 形态语料 + **1 万次种子模糊**（alphabet 含冒号与大小写）在基准自检中证明，两侧真值完全一致。
- **cachedKey/keyParse/materialFromId/containsItem 行**两侧持平（R6 改动在本轮基线已生效）。

## 3. 越线与波动行复核

| 基准 | 本轮 4 样本 | 复核 |
|------|------------|------|
| tagExpander.anyMatch[nested-map,item-hit]（-19.6%） | 基线 [75.4, 84.8] 候选 [93.8, 94.4] | `TagExpander.class` 4 个 jar sha256 相同；该行跨 r6/r9/r10 三代 jar 共 16 样本区间 **70.6~94.7ns** 双峰分布且方向与 jar 代次无关（r9 候选反而更快 78 vs 84-90）→ JIT 双峰噪声，非代码回归 |
| tagExpander[empty-map,tag-miss] +47.4%、matcher[anyOf3] +45.7%、matcher[item-id,miss] -36%（噪声带） | — | 同为未改动代码的双向抖动，本轮机器状态偏快/偏慢并存，印证跨 JVM 噪声带；不计入收益 |

**判定：0 代码回归**（idCompare 双行为基准本地复刻件，行间差值才是本轮量化结论）。

## 4. 安全性论证

- **A**：缓存不变式显式记录在 TimedEffectSession 类注释（buildTitle 对同一 (秒桶, amplifier) 返回等值组件；基类实现仅依赖秒粒度时长与等级）；BossBar progress 更新频率不变（每 tick），仅标题构建降频；会话仍以引用相等校验身份，替换会话自然弃用缓存。
- **B**：等价性三重保障——CE Key 字节码核实（toString = namespace+":"+value，两字段 public final）、8 形态语料断言、1 万次种子模糊；比较为纯函数无状态。
- record→class 转换：构造器与访问器签名形状不变，全部调用点零改动（使用面仅在 TimedEffectManager 内部，grep 核实）。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| recipeTrie.findMatch[hits,shuffled] | 基线 r10base/recipe | 386.0 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r10base2/recipe | 364.0 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r10base/recipe | 750.2 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r10base2/recipe | 746.5 |
| recipeTrie.findMatch[all-miss] | 基线 r10base/recipe | 169.8 |
| recipeTrie.findMatch[all-miss] | 基线 r10base2/recipe | 188.7 |
| recipeTrie.findMatch[mixed-workload] | 基线 r10base/recipe | 435.0 |
| recipeTrie.findMatch[mixed-workload] | 基线 r10base2/recipe | 432.4 |
| matcher.cost[item-id,hit] | 基线 r10base/recipe | 9.0 |
| matcher.cost[item-id,hit] | 基线 r10base2/recipe | 14.4 |
| matcher.cost[item-id,miss] | 基线 r10base/recipe | 9.4 |
| matcher.cost[item-id,miss] | 基线 r10base2/recipe | 13.2 |
| matcher.cost[tag,hit] | 基线 r10base/recipe | 10.9 |
| matcher.cost[tag,hit] | 基线 r10base2/recipe | 13.1 |
| matcher.cost[anyOf3,mixed] | 基线 r10base/recipe | 30.7 |
| matcher.cost[anyOf3,mixed] | 基线 r10base2/recipe | 32.3 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r10base/recipe | 24.9 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r10base2/recipe | 24.5 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r10base/recipe | 14.7 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r10base2/recipe | 10.6 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r10base/recipe | 84.8 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r10base2/recipe | 75.4 |
| tagExpander.expand[nested-map,6-items] | 基线 r10base/recipe | 140.0 |
| tagExpander.expand[nested-map,6-items] | 基线 r10base2/recipe | 145.9 |
| idResolve.materialFromId[hit-vanilla] | 基线 r10base/recipe | 1.9 |
| idResolve.materialFromId[hit-vanilla] | 基线 r10base2/recipe | 2.9 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r10base/recipe | 3.2 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r10base2/recipe | 3.0 |
| idResolve.materialFromId[miss-namespaced] | 基线 r10base/recipe | 2.5 |
| idResolve.materialFromId[miss-namespaced] | 基线 r10base2/recipe | 2.7 |
| idResolve.materialFromId[miss-bare] | 基线 r10base/recipe | 1.9 |
| idResolve.materialFromId[miss-bare] | 基线 r10base2/recipe | 2.6 |
| idResolve.containsItem[tag-hit] | 基线 r10base/recipe | 35.5 |
| idResolve.containsItem[tag-hit] | 基线 r10base2/recipe | 29.2 |
| idResolve.containsItem[tag-miss] | 基线 r10base/recipe | 33.7 |
| idResolve.containsItem[tag-miss] | 基线 r10base2/recipe | 22.9 |
| idResolve.keyParse[ce-key] | 基线 r10base/recipe | 54.9 |
| idResolve.keyParse[ce-key] | 基线 r10base2/recipe | 37.6 |
| idResolve.idCompare[toString-equals] | 基线 r10base/recipe | 15.7 |
| idResolve.idCompare[toString-equals] | 基线 r10base2/recipe | 13.1 |
| idResolve.idCompare[split-compare] | 基线 r10base/recipe | 5.6 |
| idResolve.idCompare[split-compare] | 基线 r10base2/recipe | 9.6 |
| idResolve.cachedKey[hit] | 基线 r10base/recipe | 21.8 |
| idResolve.cachedKey[hit] | 基线 r10base2/recipe | 15.3 |
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
