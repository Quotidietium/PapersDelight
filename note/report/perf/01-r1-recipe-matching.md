# 性能对比：R1 配方匹配热路径优化

- 基线样本: baseline, baseline2, baseline3　候选样本: r1, r1b, r1c
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%

| 基准 | 基线 ns/op | 候选 ns/op | 速度倍率 | 耗时变化 |
|------|-----------:|----------:|---------:|---------:|
| recipeTrie.findMatch[hits,shuffled] | 363.5 | 305.9 | 1.188x | **+18.8%** (+15.8%) |
| recipeTrie.findMatch[wrong-count,miss] | 538.8 | 473.3 | 1.139x | **+13.9%** (+12.2%) |
| recipeTrie.findMatch[all-miss] | 145.0 | 127.2 | 1.139x | **+13.9%** (+12.2%) |
| recipeTrie.findMatch[mixed-workload] | 387.3 | 329.7 | 1.175x | **+17.5%** (+14.9%) |
| matcher.cost[item-id,hit] | 8.5 | 8.5 | 0.994x | -0.6% (-0.6%) |
| matcher.cost[item-id,miss] | 8.8 | 8.9 | 0.991x | -0.9% (-0.9%) |
| matcher.cost[tag,hit] | 9.9 | 9.9 | 0.999x | -0.1% (-0.1%) |
| matcher.cost[anyOf3,mixed] | 20.1 | 20.2 | 0.996x | -0.4% (-0.4%) |
| tagExpander.anyMatch[empty-map,tag-miss] | 34.0 | 15.9 | 2.139x | **+113.9%** (+53.3%) |
| tagExpander.anyMatch[empty-map,tag-hit] | 18.0 | 10.0 | 1.793x | **+79.3%** (+44.2%) |
| tagExpander.anyMatch[nested-map,item-hit] | 60.2 | 62.6 | 0.962x | -3.8% (-4.0%) |
| tagExpander.expand[nested-map,6-items] | 98.8 | 98.5 | 1.003x | +0.3% (+0.3%) |
| tickBatch.due[interval=1,typical] | 0.6 | 0.6 | 0.989x | -1.1% (-1.1%) |
| tickBatch.due[interval=4,mixed] | 1.0 | 1.0 | 1.003x | +0.3% (+0.3%) |
| tickBatch.due[interval=8,replay-cap] | 0.7 | 0.7 | 1.018x | +1.8% (+1.8%) |

