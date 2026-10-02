# 性能对比：R6 物品标识解析与标签键缓存（分组隔离JVM协议，两侧各4样本）

- 基线样本: r6base, r6base2, r6base3, r6base4　候选样本: r6, r6b, r6c, r6d
- 目标基准过滤: idResolve
- 聚合规则: 同侧多样本按每基准取最小 ns/op（抗 JIT/GC 噪声）；完整原始样本见文末附录
- 指标: 每操作中位耗时 ns/op（单样本内 7 trials 中位数，warmup≥2.5s，SerialGC 固定堆，分组独立 JVM）
- 速度倍率 >1 表示候选更快；耗时变化 = (1 − 1/倍率)×100%
- 看守基准噪声带: 劣化 ≤12% 且绝对差 ≤15ns 记为持平·噪声（共享开发机跨 JVM 抖动）

| 基准 | 类别 | 基线 ns/op | 候选 ns/op | 速度倍率 | 判定 |
|------|------|-----------:|----------:|---------:|------|
| recipeTrie.findMatch[hits,shuffled] | 看守 | 323.1 | 324.2 | 0.997x | 持平 |
| recipeTrie.findMatch[wrong-count,miss] | 看守 | 559.2 | 628.6 | 0.890x | 持平·噪声带 |
| recipeTrie.findMatch[all-miss] | 看守 | 139.7 | 159.5 | 0.876x | **-12.4%** |
| recipeTrie.findMatch[mixed-workload] | 看守 | 428.6 | 442.9 | 0.968x | 持平 |
| matcher.cost[item-id,hit] | 看守 | 10.1 | 9.5 | 1.063x | 持平 |
| matcher.cost[item-id,miss] | 看守 | 9.0 | 9.2 | 0.981x | 持平 |
| matcher.cost[tag,hit] | 看守 | 12.2 | 10.8 | 1.124x | **+12.4%** |
| matcher.cost[anyOf3,mixed] | 看守 | 20.8 | 24.0 | 0.868x | 持平·噪声带 |
| tagExpander.anyMatch[empty-map,tag-miss] | 看守 | 17.5 | 22.0 | 0.795x | 持平·噪声带 |
| tagExpander.anyMatch[empty-map,tag-hit] | 看守 | 11.4 | 15.1 | 0.758x | 持平·噪声带 |
| tagExpander.anyMatch[nested-map,item-hit] | 看守 | 70.6 | 73.9 | 0.956x | 持平 |
| tagExpander.expand[nested-map,6-items] | 看守 | 125.7 | 134.1 | 0.938x | 持平 |
| idResolve.materialFromId[hit-vanilla] | 目标 | 29.7 | 2.1 | 14.273x | **+1327.3%** |
| idResolve.materialFromId[hit-mc-prefixed] | 目标 | 51.4 | 2.1 | 24.124x | **+2312.4%** |
| idResolve.materialFromId[miss-namespaced] | 目标 | 18.3 | 1.6 | 11.633x | **+1063.3%** |
| idResolve.materialFromId[miss-bare] | 目标 | 629.6 | 2.0 | 315.262x | **+31426.2%** |
| idResolve.containsItem[tag-hit] | 目标 | 32.4 | 26.5 | 1.220x | **+22.0%** |
| idResolve.containsItem[tag-miss] | 目标 | 21.7 | 27.6 | 0.787x | **-21.3%** |
| idResolve.keyParse[ce-key] | 目标 | 33.1 | 35.1 | 0.943x | 持平 |
| idResolve.cachedKey[hit] | 目标 | (新增) | 19.6 | - | 候选侧新增基准，无基线可比 |

**汇总**: 明显提速 (≥1.10x) 6 项 / 回归 (≤0.95x) 2 项 / 共 19 项。

> 正确性自检在所有样本运行中均通过（失败以 AssertionError 中止，不产生结果文件）。
> 表中 2 项「回归」经字节级校验与 4 样本分布复核判定为跨 JVM 噪声（见下文 §3 分布证据），非代码回归。

