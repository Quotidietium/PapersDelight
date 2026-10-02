# PapersDelight 工作流报告

> 分析时间：2026-10-02 · 配套：[架构报告](01-architecture.md) · [运行原理报告](02-operation-principles.md)
> 本报告覆盖玩家业务工作流（每个机制一张图）、管理命令工作流与开发构建工作流；函数级调用链见各 [modules/](modules/) 文件。

---

## 0. 工作流总目录

| # | 工作流 | 参与者 | 核心类 | 详情 |
|---|--------|--------|--------|------|
| 1 | 烹饪锅烹饪与取餐 | 玩家 / 漏斗 | `CookingPotManager` | [modules/01](modules/01-cookingpot-gui.md) |
| 2 | 砧板切割与自动化 | 玩家 / 漏斗 / 发射器 | `CuttingBoardManager` | [modules/03](modules/03-cutting-skillet-skewer-stove.md) |
| 3 | 煎锅（方块+手持） | 玩家 | `SkilletManager` | [modules/03](modules/03-cutting-skillet-skewer-stove.md) |
| 4 | 手持串签 | 玩家 | `HandheldSkewerManager` | [modules/03](modules/03-cutting-skillet-skewer-stove.md) |
| 5 | 烤炉与高温伤害 | 玩家 / 生物 | `StoveManager` | [modules/03](modules/03-cutting-skillet-skewer-stove.md) |
| 6 | 陶罐流体与浸泡 | 玩家 / 漏斗 / Libuid | `JugManager` | [modules/02](modules/02-jug.md) |
| 7 | 篮子自动收集 | 掉落物 / 红石 | `BasketManager` | [modules/04](modules/04-farm-villager-misc-effects.md) |
| 8 | 作物种植与生长 | 玩家 / 村民 | `mechanic/farm/*` | [modules/04](modules/04-farm-villager-misc-effects.md) |
| 9 | 土壤转化链 | 玩家 / 时间 | `OrganicCompost`/`RichSoil` | [modules/04](modules/04-farm-villager-misc-effects.md) |
| 10 | 村民四大行为 | 村民 AI | `mechanic/villager/*` + NMS 手术 | [modules/04](modules/04-farm-villager-misc-effects.md) |
| 11 | 营养效果 | 玩家 | `NourishmentManager` + `TimedEffectManager` | [modules/04](modules/04-farm-villager-misc-effects.md) · [modules/05](modules/05-recipe-effect-damage-stats.md) |
| 12 | 食物效果函数 | 玩家 | `mechanic/function/*` | [modules/04](modules/04-farm-villager-misc-effects.md) |
| 13 | 管理命令 / 重载 / 构建开发流 | 管理员 / 开发者 | `PapersDelightCommand` / Gradle | §13-15 |

---

## 1. 烹饪锅：从放置到取餐

```mermaid
sequenceDiagram
    participant P as 玩家
    participant B as CookingPotBlockBehavior
    participant M as CookingPotManager
    participant G as CookingPotMenu GUI
    participant T as potTick 循环
    participant R as RecipeTrie

    P->>B: 放置烹饪锅
    B->>M: 创建 BlockEntityController
    P->>B: 右键锅身
    B->>M: openMenu
    M->>G: 构建 27 格菜单 6输入+燃料位+展示位
    P->>G: 放入食材与碗/餐具
    G->>G: session.invalidate 版本+1
    T->>T: TickBatch 到期 扫描 6 输入槽
    T->>R: findMatch 原料多重集匹配
    R-->>T: CookingRecipe 命中
    T->>T: 创建 CookingSession 开始计时
    T->>G: 进度条更新 + 粒子节流展示
    T->>T: finishCooking 生成 waitingOutput
    P->>G: 手持空碗右键成品位
    G->>M: 组装 Meal 物品 Lore+份数+容器
    M->>P: 给餐 + 触发营养效果注册
```

**决策点**：配方匹配三重缓存校验（位置 + 6 槽指纹 + snapshotEpoch）未命中也以 NO_MATCH 哨兵做负缓存；热源失效时进度回退冷却而非清零。

**异常恢复**：破坏锅中途端锅（PDC 迁移）；关菜单瞬间区块卸载（pendingUnloads + ChunkLoad 重试）；爆炸两阶段结算；比较器红石信号（有等待输出时输出 15）。

---

## 2. 砧板：切割与全自动化

