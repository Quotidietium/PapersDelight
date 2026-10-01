<h1 align="center">
  <img src="pd_logo.png" width="360" alt="Paper&#39;s Delight logo"><br>
  Paper's Delight | 农夫乐事·插件版
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x-3fb950" alt="Minecraft 1.21.x">
  <img src="https://img.shields.io/badge/CraftEngine-26.8.1%2B-5865F2" alt="CraftEngine 26.8.1+">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Folia-supported-blueviolet" alt="Folia 支持">
  <img src="https://img.shields.io/badge/API-dev.tako%3Apapersdelight--api-blue" alt="开发者 API">
</p>

<p align="center">
  <a href="README.md">English</a> | <b>简体中文</b>
</p>

<p align="center"><i>在 Paper/Folia 服务端上呈现《农夫乐事》的内容，无需模组即可享用美食佳肴。</i></p>

---

## 📖 简介

Paper's Delight 把 *Farmer's Delight（农夫乐事）* 的整套玩法带到了现代 Paper 服务端：所有自定义方块、物品、贴图与配方都由 **CraftEngine** 驱动，玩法逻辑集中在同一个插件 jar 内，并且完整适配 **Folia** 的区域化线程模型。

- 方块、物品、模型、配方数据：**CraftEngine 资源包**
- 玩法逻辑、GUI、统计：**本插件**
- 附属插件开发（菜单、配方类型、效果、伤害类型、调度器）：**PapersDelight API**

## ✨ 功能