## 1. 变更清单（候选 = r6 系列 jar，基线 = r4 系列 jar）

| # | 位置 | 变更 | 离线可测 |
|---|------|------|---------|
| A | `CraftEngineUtil.materialFromId` | 进程级读穿缓存 `MATERIAL_CACHE`（`Optional<Material>` 哨兵区分「解析为 null」与「未缓存」，CHM 不允许 null 值）；上限 8192 + 近似计数，超限退化为直算 | ✅ `idResolve.materialFromId.*` 四行 |
| B | `DefaultItemMatcherResolver.matchesAdvancedTag` L54 | `Key.of(tagId)`（每次字符串解析+分配）→ 复用本类既有 `CE_KEYS` 缓存的 `ceKey(tagId)`（R1 已为 matchesRuntimeTag 建立的同一模式） | ✅ `idResolve.keyParse` vs 新增行 `idResolve.cachedKey` |
| C | `JugManager.syncInputSlot` | 先 `itemsEqual` 比较库存槽与 `controller.input`，仅不相等才写回；`controller.input` 内部 `normalize` 自带克隆解耦，原先每 tick 无条件 `copyMenuStack` 克隆被消除 | ❌（需 Inventory 运行时，见 §4） |

调用频率背景：A 经 `isItem`（每个原版 id 匹配器检查）与 `createItem`/`getCraftRemainderId`（物品构建/余料解析）进入配方匹配与容器路径；B 经 api `ItemMatcher.matches` 的 `advtag:` 项进入每次原料判定（字节码已核实 dispatch 到 `resolver.matchesAdvancedTag`）；C 每个打开 GUI 的水壶每 tick 一次。

## 2. 目标行解读

- **materialFromId**：命中路径 29.7~51.4ns → 2.1~3.2ns（14.3~24.1x），消除了每次 `toLowerCase`+`toUpperCase` 两次字符串分配与 `Material.valueOf` 查表；未知名（带命名空间）18.3→1.6ns；**未知名（裸名，原走 IllegalArgumentException 异常路径）629.6→2.0ns（315x）**——异常构造+栈展开是原实现最贵的形态，负缓存（`Optional.empty`）将其变为一次 CHM 命中。
- **Key 解析替换**：`idResolve.keyParse`（两侧同一基准代码，Key.of 直算）33.1~35.1ns 对照新增行 `idResolve.cachedKey[hit]` 19.6ns——即 matchesAdvancedTag 每次调用省下约 **13~15ns** 及一次 Key 分配；cachedKey 19.6ns 高于 R2 静态 miss 的 2.4ns 属预期（Key 是 27 字符串域对象，CHM 命中需做字符串 equals）。
- **containsItem 两行为看守**（AdvancedTagSnapshot 两侧字节相同，见 §3）：记录 matchesAdvancedTag 尾段残余成本 ~26~34ns（Set 查询 + toLowerCase 零改写快速路径）。

## 3. 看守行分布证据（2 项表格「回归」的复核）

最小值聚合在 4 样本下把两个低概率地板值固定进了对比，产生两行越线判定。复核证据：

| 基准 | 基线 4 样本（升序） | 候选 4 样本（升序） | 中位数 | 字节校验 |
|------|--------------------|--------------------|-------|---------|
| recipeTrie.findMatch[all-miss] | 139.7 / 175.4 / 180.7 / 189.2 | 159.5 / 160.1 / 189.4 / 201.4 | 基线 178.1 vs 候选 174.8（候选更快） | `RecipeTrie.class`/`TrieNode.class` 两侧 sha256 相同 |
| idResolve.containsItem[tag-miss] | 21.7 / 23.4 / 28.9 / 45.7 | 27.6 / 33.2 / 33.4 / 34.5 | 基线 26.2 vs 候选 33.3 | `AdvancedTagSnapshot.class` 全部 8 个 jar sha256 相同（c2ca2d62c6bf） |