```mermaid
flowchart TD
    A["放置砧板"] --> B["展示实体 ItemDisplay 建立非持久化"]
    B --> C{"交互方式"}
    C -->|手持工具右键| D["工具插入展示位 ToolPosition"]
    C -->|手持物品右键| E["物品置于砧板上方"]
    C -->|空手/潜行右键| F["取出物品或工具"]
    D --> G{"点击砧板<br/>或漏斗推入物品?"}
    E --> G
    G -->|触发切割| H["CuttingRecipe 匹配<br/>工具谓词 + 物品谓词"]
    H -->|"命中"| I["工具损耗耐久<br/>产出向方块右侧弹出"]
    H -->|"未命中"| J["物品保留原地"]
    I --> K{"工具耐久归零?"}
    K -->|是| L["工具破碎音效 + 移除工具"]
    K -->|否| M["可继续切割"]
    E --> N{"自动化路径"}
    N -->|"漏斗在上方/侧向"| O["8 tick 周期 按区块分组<br/>推入物品触发切割"]
    N -->|"漏斗在下方"| P["拉取产出物品"]
    N -->|"发射器对准砧板"| Q["发射器切割<br/>工具损耗作用于发射器背包槽位"]
    O --> H
    Q --> H
```

**决策规则**：工具匹配优先级为「CE 物品谓词 → 原版工具类型」；`insertable_tools.yml` 先到先得匹配 + `default` 变换继承。

**异常恢复**：区块卸载移除展示实体、加载重建；`removeDisplayEntity` 附带清扫半径 0.75 内游离 ItemDisplay 自愈；漏斗任务带 `hopperGeneration` 代际计数防 reload 后旧任务复活。

---

## 3. 煎锅：方块形态与手持形态

```mermaid
stateDiagram-v2
    [*] --> 空锅 : 放置单槽煎锅
    空锅 --> 加热中 : 放入食材 且 HeatSourceService 判定下方热源 有效
    加热中 --> 加热中 : 复用原版 CampfireRecipe 计时 热源判定缓存10pass
    加热中 --> 冷却 : 热源消失 进度每tick减2回退
    冷却 --> 加热中 : 热源恢复 继续计时
    加热中 --> 完成 : 进度满 物品弹出
    完成 --> 空锅 : 产出弹出
```

手持形态（高级版特性，CE 版编译关闭）：

```mermaid
flowchart LR
    A["主手持煎锅 use"] --> B["副手原料 escrow 托管进玩家 PDC"]
    B --> C["长按开始烹饪<br/>13 段耐久条渲染进度"]
    C --> D{"跳跃?"}
    D -->|"落地时机在窗口内"| E["FlipTracker 判定翻面成功<br/>换 composite 模型 + 加速"]
    D -->|"时机错误"| F["翻面失败 进度惩罚"]
    C --> G{"完成?"}
    G -->|是| H["产出进背包 退还煎锅"]
    G -->|"松手/切槽/退服"| I["escrow 退款 原料原样返回"]
    E --> G
    F --> G
```

原料图标用运行时生成的 composite item_model（`ItemModelGenerator` 写入 CE 资源包 `pd_generated_model` + manifest 增量清理）。

---

## 4. 手持串签：托管与防作弊纵深

```mermaid
flowchart TD
    A["使用串签物品"] --> B["UseGate 检查<br/>主手/潜行/冷却"]
    B --> C["整叠原料写入代理物品 PDC<br/>escrow 会话ID+原料字节+原始/剩余数量"]
    C --> D["长按逐串烤制<br/>进度条 15 段"]
    D --> E{"单串完成"}
    E --> F["completeOne 前<br/>字节级一致性比对"]
    F -->|"一致"| G["产出一份 递减剩余"]
    F -->|"不一致"| H["拒绝并回滚"]
    D --> I{"中断? 掉落/死亡/退服/关服"}
    I -->|"任一"| J["PDC escrow 存活<br/>重新持有时恢复进度"]
    I -->|"容器搬运试图偷原料"| K["HIGHEST 优先级守卫取消"]
    G --> L{"剩余归零?"}
    L -->|是| M["退还/消耗串签 收尾"]
    L -->|否| D
    J --> D
```

消费裁决由 13 项证据的纯函数 `ConsumeValidator` 完成——「托管进 PDC + 证据裁决 + 字节比对 + 事件守卫」四层构成防作弊纵深。

---

## 5. 烤炉：六槽并行与高温伤害

