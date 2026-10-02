package dev.tako.papersdelight.bench;

import java.io.IOException;
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
 * Run (Git Bash):
 *   java -cp "benchmark/classes;benchmark/lib/runtime/*;benchmark/lib/<label>/plugin.jar" \
 *        dev.tako.papersdelight.bench.BenchSuite <label>
 *
 * <label> is used for the results filename: benchmark/results/<label>.json
 * Each bench first performs a correctness self-check (AssertionError aborts if any behavior changed),
 * then performs performance measurement. The blackhole value is printed at the end to prevent dead-code elimination.
 */
public final class BenchSuite {

    public static void main(String[] args) throws IOException {
        String label = args.length > 0 ? args[0] : "run";
        List<Bench.Result> results = new ArrayList<>();

        // ---- Register benchmarks (sorted by module) ----
        dev.tako.papersdelight.recipe.RecipeTrieBench.run(results);
        dev.tako.papersdelight.recipe.MatcherCostBench.run(results);
        dev.tako.papersdelight.recipe.TagExpanderBench.run(results);
        dev.tako.papersdelight.common.TickBatchBench.run(results);

        Bench.report(System.out, results);

        Path outDir = Paths.get("benchmark", "results");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve(label + ".json");
        String json = Bench.toJson(results);
        Files.write(outFile, (json + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("results -> " + outFile.toAbsolutePath());
    }

    private BenchSuite() {}
}
