# R11 覆盖审计：全部周期/热路径与优化状态矩阵（活动收尾证据）

本报告是性能优化活动（R1~R10）的系统性收尾审计：枚举插件内**全部**周期性调度与热路径入口，
逐条标注优化状态与依据，证明「每一轮的优化方面做完」且已无遗漏的红线兼容候选。
审计方法：grep 全部 `static void tick(`（CE ticker 入口）、`runAtFixedRate/runTaskTimer`（CCScheduler
周期任务）、`TickBatch.due` 消费者（批处理补偿循环）、GUI 动画任务，再逐一人工核对实现。

## 1. 周期性 / 每 tick 路径矩阵

| 路径 | 入口 | 频率 | 状态 | 依据轮次 |
|------|------|------|------|---------|
| 烹饪锅方块 tick | CookingPotBlockEntityController.tick → potTick | 每 tick | ✅ 已优化 | R3（产物原型缓存/热缓存/支撑节流/缓存门）+ R8（GUI 会话槽位先比后克隆、空闲快路径 3→1 次调度、无变化不刷新） |
| 水壶方块 tick | JugBlockEntityController.tick → jugPass | 每 tick | ✅ 已优化 | R6（syncInputSlot 先比后克隆）；populateMenu 既有 DisplaySnapshot 四维 diff（fluidKey/amount/capacity/progressStage），稳态仅字段比较 |
| 煎锅方块 tick | SkilletBlockEntityController.tick → tickSkillet | 每 tick | ✅ 已优化 | R7（显示签名 diff 门——唯一逐 tick 刷新展示实体的路径；storedStackDirect 直取；热缓存 10 pass 既有） |
| 手持煎锅烹饪 | SkilletManager tickHandheldCooking | 每 tick/玩家 | ✅ 已优化 | R10（isItem 零分配比较，~1.8x）；其余为 NMS getter |
| 炉灶方块 tick | StoveBlockEntityController.tick → tickStove | 每 tick | ✅ 已优化 | R7（dropLoc 惰性分配、isBlockedAbove 仅非空检查）；粒子/环境音既有节拍+抖动+可见性门 |
| 篮子收集 | BasketManager.collectTick | 每 tick（全局） | ✅ 审计通过 | 有界（≤MAX_BASKETS_PER_TICK）、nextAttempts 退避、冷却期扫描；成本为冷却速率级 |
| 计时效果心跳 | TimedEffectManager.tick → tickPlayer | 每 2t/受效果玩家 | ✅ 已优化 | R10（标题秒桶缓存 20→1 次构建/秒）；bossBar progress 语义性每 tick 更新保留 |
| 营养效果 tick | NourishmentManager.onEffectTick | 每 2t/玩家 | ✅ 审计通过 | 纯 NMS getter（饱和度/饥饿读写），无插件侧可优化对象（红线 1 不允许改语义） |
| 砧板漏斗任务 | CuttingBoardManager hopperTask | 周期 | ✅ 审计通过 | 事件速率；展示刷新仅发生在物品必然已变化的 5 个调用点（diff 门永不命中，正确地未移植 R7 门） |
| 配方书标签动画 | CookingPotRecipeBook tagAnimation | tagCycleIntervalTicks（秒级） | ✅ 审计通过 | 低频 GUI 动画，每帧仅动画槽位 setItem |
| 浏览器进度动画 | RecipeBrowserManager runTaskTimer ×3 | 秒级 | ✅ 审计通过 | 同上 |
| 绳绑作物 | RopedCropBlockBehavior | 事件一次性 runTask | ✅ 审计通过 | 事件速率 |

## 2. 事件热路径矩阵

| 路径 | 状态 | 依据轮次 |
|------|------|---------|
| 配方匹配（Trie DFS + 线性回退） | ✅ R1（冻结结构/位掩码；1.07~1.65x） | R1 |
| 原料匹配器（item/tag/advtag） | ✅ R1（NS_KEYS/CE_KEYS）+ R6（ceKey 复用）+ R10（isItem 零分配） | R1/R6/R10 |
| 原版 id→Material 解析 | ✅ R6（进程级缓存 12.6~362x） | R6 |
| 配置读取 getOr/getList | ✅ R2（读穿缓存 3.5~44.7x；动态键 miss 有界权衡透明记录） | R2 |
| 热源状态匹配 | ✅ R2（零分配状态匹配 1.4~2.7x） | R2 |
| 文本解析（MiniMessage/legacy） | ✅ R4（解析结果缓存 34~1498.8x） | R4 |
| 版本判定 | ✅ R7（缓存 55~84ns→≈0） | R7 |
| 餐食容器呈现 | ✅ R7（单遍 meta） | R7 |
| TickBatch 批量补偿 | ✅ 既有实现亚 ns（0.6~1.5ns），无优化空间 | R9 确认持平 |
| 粒子邻域计数 | ✅ 既有 chunkKey 原子计数器 O(1) | 本次审计确认 |

## 3. 评估后主动放弃的候选（红线论证记录）

| 候选 | 放弃原因 |
|------|---------|
| ParticleVisibility 零分配玩家扫描 | Paper getNearbyPlayers 为 AABB **包围盒相交**语义，位置+距离平方改写会在盒角改变可见性判定（红线 1 行为变化）；且为节拍级频率，收益有限 |
| 砧板展示 diff 门（移植 R7） | 5 个调用点全部为「物品必然刚被修改」的事件路径，门永不命中——只会增加开销 |
| 配方书/浏览器图标整体缓存 | 返回的 ItemStack 会被 applyPlaceholders 等就地改写，缓存需完整别名审计，风险/收益比不成立（打开 GUI 为低频动作） |
| CraftEngineItems 静态调用消除 | CE 外部插件内部实现，非本项目代码 |
| Location 逐 tick 分配（potTick L935 等） | 每锅每 tick 1 个年轻代小对象（~10ns 级），缓存需处理世界卸载/重载失效，复杂度不成比例 |

## 4. 结论

- 全部 12 条周期路径与 10 类事件热路径已优化或经证据审计通过；
- 剩余成本要么是 NMS/CE 外部实现（不可触碰），要么是语义性必要工作（bossBar 进度、GUI 刷新的变更传播），要么是低频事件路径；
- 在「不损安全性/稳定性/兼容性（红线 1）与不丢功能（红线 2）」约束下，已无剩余的、可证实收益的优化候选。活动进入稳态；后续若新增功能或依赖版本变化，按 CONTRIBUTING.md §5 协议增量补测。

累计成果（对 1.2.1 原始版，R9 全量回归 + R10 增量）：**31 项可度量提升（1.07x~1498.8x）/ 0 回归**，另有 1 项透明记录的有界权衡与 4 项服务端绑定路径的调用消除论证（R3/R6/R7/R8 各报告）。