```mermaid
flowchart TD
    A["放置烤炉"] --> B["六面/六点物品展示位注册"]
    B --> C["六个槽位独立计时<br/>复用 CampfireRecipe 缓存"]
    C --> D{"上方方块遮蔽?"}
    D -->|是| E["强制弹出该槽物品<br/>防窒息式烹饪"]
    D -->|否| F["继续烹饪 粒子+环境音带抖动间隔"]
    F --> G{"完成"}
    G -->|是| H["产物弹出"]
    F --> I{"同区块粒子预算<br/>ParticleThrottle 超限?"}
    I -->|是| J["本 tick 跳过粒子"]
    I -->|否| K["播发 + 观察者视距裁剪"]
    H --> L["完成"]
    subgraph HT["高温方块 HighTemperature 独立行为"]
        M["生物踩上高温方块"] --> N{"潜行或冰霜行者?"}
        N -->|"免疫"| O["无伤害"]
        N -->|否| P{"burn_area 0-16像素<br/>AABB 相交判定"}
        P -->|是| Q["三级伤害回退链<br/>自定义 DamageTypes 到 NMS 原版类型 到 裸伤害"]
        P -->|否| O
    end
```

环境音优先 NMS `playSoundByKey`，失败回退 Bukkit `playSound`。

---

## 6. 陶罐：流体四部曲

```mermaid
flowchart TD
    A["陶罐交互入口"] --> B{"Libuid 可用?"}
    B -->|"否 JugGate 拦截"| C["JugUnavailableNotice 提示<br/>机制整体降级"]
    B -->|是| D{"玩家动作"}
    D -->|"手持流体容器右键"| E["灌入 JugFluidFillingRecipe<br/>容量上限校验 液位模型切换"]
    D -->|"手持空容器右键"| F["倒出 JugFluidEmptyingRecipe<br/>expressionStack 纯流体校验 可回滚"]
    D -->|"投入浸泡物品"| G["浸泡配方 JugSoakingRecipe<br/>tickJug 逐秒推进"]
    D -->|"GUI 打开"| H["菜单会话 generation 防旧回调<br/>skipNextInputRead 双写竞态解"]
    E --> I["JugCapacityBar 耐久条伪装容量<br/>round amount/1000 切换 16 档模型"]
    F --> I
    G --> J{"浸泡完成"}
    J -->|是| K["产出 + 消耗原料"]
    H --> L{"换手持容器"}
    L -->|"是"| M["JugDeliveryFlow 状态机 0到3<br/>立即扣 1 tick 后付"]
    M -->|"玩家离线/任务失败"| N["兜底世界掉落 + 罐体回滚<br/>retain 挂补偿队列 每20t重试"]
```

**失效流体保全**：Libuid 判 INVALID 的流体以原始字节存 PDC（`writeOpaqueFluid`），掉落/放置往返不丢，Libuid 回归后可恢复。

---

## 7. 篮子：自动收集

```mermaid
flowchart TD
    A["放置篮子"] --> B["注册进全局心跳表"]
    B --> C["全局 1t 心跳<br/>每 tick 最多处理 32 个篮子"]
    C --> D{"红石锁定 enabled 属性<br/>neighborChanged 维护"}
    D -->|"锁定"| E["跳过本 tick"]
    D -->|激活| F["收集半径内掉落物<br/>按 chunkKey 合并派发区域任务"]
    F --> G{"篮内有空间?"}
    G -->|是| H["吸入物品"]
    G -->|否| I["标记满 跳过"]
    E --> J{"连续空转次数"}
    H --> J
    J -->|"指数退避 1到32t"| K["降低心跳频率"]
    J -->|"有活动"| C
```

---

## 8. 作物：三套行为同构复用

```mermaid
stateDiagram-v2
    direction LR
    state 生长与收获 {
        [*] --> 幼苗 : 种植于合法土壤
        幼苗 --> 成熟 : 随机刻 vanilla速度公式 邻居加成 同作物减半
        成熟 --> 收获 : 右键 或 村民收割 或 骨粉链
        收获 --> 幼苗 : 种子回种
    }
```

三套行为的差异点：

| 行为类 | 结构特点 | 特有机制 |
|--------|---------|---------|
| `AdvancedCropBlockBehavior` | 单格基线 | 三级土壤匹配 标签→vanilla 状态→CE id |
| `DoubleCropBlockBehavior` | 上/下半双方块 | `syncAges` 上下半同步模式；野生稻 `WildRiceBlockBehavior` 水生变体 |
| `RopedCropBlockBehavior` | 绑绳攀爬 | 破坏/水淹绑绳节后整段柱还原为绳方块 跨 Folia 区域逐格调度 |