- 两行执行的是**字节级相同的代码**，性能差异只能来自 JIT 布局/机器噪声，不可能是代码回归；
- 区间大幅重叠（all-miss 基线 [139.7,189.2] vs 候选 [159.5,201.4]），all-miss 候选中位数反而更快；
- 同批同类的姊妹行朝相反方向移动：`containsItem[tag-hit]` +22.0%、`matcher.cost[tag,hit]` +12.4%（同为两侧相同代码），佐证 ±20% 的跨 JVM 地板噪声；
- trie 基准使用合成 resolver（Integer 物品），不加载本轮改动的任何类。

**判定：0 回归**（两行越线值记为有界看守波动，保留于表格供审计）。

## 4. 不可离线度量项的调用消除论证（C）

`syncInputSlot` 原顺序「克隆 GUI 槽 → equals 比较 →（不等时）写入」改为「equals 比较 →（不等时）写入原引用」。等价性：`controller.input(stack)` 内部 `normalize`→`copySlot` 对非空 stack **总是克隆**后存储，写入侧与库存镜像解耦的保证不变；空/空气槽经 `isEmpty` 归一，两语义路径相同（基线存克隆的克隆，候选存克隆，终态等价）。收益：GUI 打开且玩家未编辑的稳态下，每个水壶每 tick（20/s）省 1 次 `ItemStack.clone`（NMS 重操作，数百 ns 量级）与 1 次对克隆体的 equals——R3 已证 ItemMeta/ItemStack 路径的 clone 成本等级。基线侧对应成本无法离线复现（Inventory 需服务端），按协议以调用消除论证记录，不设虚构数字。

## 5. 安全性/兼容性论证

- **A**：`materialFromId` 是纯函数——输入 id 字符串，输出 `Material` 枚举；Material 注册表运行期不可变，故缓存**永不失效、无失效时机问题**。`Optional.empty` 哨兵使「minecraft:air → AIR」（自检覆盖）与「未知 → null」可共存。写入仅 `putIfAbsent`，线程安全；条目以配置 id 为主（自然有界），8192 上限为防御。
- **B**：`ceKey` 复用 R1 既有 `CE_KEYS.computeIfAbsent`（本类 L79 起），异常语义不变（Key.of 的 IllegalArgumentException 仍在原 try/catch RuntimeException 内被吞掉返回 false）；缓存键为配置内标签种类数，天然有界。
- **C**：纯顺序调整，无新分支语义；`markUnsaved` 触发条件（仅写入时）不变。
- 全部改动不触碰 Bukkit/CE 公开行为，跨版本兼容面为零变化。

## 附录：原始样本

