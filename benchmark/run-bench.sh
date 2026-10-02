#!/usr/bin/env bash
# 运行基准测试套件。用法:
#   bash benchmark/run-bench.sh <label>
# 前置:
#   ./gradlew benchmarkClasses benchmarkRuntime          # 编译基准 + 拷贝运行时依赖
#   mkdir -p benchmark/lib/<label>                       # 放入被测插件 jar 并命名为 plugin.jar
# 输出:
#   benchmark/results/<label>.json
set -euo pipefail
cd "$(dirname "$0")/.."

LABEL="${1:?usage: run-bench.sh <label> (jar at benchmark/lib/<label>/plugin.jar)}"
JAVA_BIN="${JAVA_BIN:-/f/Java/21/bin/java.exe}"
[ -x "$JAVA_BIN" ] || JAVA_BIN="$(command -v java)"

PLUGIN_JAR="benchmark/lib/${LABEL}/plugin.jar"
if [ ! -f "$PLUGIN_JAR" ]; then
  echo "missing $PLUGIN_JAR" >&2
  exit 2
fi
# benchmark source set 的类输出目录（Gradle 默认布局）
BENCH_CLASSES="build/classes/java/benchmark"
if [ ! -d "$BENCH_CLASSES" ]; then
  echo "run ./gradlew benchmarkClasses first" >&2
  exit 2
fi

CP="${BENCH_CLASSES};${PLUGIN_JAR}"
for jar in benchmark/lib/runtime/*.jar; do
  CP="${CP};${jar}"
done

# 类路径顺序: 基准类 -> 被测插件 jar -> 运行时依赖
# -XX:+UseSerialGC 降低 GC 噪声; 固定堆避免动态扩容干扰
exec "$JAVA_BIN" -XX:+UseSerialGC -Xms512m -Xmx512m \
  -cp "$CP" dev.tako.papersdelight.bench.BenchSuite "$LABEL"
