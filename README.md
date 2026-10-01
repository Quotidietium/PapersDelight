<h1 align="center">
  <img src="pd_logo.png" width="360" alt="Paper&#39;s Delight logo"><br>
  Paper's Delight
</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x-3fb950" alt="Minecraft 1.21.x">
  <img src="https://img.shields.io/badge/CraftEngine-26.8.1%2B-5865F2" alt="CraftEngine 26.8.1+">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Folia-supported-blueviolet" alt="Folia supported">
  <img src="https://img.shields.io/badge/API-dev.tako%3Apapersdelight--api-blue" alt="Developer API">
</p>

<p align="center">
  <b>English</b> | <a href="README_zh.md">简体中文</a>
</p>

<p align="center"><i>Farmer's Delight content for Paper/Folia servers, bringing delicious meals without requiring mods.</i></p>

---

## 📖 About

PapersDelight brings the *Farmer's Delight* experience to modern Paper servers. All custom blocks, items, textures and recipes are driven by **CraftEngine**, while the gameplay logic lives in a single plugin jar that is fully compatible with **Folia**'s regionised threading model.

- Blocks, items, models and recipe data: **CraftEngine resource pack**
- Gameplay logic, GUIs and statistics: **this plugin**
- Addon development (menus, recipe types, effects, damage types, schedulers): **PapersDelight API**

## ✨ Features

| Mechanic | Description |
| --- | --- |
| 🍲 **Cooking Pot** | Multi-ingredient recipes, container handling, meal serving, hopper automation, *recipe filtering and one-click filling |
| 🔥 **Stove** | Six-slot campfire-style cooking and custom damage types |
| 🍳 **Skillet** | Block skillet, *handheld cooking progress bar and flipping |
| 🔪 **Cutting Board** | Tool-based cutting, insertable tools and dispenser interaction |
| 🫙 **Jug** | Fluid storage and transport and soaking recipes (requires [Libuid](https://github.com/Paperized-Modding/Libuid)) |
| 🧺 **Basket** | Automated item collection and redstone locking |
| 🌾 **Farming integration** | Farming closer to vanilla mechanics |
| ✨ **Timed Effects** | Nourishment effects with boss bar display, persisted in PDC |
| 🏡 ***Villager mechanics** | Villager harvesting, planting, trading and breeding |
| 📊 ***Statistics** | Optional SQLite-backed player statistics |

Features marked with * are exclusive to the Premium Edition.

## 🧩 Requirements

| Component | Version | Notes |
| --- | --- | --- |
| Paper **or** Folia | 1.21.X ~ 26.X | `api-version: 1.21` |
| **CraftEngine** | **26.8.1+** | **Required** |
| Java | 21 | Server runtime |
| PlaceholderAPI | 2.11.6+ | Optional, enables placeholder support |
| Libuid | 1.0.1+ | Optional, enables the Jug fluid mechanic |

## 🚀 Installation

1. Install **CraftEngine** into `plugins/` and start the server once so it creates its folder structure.
2. Install the **PapersDelight resource pack** into CraftEngine's resources folder:
   ```text
   plugins/CraftEngine/resources/<pack>/
   ```
   The pack contains every block, item, model and recipe used by the plugin.
3. Drop `PapersDelight-<version>-CE.jar` into `plugins/`.
4. Restart the server. The console reports the startup time once the plugin initialises successfully.

## 🗂 Generated files

```text
plugins/PapersDelight/
├── config.yml      # main configuration (lang, heat sources, throttles, stats…)
├── gui.yml         # GUI layouts, icons and texts
└── lang/           # language files
```

All custom **content** definitions (blocks, items, recipes, loot) live in the CraftEngine resource pack, not in this folder.

## 🎮 Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/pd help` | — | Command overview |
| `/pd status` | — | Runtime status of mechanics and integrations |
| `/pd version` | — | Plugin version information |
| `/pd recipe` | `papersdelight.recipe` | Recipe browser (Premium Edition feature) |
| `/pd reload` | `papersdelight.reload` | Reload `config.yml`, `gui.yml`, language files and CraftEngine parsers |
| `/pd inspect` | `papersdelight.dev` | Inspect the held item / targeted block (vanilla id, CraftEngine id, amount, position) |
| `/pd effect <nourishment> <seconds>` | `papersdelight.dev` | Apply nourishment for testing |
| `/pd jug fluid-items` | `papersdelight.dev` | List registered jug fluid items |

## ⚙️ Configuration highlights

```yaml
lang: zh_cn                # language file used from lang/

heat_sources: …            # blocks that heat pots/skillets (vanilla ids, CraftEngine ids or block tags)
particle_throttle: …       # global particle budget

container:
  # 1 = run container logic every tick (default, identical to legacy behaviour)
  # 4 = batching: heavy work (snapshot/write-back/particles/GUI)
  #     is merged, while skipped ticks are replayed so cooking time stays identical
  tick_interval_ticks: 1
```

## ☕ Support the Author

Purchase the **Premium Edition** of Paper's Delight to unlock premium-only features and receive **eight** free addon contents.

**Add QQ 1612948730 (recommended)** or purchase it for ￥30 via the [Afdian page](https://afdian.com/item/64f4bc007ad111f1b2b75254001e7c00). The platform charges the author a 6% service fee; the purchase price remains unchanged.

## 🔌 Developer API

PapersDelight exposes a stable API for addon plugins: menu contracts, recipe types, item matchers, damage-type contracts and capability gates. It is published to our Maven repository and consumed as `compileOnly` — the classes are provided by the PapersDelight plugin at runtime.

The API is **contracts only**. It ships no menu engine, no effect engine, no CraftEngine helper and no scheduler, and its POM declares **no dependencies**, so linking against it never adds a third-party library to your addon. Runtime features are reached through contracts the plugin implements — the GUI engine, for instance, is published as a service:

```java
MenuService menus = MenuService.get();
if (menus != null) menus.openMenu(player, "my_module");
```

**Repository**

```kotlin
repositories {
    maven("https://mvn.hezhongkj.top/releases/")
}
```

**Gradle (Kotlin DSL)**

```kotlin
dependencies {
    compileOnly("dev.tako:papersdelight-api:4.0.0")
}
```

**Gradle (Groovy DSL)**

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

## 🙏 Credits

- **Authors:** [Shimamura Tako](https://github.com/Shimamura-Tako), [Mr Dg32z_](https://github.com/Dg32z)
- **Contributors:** [yuuka0](https://github.com/yuuka0desu), [gukuan](https://github.com/GUKUAN), [Cold Leaves](https://github.com/FOXLeaves), and [you](https://github.com/Paperized-Modding/PapersDelight/graphs/contributors)
- **Inspired by:** [Farmer's Delight](https://github.com/vectorwing/FarmersDelight) (vectorwing) — mechanics and asset design
- **Powered by:** [CraftEngine](https://github.com/Xiao-MoMi/craft-engine), [Paper](https://github.com/PaperMC/Paper), [Folia](https://github.com/PaperMC/Folia)

## 📄 License and Contributions

This project is licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE). Please comply with its terms when distributing, modifying or making this project or derivative works available over a network.

Issues and pull requests are welcome. By submitting a contribution, you agree that it may be released under this project's license. Please ensure that your changes follow the project's conventions and licensing requirements before submitting them.
