# PapersDelight 项目规范（工程约定）

本文件是本仓库的工程规范。任何代码变更（尤其是性能优化）都必须遵守。

## 1. 两条红线（不可违反）

1. **安全性 / 稳定性 / 兼容性优先**：公开 API（`papersdelight-api` 契约、`paper-plugin.yml` 行为、配置文件格式、命令与权限）不得出现破坏性变更；事件优先级、可见的游戏行为、持久化数据格式必须保持等价。
2. **禁止为性能牺牲功能**：任何优化不得删除或弱化现有功能路径（含社区版/高级版双发行的门控逻辑）。

## 2. 变更流程：每一轮改动必须完整走完

每次源代码更新（无论大小）都必须：

1. **编译打包**：`./gradlew shadowJar`（只重编受影响模块，见 §3 细粒度构建）。
2. **跑基准**：优化类改动必须先建基线（改动前 jar 存入 `benchmark/lib/<label>/`），改动后用同一 harness 对比：
   ```bash
   ./gradlew benchmarkClasses benchmarkRuntime
   bash benchmark/run-bench.sh baseline
   bash benchmark/run-bench.sh candidate
   python benchmark/compare.py baseline candidate
   ```
3. **自检**：benchmark 自带正确性 self-check（AssertionError 即失败），失败禁止合入。
4. **更新笔记**：受影响模块的 `note/modules/*.md` 方法表与 `note/report/perf/` 对比报告必须同步更新。
5. **提交**：`git add` 相关源码 + note + benchmark + 本文件；推送失败可暂时放弃，禁止丢弃本地提交。

## 3. 细粒度构建 dist

- 常规验证：`./gradlew shadowJar` —— Gradle 增量编译，只重编受影响子项目。
- 交付打包：`./gradlew dist` —— 把最终 fat jar、各 NMS-Bridge 模块 jar 与基准运行时统一汇总到项目根 `dist/` 目录：
  ```
  dist/
  ├── PapersDelight-<version>.jar        # shadowJar 产物（可直接投入 plugins/）
  ├── modules/                           # NMS-Bridge api 与各版本模块 jar
  └── benchmark/                         # 基准 classes + 运行时依赖 + run 脚本副本
  ```
- 严禁把未通过 `shadowJar` 的代码标记为完成。

## 4. 磁盘与缓存纪律（F 盘约束）

- **所有构建缓存必须在 F 盘**：环境变量 `GRADLE_USER_HOME` 已指向 `F:\TEMP\.gradle`；任何场景不得让它落回 `C:\Users\...\.gradle`。
- **项目内运行产物一律放 `run/`**：本地试运行目录（runServer 数据目录、Gradle 专用 home、下载的 JDK 等）使用项目根的 `run/` 文件夹；`runServer`/`runServerFolia` 的运行目录也在这里（见 `build.gradle.kts` 的 `runDirectory` 配置）。
- **禁止操作项目文件夹之外的内容**（读写缓存目录 `F:\TEMP\.gradle` 属于构建工具自身行为，不受此限）。
- `run/`、`dist/`、`benchmark/classes`、`benchmark/lib`、`benchmark/results` 均已加入 `.gitignore`，不得提交生成物。

## 5. 基准测试规范（`benchmark/`）

- 位置：基准源码 `benchmark/src/`（由根项目 `benchmark` source set 编译，输出 `benchmark/classes`）。
- 协议：warmup ≥2.5s → 7 trials × ≥0.7s，取中位 ns/op；blackhole 防死码消除；先 self-check 后测量。
- 运行时依赖由 `./gradlew benchmarkRuntime` 拷贝到 `benchmark/lib/runtime/`；被测 jar 放 `benchmark/lib/<label>/plugin.jar`。
- 结果 JSON 落 `benchmark/results/<label>.json`；对比报告由 `benchmark/compare.py` 生成到 `note/report/perf/`。
- 新增优化点必须同步新增/扩展对应 bench，保证可量化。

## 6. 代码约定

- Java 21（根项目）；NMS 桥按各子项目 toolchain 编译。
- UTF-8；保留 `-Xlint:deprecation` 警告不新增。
- 优化代码必须保持原有行为顺序依赖（例如 Trie 子节点插入序决定匹配优先级、热源列表配置序决定判定序）。