配套：`FarmlandBlockBehavior`（耕地行为）、`CropBonemealFix`（潜行骨粉右键以 LOWEST 优先级 DENY 物品使用，防原版链路误吞——三个作物 Factory 在 create 时自动登记方块 id）。

---

## 9. 土壤转化链

```mermaid
flowchart LR
    A["有机堆肥 OrganicCompost"] -->|"激活剂 + 光 + 水<br/>概率堆肥推进"| B["转化为 rich_soil 默认产物"]
    B --> C["RichSoil 富饶土壤"]
    C -->|"对上下作物"| D["免费骨粉<br/>优先 CE BonemealableBlock 兜底 NMS tryBonemeal"]
    C -->|"conversions 规则"| E["整块转化为其他土壤类型"]
```

---

## 10. 村民：四大行为管理器

```mermaid
flowchart TD
    A["村民生成/加载"] --> B["VillagerBehaviorSurgery<br/>refreshBrain 脑手术"]
    B --> C["WORK 活动注入 CeHarvestFarmland<br/>优先级5 JOB_SITE 条件"]
    B --> D["反射摘除原版 TradeWithVillager<br/>注入 CeTradeWithVillager 到 MEET/IDLE"]
    C --> E["收割补种 双缓存决策<br/>replantIndex + PlantingMemo"]
    D --> F["交易 自定义物品可分享<br/>WANDERING_TRADER_TRADES 注入"]
    subgraph PM["其余三个管理器 独立监听器"]
        G["VillagerPickupManager<br/>拾取掉落食物 isOwnedByCurrentRegion 双校验"]
        H["VillagerBreedManager<br/>繁殖意愿 基于食物点 VillagerFoodPointSetting"]
        I["VillagerTradeManager<br/>交易池注入 幂等标记 PapersDelightListing"]
    end
```

交易池注入的版本断层：1.21.1/1.21.4 直接 put 可变 Map；1.21.10/11 变不可变 `List<Pair>`，须 `sun.misc.Unsafe` 换静态字段；v1_21_11 的 LEGACY 三态探测路径直接复用 v1_21_10 的 Injector。

---

## 11. 营养效果（Nourishment）

```mermaid
sequenceDiagram
    participant P as 玩家
    participant F as NourishmentFunction CE函数
    participant N as NourishmentManager
    participant T as TimedEffectManager
    participant PDC as 玩家 PDC

    P->>F: 吃下带 nourishment 配置的食物
    F->>T: 注册计时效果 byQualifiedId
    T->>N: 营养会话建立 bossbar 展示
    N->>P: 饱食恒定时 always-eat 饥饿值 20 写 19
    loop 每 2 tick
        T->>T: internalTick 剩余减 2 EntityScheduler 路由
        T->>P: bossbar 更新
    end
    alt 剩余归零
        T->>P: 效果移除 bossbar 撤销 饥饿恢复
    else 玩家退服
        T->>PDC: LOWEST 优先级先写 int[3]+byte[]
        Note over PDC: 先恢复饥饿 20 再 persist 防存档 19
    else 玩家加入
        PDC-->>T: MONITOR +20t 延迟恢复会话
    else 死亡/喝奶
        T->>T: 会话清除
    end
```

---

## 12. 食物效果函数管线（CE ItemFunction）

```mermaid
flowchart TD
    A["玩家吃完自定义食物<br/>CE 触发 function"] --> B{"function 类型"}
    B -->|Nourishment| C["注册营养 见第11节"]
    B -->|UpgradeEffect| D["效果升级<br/>先查 TimedEffectManager 注册表<br/>miss 回落 vanilla Registry.EFFECT"]
    B -->|RemoveEffect| E["移除效果 双轨寻址同上"]
    B -->|RandomRemoveEffect| F["随机移除 N 个"]
    B -->|ChorusTeleport| G["紫颂果式随机传送"]
    B -->|EndermanGristleTeleport| H["末影人鞍传送 变体"]
    D --> I["IsSneakingCondition 条件前置<br/>可要求潜行才生效"]
    E --> I
    F --> I
    C --> J["效果落位"]
    G --> J
    H --> J
```

双轨寻址是复用关键：同一函数配置可同时作用于自定义计时效果与原版药水效果。

---

## 13. 管理命令工作流