**汇总**: 明显提速 (≥1.10x) 6 项 / 回归 (≤0.95x) 0 项 / 共 15 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| recipeTrie.findMatch[hits,shuffled] | 基线 baseline | 393.6 |
| recipeTrie.findMatch[hits,shuffled] | 基线 baseline2 | 409.3 |
| recipeTrie.findMatch[hits,shuffled] | 基线 baseline3 | 363.5 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 baseline | 772.7 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 baseline2 | 663.7 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 baseline3 | 538.8 |
| recipeTrie.findMatch[all-miss] | 基线 baseline | 204.9 |
| recipeTrie.findMatch[all-miss] | 基线 baseline2 | 196.4 |
| recipeTrie.findMatch[all-miss] | 基线 baseline3 | 145.0 |
| recipeTrie.findMatch[mixed-workload] | 基线 baseline | 512.6 |
| recipeTrie.findMatch[mixed-workload] | 基线 baseline2 | 536.1 |
| recipeTrie.findMatch[mixed-workload] | 基线 baseline3 | 387.3 |
| matcher.cost[item-id,hit] | 基线 baseline | 14.3 |
| matcher.cost[item-id,hit] | 基线 baseline2 | 8.5 |
| matcher.cost[item-id,hit] | 基线 baseline3 | 8.5 |
| matcher.cost[item-id,miss] | 基线 baseline | 8.9 |
| matcher.cost[item-id,miss] | 基线 baseline2 | 8.8 |
| matcher.cost[item-id,miss] | 基线 baseline3 | 8.9 |
| matcher.cost[tag,hit] | 基线 baseline | 9.9 |
| matcher.cost[tag,hit] | 基线 baseline2 | 9.9 |
| matcher.cost[tag,hit] | 基线 baseline3 | 10.1 |
| matcher.cost[anyOf3,mixed] | 基线 baseline | 20.1 |
| matcher.cost[anyOf3,mixed] | 基线 baseline2 | 20.6 |
| matcher.cost[anyOf3,mixed] | 基线 baseline3 | 20.3 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 baseline | 47.6 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 baseline2 | 51.3 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 baseline3 | 34.0 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 baseline | 28.2 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 baseline2 | 18.3 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 baseline3 | 18.0 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 baseline | 80.8 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 baseline2 | 60.2 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 baseline3 | 60.7 |
| tagExpander.expand[nested-map,6-items] | 基线 baseline | 100.5 |
| tagExpander.expand[nested-map,6-items] | 基线 baseline2 | 98.8 |
| tagExpander.expand[nested-map,6-items] | 基线 baseline3 | 102.4 |
| tickBatch.due[interval=1,typical] | 基线 baseline | 0.7 |
| tickBatch.due[interval=1,typical] | 基线 baseline2 | 0.7 |
| tickBatch.due[interval=1,typical] | 基线 baseline3 | 0.6 |
| tickBatch.due[interval=4,mixed] | 基线 baseline | 1.7 |
| tickBatch.due[interval=4,mixed] | 基线 baseline2 | 1.0 |
| tickBatch.due[interval=4,mixed] | 基线 baseline3 | 1.0 |
| tickBatch.due[interval=8,replay-cap] | 基线 baseline | 0.7 |
| tickBatch.due[interval=8,replay-cap] | 基线 baseline2 | 0.8 |
| tickBatch.due[interval=8,replay-cap] | 基线 baseline3 | 0.7 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r1 | 361.0 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r1b | 311.2 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r1c | 305.9 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r1 | 657.9 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r1b | 477.2 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r1c | 473.3 |
| recipeTrie.findMatch[all-miss] | 候选 r1 | 171.0 |
| recipeTrie.findMatch[all-miss] | 候选 r1b | 130.5 |
| recipeTrie.findMatch[all-miss] | 候选 r1c | 127.2 |
| recipeTrie.findMatch[mixed-workload] | 候选 r1 | 490.7 |
| recipeTrie.findMatch[mixed-workload] | 候选 r1b | 332.4 |
| recipeTrie.findMatch[mixed-workload] | 候选 r1c | 329.7 |
| matcher.cost[item-id,hit] | 候选 r1 | 15.5 |
| matcher.cost[item-id,hit] | 候选 r1b | 10.6 |
| matcher.cost[item-id,hit] | 候选 r1c | 8.5 |
| matcher.cost[item-id,miss] | 候选 r1 | 12.2 |
| matcher.cost[item-id,miss] | 候选 r1b | 14.8 |
| matcher.cost[item-id,miss] | 候选 r1c | 8.9 |
| matcher.cost[tag,hit] | 候选 r1 | 9.9 |
| matcher.cost[tag,hit] | 候选 r1b | 10.0 |
| matcher.cost[tag,hit] | 候选 r1c | 10.1 |
| matcher.cost[anyOf3,mixed] | 候选 r1 | 29.0 |
| matcher.cost[anyOf3,mixed] | 候选 r1b | 20.2 |
| matcher.cost[anyOf3,mixed] | 候选 r1c | 20.2 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r1 | 24.0 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r1b | 15.9 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r1c | 16.7 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r1 | 10.0 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r1b | 10.1 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r1c | 10.0 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r1 | 80.4 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r1b | 66.1 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r1c | 62.6 |
| tagExpander.expand[nested-map,6-items] | 候选 r1 | 98.5 |
| tagExpander.expand[nested-map,6-items] | 候选 r1b | 98.6 |
| tagExpander.expand[nested-map,6-items] | 候选 r1c | 102.0 |
| tickBatch.due[interval=1,typical] | 候选 r1 | 0.7 |
| tickBatch.due[interval=1,typical] | 候选 r1b | 0.7 |
| tickBatch.due[interval=1,typical] | 候选 r1c | 0.6 |
| tickBatch.due[interval=4,mixed] | 候选 r1 | 1.0 |
| tickBatch.due[interval=4,mixed] | 候选 r1b | 1.0 |
| tickBatch.due[interval=4,mixed] | 候选 r1c | 1.0 |
| tickBatch.due[interval=8,replay-cap] | 候选 r1 | 0.7 |
| tickBatch.due[interval=8,replay-cap] | 候选 r1b | 0.7 |
| tickBatch.due[interval=8,replay-cap] | 候选 r1c | 0.7 |
