#!/usr/bin/env bash
# 运行基准测试套件。用法:
#   bash benchmark/run-bench.sh <label> [group]
#     group ∈ {recipe, common, config, heat}；缺省则逐组各起独立 JVM 全量运行。
# 前置:
#   ./gradlew benchmarkClasses benchmarkRuntime
#   mkdir -p benchmark/lib/<label> && cp <plugin.jar> benchmark/lib/<label>/plugin.jar
# 输出:
#   benchmark/results/<label>/<group>.json（每组一个文件）
set -euo pipefail
cd "$(dirname "$0")/.."

LABEL="${1:?usage: run-bench.sh <label> [group]}"
GROUP="${2:-}"
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

run_group() {
  local g="$1"
  echo "=== group: ${g} ==="
  # 类路径顺序: 基准类 -> 被测插件 jar -> 运行时依赖
  # -XX:+UseSerialGC 降低 GC 噪声; 固定堆避免动态扩容干扰
  "$JAVA_BIN" -XX:+UseSerialGC -Xms512m -Xmx512m \
    -cp "$CP" dev.tako.papersdelight.bench.BenchSuite "$LABEL" "$g"
}

if [ -n "$GROUP" ]; then
  run_group "$GROUP"
else
  for g in recipe common config heat; do
    run_group "$g"
  done
fi
