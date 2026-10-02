# PapersDelight 代码分析笔记

> 分析时间：2026-10-02 · 分析范围：仓库全量（主插件 + NMS-Bridge）· 分析模式：完整分析
> 代码规模：225 个 Java 文件 / ~35,018 行 · 无测试 / 无 CI
> 本次分析按要求**未包含** AI 替代方案与 Skill Blueprint

## 项目一句话

PapersDelight 是把 **Farmer's Delight 玩法**（烹饪锅、砧板、煎锅、烤炉、陶罐、篮子、作物、村民交互、营养效果）带到 **Paper/Folia 服务器**的插件：内容由 CraftEngine 资源包定义（方块/物品/模型/配方），玩法逻辑（GUI、tick 循环、自动化、统计）全部在本插件中实现，本仓库为 **1.2.1 社区版（CE）**，高级特性经 `FeatureSupport.EXTENDED=false` 编译期关闭。

## 技术栈

Java 21 · Gradle Kotlin DSL · Paper API 1.21 · Folia 区域调度（CC-Scheduler）· CraftEngine 26.8.1+（强制）· SQLite（统计）· bStats · PlaceholderAPI / Libuid（可选）· Shadow 打包 · paperweight userdev（NMS 桥）

## 笔记目录

| 文档 | 内容 | 篇幅 |
|------|------|------|
| [01-architecture.md](01-architecture.md) | 技术栈矩阵、Gradle 多项目结构、运行时分层、包依赖全景、NMS 桥设计、9 类设计模式、线程模型、架构风险 | 377 行 |
| [02-operation-principles.md](02-operation-principles.md) | 五阶段生命周期、启动决策树、配置/配方装载链、tick 引擎、Folia 并发原语、三条端到端数据流、持久化通道表、热重载三层回滚、错误处理链 | ~370 行 |
| [03-workflow.md](03-workflow.md) | 16 节工作流：烹饪锅/砧板/煎锅/串签/烤炉/陶罐/篮子/作物/土壤/村民/营养/食物函数/命令/进出服/构建流，每流一图 | ~330 行 |
| modules/00-lifecycle-config-registration.md | 生命周期 7 类 + client 3 类 + config 5 类 + registration 14 类 + ce/compat/support/command/Metrics，36 文件函数目录 | 1063 行 |
| modules/01-cookingpot-gui.md | cookingpot 10 类 + gui 10 类（含 124 项 CookingPotManager 方法表），20 文件函数目录 | 920 行 |
| modules/02-jug.md | jug 38 类函数目录（JugManager 74 方法） | 1071 行 |
| modules/03-cutting-skillet-skewer-stove.md | cutting 5 + skillet 12 + skewer 11 + stove 6，34 文件函数目录 | 1251 行 |
| modules/04-farm-villager-misc-effects.md | farm 8 + villager 7 + misc 7 + nourishment 2 + basket 2 + petfood 1 + function 8，35 文件函数目录 | 1044 行 |
| modules/05-recipe-effect-damage-stats.md | recipe 11 + effect 4 + damage 2 + stats 4 + heat 1 + util 9 + common 4，35 文件函数目录 | 950 行 |
| modules/06-nms-bridge-resources.md | NMS-Bridge 30 类 + 四版本差异矩阵 + 构建配置 + 资源文件结构 | 680 行 |
| report/perf/*.md | 性能优化轮次对比报告（基准数据 + 分析结论） | 持续更新 |

**合计约 8,100 行分析文档；全部 225 个源文件的每个类、每个方法均有签名、行号与行为说明。**

## 性能优化轮次（2026-10 起）

| 轮次 | 优化点 | 报告 | 主要结果 |
|------|--------|------|---------|
| R1 | 配方匹配热路径（RecipeTrie 冻结结构/位掩码 DFS、TagExpander、DefaultItemMatcherResolver 键缓存） | [report/perf/01](report/perf/01-r1-recipe-matching.md) | 目标基准 6 项提升 1.3~2.7x，0 回归 |
| R2 | 配置与热源总线（ConfigManager getOr/getList 读穿缓存、HeatSourceService 零分配状态匹配） | [report/perf/02](report/perf/02-r2-config-heat.md) | getOr 命中 3.9~4.5x、静态 miss 21.6x、getList 38.3x、热源 +15~17%（1 项病态动态键 -16.8% 有界代价） |
| R3 | 容器 tick 路径（产物原型缓存消除逐 tick CraftEngine 构建、支撑属性节流、漏斗扫描零分配、缓存门量化） | [report/perf/03](report/perf/03-r3-container-tick.md) | 0 回归；集成路径以调用消除论证；potCache 缓存门 21.2ns vs 全量匹配 312.9ns |
| R4 | 文本解析热路径（TextUtil 解析结果缓存 + 零分配标签扫描） | [report/perf/04](report/perf/04-r4-text-parse.md) | 静态文本解析 **~1291x**、8 行 lore **~606x**、纯 legacy ~34x、病态 miss 路径 +22.8% |
| R5 | 原始 jar 全量回归 + 综合报告 | [report/perf/05](report/perf/05-r5-full-regression.md) | 对 1.2.1 原始版累计 **24 项提升 / 0 回归**（1.2x~1304x），3 项有界代价透明记录 |
| R6 | 物品标识解析与标签键缓存（materialFromId 进程级缓存、matchesAdvancedTag 复用 CE_KEYS、Jug 输入槽先比后克隆） | [report/perf/06](report/perf/06-r6-id-resolution.md) | materialFromId 命中 **12.6~24.1x**、未知名（异常路径）**315x**、advtag 每次省 ~16ns Key 解析；0 回归 |

基准框架与协议见 `benchmark/`（分组独立 JVM、多样本最小值聚合、噪声带规则），操作规范见 [CONTRIBUTING.md](../CONTRIBUTING.md)。

## 核心发现（十条）

1. **两阶段启动 + 快照交接**：onLoad 只做 CraftEngine parser 注册（必须早于 CE 解析资源包），onEnable 装配管理器并经 `RuntimeConfigHandoff` 激活快照；CE PackManager 未就绪即拒绝启动（`PapersDelight.java:123-127`）。
2. **代次令牌替代注销**：parser 一经注册进 CE 永不注销，重载靠 `ParserGeneration` 换代使旧回调静默失效——规避了注销 CE 内部注册表的危险操作。
3. **CE/PE 同源发行**：`support/FeatureSupport.java:5` 的单个常量 `EXTENDED=false` 决定 11 个高级特性的装配与否，同一份源码产出两种发行物，另有 `tryUnlockAllFeatures` 反篡改提示。
4. **Folia 并发原语体系**：会话票据（operationVersion/generation/代际计数）+ 三段跨线程链（快照→调度→验票写回）+ 爆炸两阶段结算 + GUI 挂起，贯穿全部容器机制。
5. **TickBatch 欠账回放**：`container.tick_interval_ticks` 批处理后跳过的 tick 按 `min(elapsed, interval×8)` 回放，烹饪总时长与逐 tick 完全一致（`common/TickBatch.java:14-19`）。
6. **配方匹配双保险**：RecipeTrie 按原料数分根 + stableKey 排序 + DFS 回溯是快路径，miss 后线性贪心 `matches()` 兜底；`RecipeManager` 用 AtomicReference 无锁快照发布。
7. **NMS 四版本桥**：api 纯接口 + 根模块纯反射 + 4 个 paperweight 子项目；1.21.10/11 交易池注入须 `sun.misc.Unsafe` 换不可变静态字段；v1_21_11 出现 `BrainActivityCompatibility` 兼容垫片，版本漂移成本递增。
8. **可选依赖三层隔离**：陶罐 × Libuid 采用「Object 字段 + 反射按需初始化 + JugGate 门面」而非独立 ClassLoader；三阶段安装（解码器/运行时/菜单）独立失败、独立降级。
9. **escrow 事务模式三处复用**：串签（物品 PDC，跨掉落/死亡/重启）、手持煎锅（玩家 PDC）、陶罐交付（状态机 0-3 + 每 20t 补偿泵）——所有中断路径通向完整退款或完整交付。
10. **工程短板**：零自动化测试、零 CI，验证依赖 runServer/runServerFolia 本地起服；三个巨型管理器（锅 1640 行/砧板 1533 行/陶罐 1118 行）集中四类职责；发现少量死代码（BackstabbingEnchantmentRegistrar 主插件无调用方、JugDeliveryTransaction/JugLoreUtil 无调用方、zh_cn.yml 残留 3 个已下线附魔的语言键）。

## 阅读建议

- 想快速了解插件如何跑起来 → [02 运行原理](02-operation-principles.md) 第 1、3 节
- 想改某个机制的逻辑 → 先看 [03 工作流](03-workflow.md) 对应节，再进 modules/ 查函数表
- 想写附属插件 → 架构报告 §3 契约层与 §6.5 服务发布（`MenuService.get()`）
- 想适配新 MC 版本 → [modules/06](modules/06-nms-bridge-resources.md) 版本差异矩阵
