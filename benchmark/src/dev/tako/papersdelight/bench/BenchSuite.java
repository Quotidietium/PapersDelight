package dev.tako.papersdelight.bench;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark entry point.
 *
 * Run (Git Bash):  bash benchmark/run-bench.sh <label> [group]
 *   group ∈ {recipe, common, config, heat, all}，默认由 run-bench.sh 逐组各起一个独立 JVM
 *   （组间 JIT 上下文隔离，避免跨组代码形状干扰计时）。
 * 结果写入 benchmark/results/<label>/<group>.json
 *
 * 每组先做正确性自检（任何行为改变 → AssertionError 中止，不产生结果文件），再测量。
 */
public final class BenchSuite {

    public static void main(String[] args) throws Exception {
        String label = args.length > 0 ? args[0] : "run";
        String group = args.length > 1 ? args[1] : "all";
        List<Bench.Result> results = new ArrayList<>();

        if ("all".equals(group)) {
            dev.tako.papersdelight.recipe.RecipeTrieBench.run(results);
            dev.tako.papersdelight.recipe.MatcherCostBench.run(results);
            dev.tako.papersdelight.recipe.TagExpanderBench.run(results);
            dev.tako.papersdelight.common.TickBatchBench.run(results);
            dev.tako.papersdelight.config.ConfigGetOrBench.run(results);
            dev.tako.papersdelight.heat.HeatSourceBench.run(results);
            dev.tako.papersdelight.cookingpot.PotTickBench.run(results);
            dev.tako.papersdelight.util.TextParseBench.run(results);
        } else {
            switch (group) {
                case "recipe" -> {
                    dev.tako.papersdelight.recipe.RecipeTrieBench.run(results);
                    dev.tako.papersdelight.recipe.MatcherCostBench.run(results);
                    dev.tako.papersdelight.recipe.TagExpanderBench.run(results);
                }
                case "common" -> dev.tako.papersdelight.common.TickBatchBench.run(results);
                case "config" -> dev.tako.papersdelight.config.ConfigGetOrBench.run(results);
                case "heat" -> dev.tako.papersdelight.heat.HeatSourceBench.run(results);
                case "container" -> dev.tako.papersdelight.cookingpot.PotTickBench.run(results);
                case "text" -> dev.tako.papersdelight.util.TextParseBench.run(results);
                default -> throw new IllegalArgumentException("unknown group: " + group);
            }
        }

        Bench.report(System.out, results);

        Path outDir = Paths.get("benchmark", "results", label);
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve(group + ".json");
        Files.write(outFile, (Bench.toJson(results) + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("results -> " + outFile.toAbsolutePath());
    }

    private BenchSuite() {}
}