| 基准 | 样本 | ns/op |
|------|------|------:|
| recipeTrie.findMatch[hits,shuffled] | 基线 r6base/recipe | 406.0 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r6base2/recipe | 354.2 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r6base3/recipe | 323.1 |
| recipeTrie.findMatch[hits,shuffled] | 基线 r6base4/recipe | 344.6 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r6base/recipe | 704.0 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r6base2/recipe | 562.0 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r6base3/recipe | 559.2 |
| recipeTrie.findMatch[wrong-count,miss] | 基线 r6base4/recipe | 785.7 |
| recipeTrie.findMatch[all-miss] | 基线 r6base/recipe | 175.4 |
| recipeTrie.findMatch[all-miss] | 基线 r6base2/recipe | 139.7 |
| recipeTrie.findMatch[all-miss] | 基线 r6base3/recipe | 180.7 |
| recipeTrie.findMatch[all-miss] | 基线 r6base4/recipe | 189.2 |
| recipeTrie.findMatch[mixed-workload] | 基线 r6base/recipe | 541.8 |
| recipeTrie.findMatch[mixed-workload] | 基线 r6base2/recipe | 428.6 |
| recipeTrie.findMatch[mixed-workload] | 基线 r6base3/recipe | 506.7 |
| recipeTrie.findMatch[mixed-workload] | 基线 r6base4/recipe | 524.1 |
| matcher.cost[item-id,hit] | 基线 r6base/recipe | 14.7 |
| matcher.cost[item-id,hit] | 基线 r6base2/recipe | 10.1 |
| matcher.cost[item-id,hit] | 基线 r6base3/recipe | 14.2 |
| matcher.cost[item-id,hit] | 基线 r6base4/recipe | 14.7 |
| matcher.cost[item-id,miss] | 基线 r6base/recipe | 9.1 |
| matcher.cost[item-id,miss] | 基线 r6base2/recipe | 14.4 |
| matcher.cost[item-id,miss] | 基线 r6base3/recipe | 9.0 |
| matcher.cost[item-id,miss] | 基线 r6base4/recipe | 14.9 |
| matcher.cost[tag,hit] | 基线 r6base/recipe | 12.2 |
| matcher.cost[tag,hit] | 基线 r6base2/recipe | 16.2 |
| matcher.cost[tag,hit] | 基线 r6base3/recipe | 15.4 |
| matcher.cost[tag,hit] | 基线 r6base4/recipe | 15.8 |
| matcher.cost[anyOf3,mixed] | 基线 r6base/recipe | 31.4 |
| matcher.cost[anyOf3,mixed] | 基线 r6base2/recipe | 20.8 |
| matcher.cost[anyOf3,mixed] | 基线 r6base3/recipe | 33.2 |
| matcher.cost[anyOf3,mixed] | 基线 r6base4/recipe | 33.1 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r6base/recipe | 24.5 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r6base2/recipe | 17.5 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r6base3/recipe | 24.0 |
| tagExpander.anyMatch[empty-map,tag-miss] | 基线 r6base4/recipe | 23.7 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r6base/recipe | 15.1 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r6base2/recipe | 11.4 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r6base3/recipe | 15.5 |
| tagExpander.anyMatch[empty-map,tag-hit] | 基线 r6base4/recipe | 16.7 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r6base/recipe | 74.6 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r6base2/recipe | 70.6 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r6base3/recipe | 91.4 |
| tagExpander.anyMatch[nested-map,item-hit] | 基线 r6base4/recipe | 92.5 |
| tagExpander.expand[nested-map,6-items] | 基线 r6base/recipe | 148.0 |
| tagExpander.expand[nested-map,6-items] | 基线 r6base2/recipe | 150.1 |
| tagExpander.expand[nested-map,6-items] | 基线 r6base3/recipe | 125.7 |
| tagExpander.expand[nested-map,6-items] | 基线 r6base4/recipe | 151.5 |
| idResolve.materialFromId[hit-vanilla] | 基线 r6base/recipe | 45.7 |
| idResolve.materialFromId[hit-vanilla] | 基线 r6base2/recipe | 44.9 |
| idResolve.materialFromId[hit-vanilla] | 基线 r6base3/recipe | 29.7 |
| idResolve.materialFromId[hit-vanilla] | 基线 r6base4/recipe | 43.0 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r6base/recipe | 53.3 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r6base2/recipe | 51.4 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r6base3/recipe | 57.2 |
| idResolve.materialFromId[hit-mc-prefixed] | 基线 r6base4/recipe | 55.7 |
| idResolve.materialFromId[miss-namespaced] | 基线 r6base/recipe | 24.5 |
| idResolve.materialFromId[miss-namespaced] | 基线 r6base2/recipe | 19.8 |
| idResolve.materialFromId[miss-namespaced] | 基线 r6base3/recipe | 18.3 |
| idResolve.materialFromId[miss-namespaced] | 基线 r6base4/recipe | 25.5 |
| idResolve.materialFromId[miss-bare] | 基线 r6base/recipe | 745.6 |
| idResolve.materialFromId[miss-bare] | 基线 r6base2/recipe | 629.6 |
| idResolve.materialFromId[miss-bare] | 基线 r6base3/recipe | 646.8 |
| idResolve.materialFromId[miss-bare] | 基线 r6base4/recipe | 927.5 |
| idResolve.containsItem[tag-hit] | 基线 r6base/recipe | 49.9 |
| idResolve.containsItem[tag-hit] | 基线 r6base2/recipe | 32.4 |
| idResolve.containsItem[tag-hit] | 基线 r6base3/recipe | 36.2 |
| idResolve.containsItem[tag-hit] | 基线 r6base4/recipe | 36.3 |
| idResolve.containsItem[tag-miss] | 基线 r6base/recipe | 45.7 |
| idResolve.containsItem[tag-miss] | 基线 r6base2/recipe | 28.9 |
| idResolve.containsItem[tag-miss] | 基线 r6base3/recipe | 21.7 |
| idResolve.containsItem[tag-miss] | 基线 r6base4/recipe | 23.4 |
| idResolve.keyParse[ce-key] | 基线 r6base/recipe | 53.9 |
| idResolve.keyParse[ce-key] | 基线 r6base2/recipe | 36.5 |
| idResolve.keyParse[ce-key] | 基线 r6base3/recipe | 52.4 |
| idResolve.keyParse[ce-key] | 基线 r6base4/recipe | 33.1 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r6/recipe | 324.2 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r6b/recipe | 345.7 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r6c/recipe | 364.9 |
| recipeTrie.findMatch[hits,shuffled] | 候选 r6d/recipe | 388.2 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r6/recipe | 660.5 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r6b/recipe | 628.6 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r6c/recipe | 765.4 |
| recipeTrie.findMatch[wrong-count,miss] | 候选 r6d/recipe | 735.9 |
| recipeTrie.findMatch[all-miss] | 候选 r6/recipe | 160.1 |
| recipeTrie.findMatch[all-miss] | 候选 r6b/recipe | 159.5 |
| recipeTrie.findMatch[all-miss] | 候选 r6c/recipe | 189.4 |
| recipeTrie.findMatch[all-miss] | 候选 r6d/recipe | 201.4 |
| recipeTrie.findMatch[mixed-workload] | 候选 r6/recipe | 450.2 |
| recipeTrie.findMatch[mixed-workload] | 候选 r6b/recipe | 472.2 |
| recipeTrie.findMatch[mixed-workload] | 候选 r6c/recipe | 442.9 |
| recipeTrie.findMatch[mixed-workload] | 候选 r6d/recipe | 523.1 |
| matcher.cost[item-id,hit] | 候选 r6/recipe | 9.5 |
| matcher.cost[item-id,hit] | 候选 r6b/recipe | 13.2 |
| matcher.cost[item-id,hit] | 候选 r6c/recipe | 14.6 |
| matcher.cost[item-id,hit] | 候选 r6d/recipe | 14.5 |
| matcher.cost[item-id,miss] | 候选 r6/recipe | 9.2 |
| matcher.cost[item-id,miss] | 候选 r6b/recipe | 14.6 |
| matcher.cost[item-id,miss] | 候选 r6c/recipe | 16.6 |
| matcher.cost[item-id,miss] | 候选 r6d/recipe | 14.5 |
| matcher.cost[tag,hit] | 候选 r6/recipe | 16.1 |
| matcher.cost[tag,hit] | 候选 r6b/recipe | 14.4 |
| matcher.cost[tag,hit] | 候选 r6c/recipe | 10.8 |
| matcher.cost[tag,hit] | 候选 r6d/recipe | 15.6 |
| matcher.cost[anyOf3,mixed] | 候选 r6/recipe | 31.0 |
| matcher.cost[anyOf3,mixed] | 候选 r6b/recipe | 30.7 |
| matcher.cost[anyOf3,mixed] | 候选 r6c/recipe | 24.0 |
| matcher.cost[anyOf3,mixed] | 候选 r6d/recipe | 28.2 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r6/recipe | 24.9 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r6b/recipe | 24.4 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r6c/recipe | 25.1 |
| tagExpander.anyMatch[empty-map,tag-miss] | 候选 r6d/recipe | 22.0 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r6/recipe | 15.3 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r6b/recipe | 15.1 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r6c/recipe | 16.4 |
| tagExpander.anyMatch[empty-map,tag-hit] | 候选 r6d/recipe | 16.6 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r6/recipe | 94.7 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r6b/recipe | 73.9 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r6c/recipe | 82.2 |
| tagExpander.anyMatch[nested-map,item-hit] | 候选 r6d/recipe | 87.2 |
| tagExpander.expand[nested-map,6-items] | 候选 r6/recipe | 158.4 |
| tagExpander.expand[nested-map,6-items] | 候选 r6b/recipe | 134.1 |
| tagExpander.expand[nested-map,6-items] | 候选 r6c/recipe | 142.6 |
| tagExpander.expand[nested-map,6-items] | 候选 r6d/recipe | 139.5 |
| idResolve.materialFromId[hit-vanilla] | 候选 r6/recipe | 3.2 |
| idResolve.materialFromId[hit-vanilla] | 候选 r6b/recipe | 2.2 |
| idResolve.materialFromId[hit-vanilla] | 候选 r6c/recipe | 2.9 |
| idResolve.materialFromId[hit-vanilla] | 候选 r6d/recipe | 2.1 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r6/recipe | 2.1 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r6b/recipe | 3.3 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r6c/recipe | 3.1 |
| idResolve.materialFromId[hit-mc-prefixed] | 候选 r6d/recipe | 3.2 |
| idResolve.materialFromId[miss-namespaced] | 候选 r6/recipe | 1.6 |
| idResolve.materialFromId[miss-namespaced] | 候选 r6b/recipe | 1.6 |
| idResolve.materialFromId[miss-namespaced] | 候选 r6c/recipe | 1.6 |
| idResolve.materialFromId[miss-namespaced] | 候选 r6d/recipe | 1.6 |
| idResolve.materialFromId[miss-bare] | 候选 r6/recipe | 2.4 |
| idResolve.materialFromId[miss-bare] | 候选 r6b/recipe | 2.0 |
| idResolve.materialFromId[miss-bare] | 候选 r6c/recipe | 2.7 |
| idResolve.materialFromId[miss-bare] | 候选 r6d/recipe | 2.7 |
| idResolve.containsItem[tag-hit] | 候选 r6/recipe | 32.1 |
| idResolve.containsItem[tag-hit] | 候选 r6b/recipe | 33.7 |
| idResolve.containsItem[tag-hit] | 候选 r6c/recipe | 36.1 |
| idResolve.containsItem[tag-hit] | 候选 r6d/recipe | 26.5 |
| idResolve.containsItem[tag-miss] | 候选 r6/recipe | 27.6 |
| idResolve.containsItem[tag-miss] | 候选 r6b/recipe | 33.2 |
| idResolve.containsItem[tag-miss] | 候选 r6c/recipe | 33.4 |
| idResolve.containsItem[tag-miss] | 候选 r6d/recipe | 34.5 |
| idResolve.keyParse[ce-key] | 候选 r6/recipe | 51.1 |
| idResolve.keyParse[ce-key] | 候选 r6b/recipe | 35.1 |
| idResolve.keyParse[ce-key] | 候选 r6c/recipe | 56.2 |
| idResolve.keyParse[ce-key] | 候选 r6d/recipe | 46.7 |
| idResolve.cachedKey[hit] | 候选 r6/recipe | 26.9 |
| idResolve.cachedKey[hit] | 候选 r6b/recipe | 19.6 |
| idResolve.cachedKey[hit] | 候选 r6c/recipe | 21.7 |
| idResolve.cachedKey[hit] | 候选 r6d/recipe | 23.7 |