| 机制 | 说明 |
| --- | --- |
| 🍲 **厨锅** | 多材料配方、容器处理、成品盛取、漏斗自动化、*配方筛选及一键填入 |
| 🔥 **炉灶** | 6 槽营火式烹饪、自定义伤害类型 |
| 🍳 **煎锅** | 方块煎锅、*手持烹饪进度条与翻面 |
| 🔪 **砧板** | 工具切割、可插入工具、发射器交互 |
| 🫙 **液罐** | 流体存储与转运、浸泡配方（需要 [Libuid](https://github.com/Paperized-Modding/Libuid)） |
| 🧺 **篮子** | 自动化掉落物收集、红石锁定 |
| 🌾 **种植整合** | 更贴近原版机制的种植系统 |
| ✨ **持续效果** | 滋养效果，带 BossBar 显示并持久化到 PDC |
| 🏡 ***村民机制** | 村民收获、种植、交易、繁殖 |
| 📊 ***统计** | 可选的 SQLite 玩家统计 |

注: 带*的特性为高级版专属特性。

## 🧩 运行依赖

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Paper **或** Folia | 1.21.X ~ 26.X | `api-version: 1.21` |
| **CraftEngine** | **26.8.1+** | **必需** |
| Java | 21 | 服务端运行时 |
| PlaceholderAPI | 2.11.6+ | 可选，启用文本占位符 |
| Libuid | 1.0.1+ | 可选，启用液罐流体机制 |

## 🚀 安装

1. 把 **CraftEngine** 放进 `plugins/`，启动一次服务器让它生成目录结构。
2. 把 **PapersDelight 资源包**放进 CraftEngine 的资源目录：
   ```text
   plugins/CraftEngine/resources/<pack>/
   ```
   资源包里包含插件用到的全部方块、物品、模型与配方定义。
3. 把 `PapersDelight-<版本>-CE.jar` 放进 `plugins/`。
4. 重启服务器。初始化成功后控制台会输出启动耗时。

## 🗂 生成的文件

```text
plugins/PapersDelight/
├── config.yml      # 主配置（语言、热源、粒子节流、统计…）
├── gui.yml         # GUI 布局、图标与文本
└── lang/           # 语言文件
```

所有自定义**内容**定义（方块、物品、配方、掉落）在 CraftEngine 资源包里，不在这个目录。

## 🎮 指令与权限

| 指令 | 权限 | 说明 |
| --- | --- | --- |
| `/pd help` | — | 指令总览 |
| `/pd status` | — | 各机制与前置集成的运行状态 |
| `/pd version` | — | 版本信息 |
| `/pd recipe` | `papersdelight.recipe` | 配方浏览器（高级版特性） |
| `/pd reload` | `papersdelight.reload` | 重载 `config.yml`、`gui.yml`、语言文件与 CraftEngine parser |
| `/pd inspect` | `papersdelight.dev` | 检查主手物品/准星方块（原版 ID、CraftEngine ID、数量、坐标） |
| `/pd effect <nourishment> <秒数>` | `papersdelight.dev` | 测试用：给自身施加滋养效果 |
| `/pd jug fluid-items` | `papersdelight.dev` | 列出已注册的液罐流体物品 |

## ⚙️ 配置要点

```yaml
lang: zh_cn                # 使用 lang/ 下的哪个语言文件

heat_sources: …            # 能加热锅/煎锅的方块（原版 ID、CraftEngine ID 或方块标签）
particle_throttle: …       # 全服粒子预算

container:
  # 1 = 每 tick 执行容器逻辑（默认，与旧行为完全一致）
  # 4 = 批处理：把快照/回写/粒子/GUI 等重活合并，
  #     跳过多少 tick 就在下一次补跑多少次，因此烹饪时长不会变慢
  tick_interval_ticks: 1
```

## ☕ 支持作者

购买 Paper's Delight **高级版**，解锁高级版专属特性，并立即获得**八款**免费附属内容。

**添加 QQ 1612948730（推荐）** 或从[爱发电页面](https://afdian.com/item/64f4bc007ad111f1b2b75254001e7c00)（平台向作者收取 6% 服务费，购买价不变）以 ￥30 的价格购买。

## 🔌 开发者 API

PapersDelight 为附属插件提供稳定 API：菜单契约、配方类型注册、物品匹配、伤害类型契约与能力门。API 已发布到我们的 Maven 仓库，使用 `compileOnly` 引入——运行时由 PapersDelight 插件提供这些类。

这份 API **只提供契约**：不含菜单引擎、效果引擎、CraftEngine 工具与调度器，POM 也**不声明任何依赖**，因此接入它不会给你的附属插件带来任何第三方库。运行时能力通过插件实现的契约获取——例如 GUI 引擎是以服务形式发布的：

```java
MenuService menus = MenuService.get();
if (menus != null) menus.openMenu(player, "my_module");
```

**仓库地址**

```kotlin
repositories {
    maven("https://mvn.hezhongkj.top/releases/")
}
```

**Gradle（Kotlin DSL）**

```kotlin
dependencies {
    compileOnly("dev.tako:papersdelight-api:4.0.0")
}
```

**Gradle（Groovy DSL）**

```groovy
dependencies {
    compileOnly 'dev.tako:papersdelight-api:4.0.0'
}
```

**Maven**

```xml
<repository>
    <id>hezhong</id>
    <url>https://mvn.hezhongkj.top/releases/</url>
</repository>

<dependency>
    <groupId>dev.tako</groupId>
    <artifactId>papersdelight-api</artifactId>
    <version>4.0.0</version>
    <scope>provided</scope>
</dependency>
```

## 🙏 致谢

- **作者：** [Shimamura Tako](https://github.com/Shimamura-Tako)、[Mr Dg32z_](https://github.com/Dg32z)
- **贡献者：** [yuuka0](https://github.com/yuuka0desu)、[gukuan](https://github.com/GUKUAN)、[Cold Leaves](https://github.com/FOXLeaves)、还有[你们](https://github.com/Paperized-Modding/PapersDelight/graphs/contributors)
- **灵感来源：** [Farmer's Delight](https://github.com/vectorwing/FarmersDelight)（vectorwing）——机制与素材设计
- **技术支持：** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine)、[Paper](https://github.com/PaperMC/Paper)、[Folia](https://github.com/PaperMC/Folia)

## 📄 许可证与贡献

本项目以 [GNU Affero 通用公共许可证 v3.0（AGPL-3.0）](LICENSE) 授权。请在分发、修改或通过网络提供本项目及其衍生作品时遵守许可证条款。

欢迎提交 Issue 和 Pull Request。提交贡献即表示你同意其内容可按本项目许可证发布，并请在提交前确认变更符合项目的代码规范与许可证要求。