```mermaid
flowchart TD
    A["/pd 子命令"] --> B{"子命令分发表<br/>PapersDelightCommand"}
    B -->|help| C["命令总览"]
    B -->|status| D["各机制与集成运行时状态表"]
    B -->|version| E["插件版本 + CE 版本门槛"]
    B -->|reload| F{"papersdelight.reload 权限"}
    B -->|inspect| G{"papersdelight.dev"}
    B -->|effect| G
    B -->|jug fluid-items| G
    B -->|recipe| H{"papersdelight.recipe<br/>且 PE 版 recipeBrowser 特性"}
    F -->|"通过"| I["ReloadCoordinator 全链路重载<br/>见运行原理报告第7节"]
    F -->|拒绝| J["无权限提示"]
    G -->|"通过"| K["开发调试<br/>inspect 手持物/目标方块 id<br/>effect 施加营养测试<br/>jug fluid-items 列流体物品"]
    H -->|通过| L["打开配方浏览器"]
    H -->|"CE 版"| M["子命令不存在"]
    N["/fd 别名 farmersdelight"] --> L
```

`/pd` 与 `/fd` 经 `CommandMap.register` 动态注册（`PapersDelight.java:331-352, 360-382`），不是 plugin.yml 声明式命令——因此 tab 补全走自定义 `onTabComplete`。

---

## 14. 玩家加入/退出的全局联动

```mermaid
flowchart LR
    A["PlayerJoinEvent"] --> B["TimedEffectManager<br/>MONITOR +20t 恢复 PDC 效果"]
    A --> C["StatsManager 异步 warmUp<br/>单飞防重复预热"]
    A --> D["手持煎锅/串签 escrow<br/>校验并恢复托管状态"]
    E["PlayerQuitEvent"] --> F["TimedEffectManager<br/>LOWEST 先存 PDC"]
    E --> G["StatsManager 缓存可留守<br/>由 flush 周期带走"]
    E --> H["GUI 会话清理<br/>closeAll 触发持久化三段链"]
```

---

## 15. 开发构建工作流

```mermaid
flowchart LR
    A["开发者"] --> B{"gradlew 任务"}
    B -->|build| C["shadowJar 产出 PapersDelight-版本.jar<br/>重定位 jetbrains/ccscheduler 排除 META-INF<br/>BUILD_FOLDER 可外置输出"]
    B -->|runServer| D["Paper 1.21 + Java21 + 4G<br/>自动挂载 shadowJar"]
    B -->|runServerFolia| E["Folia 26.1.2 + Java25<br/>sun-misc-unsafe 开关"]
    C --> F["人工部署到服务器 plugins 目录冒烟测试"]
    D --> F
    E --> F
    F --> G{"手工验证"}
    G -->|"发现问题"| H["回到编码 无自动化测试与CI"]
    G -->|通过| I["git 提交到 main 分支"]
```

开发流特征（风险面）：

- **无测试源集**：35k 行代码零单元测试；
- **无 CI/CD**：无 workflows 配置，构建完全依赖本地环境（Java 21 + 25 双 toolchain）；
- **验证手段**：`runPaper` 插件的 `runServer`/`runServerFolia` 双运行时本地起服，`/pd status`、`/pd inspect`、`/pd effect`、`/pd jug fluid-items` 是内置的调试探针；
- **多版本联动**：NMS-Bridge 各子项目用 paperweight userdev 锁定各 MC 版本dev-bundle，修改 Bridge 接口需同步 4 个版本实现 + api 模块。

---

## 16. 跨工作流的公共约束

所有业务工作流共享以下不变式（提取自代码显式条件）：

| 不变式 | 实现位置 | 含义 |
|--------|---------|------|
| 容器烹饪总时长与 tick 间隔配置无关 | `common/TickBatch.java:14-19` | 跳 tick 必须欠账回放 上限 8 倍 |
| 异步写回前必验会话票 | `CookingPotManager` operationVersion / `JugMenuSessionRegistry` generation | 玩家操作永远胜过异步任务 |
| escrow 事务要么完整退款要么完整交付 | 串签/煎锅/陶罐交付 | 所有中断路径都通向恢复 |
| 配方查询零锁 | `RecipeManager` AtomicReference | 读路径永不阻塞游戏线程 |
| 统计查询永不阻塞 | `StatsManager` 缓存 miss 返回 0 + 异步预热 | 占位符/命令线程安全 |
| 伤害路径必有原版回退 | `DamageTypes` 三级回退 | 自定义注册失败不影响玩法 |
| 特性门控单一开关 | `support/FeatureSupport.java:5` | CE 版 11 个高级特性编译期关闭 |
