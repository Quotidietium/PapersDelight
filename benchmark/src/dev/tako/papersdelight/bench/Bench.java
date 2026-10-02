package dev.tako.papersdelight.bench;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimalist microbenchmark harness: no external dependencies.
 *
 * Protocol: fixed-iteration batch loop, timed with System.nanoTime.
 * - warmup phase: run batches continuously for at least WARMUP_MS, discard results
 * - measurement phase: TRIALS independent batches, each taking at least MIN_TRIAL_MS
 * - anti-dead-code-elimination: every batch folds results into a mutable long blackhole,
 *   whose final value is printed at the end (must actually be consumed)
 * - statistical indicators: median / minimum / maximum / coefficient of variation
 *
 * The baseline version and the optimized version of the same class use the same harness for measurement,
 * so the comparison reflects only code differences, not harness differences.
 */
public final class Bench {

    public static final long WARMUP_MS = 2500;
    public static final int TRIALS = 7;
    public static final long MIN_TRIAL_MS = 700;
    /** Number of ops per batch: large enough to smooth out timing granularity. */
    public static final int OPS_PER_BATCH = 20_000;

    /** Blackhole: folds results into a mutable long to prevent JIT dead-code elimination. */
    public static final class Blackhole {
        public long sink;
        public void consume(long v) { sink ^= v * 0x9E3779B97F4A7C15L; }
        public void consume(boolean v) { consume(v ? 1 : 0); }
        public void consume(Object v) { consume(System.identityHashCode(v)); }
    }

    /** One batch operation: executes OPS_PER_BATCH ops, folding each result into the blackhole. */
    public interface Op {
        void run(Blackhole bh, int ops);
    }

    public static final class Result {
        public final String name;
        public final double medianNsPerOp;
        public final double minNsPerOp;
        public final double maxNsPerOp;
        public final double opsPerMs;
        public final double cv;
        public final long blackhole;

        Result(String name, double[] nsPerOpTrials, long blackhole) {
            this.name = name;
            double[] sorted = nsPerOpTrials.clone();
            Arrays.sort(sorted);
            this.medianNsPerOp = percentile(sorted, 0.5);
            this.minNsPerOp = sorted[0];
            this.maxNsPerOp = sorted[sorted.length - 1];
            this.opsPerMs = 1_000_000.0 / medianNsPerOp;
            double mean = 0;
            for (double v : sorted) mean += v;
            mean /= sorted.length;
            double variance = 0;
            for (double v : sorted) variance += (v - mean) * (v - mean);
            variance /= sorted.length;
            this.cv = mean > 0 ? Math.sqrt(variance) / mean : 0;
            this.blackhole = blackhole;
        }

        private static double percentile(double[] sorted, double p) {
            int idx = (int) Math.ceil(p * sorted.length) - 1;
            if (idx < 0) idx = 0;
            if (idx >= sorted.length) idx = sorted.length - 1;
            return sorted[idx];
        }
    }

    public static Result measure(String name, Op op) {
        Blackhole bh = new Blackhole();

        long warmupStart = System.nanoTime();
        while (System.nanoTime() - warmupStart < WARMUP_MS * 1_000_000L) {
            op.run(bh, OPS_PER_BATCH);
        }

        List<double[]> trials = new ArrayList<>();
        // Grow batch size dynamically: make a single batch reach at least MIN_TRIAL_MS
        int ops = OPS_PER_BATCH;
        for (int t = 0; t < TRIALS; t++) {
            long start = System.nanoTime();
            op.run(bh, ops);
            long elapsed = System.nanoTime() - start;
            double nsPerOp = (double) elapsed / ops;
            trials.add(new double[]{nsPerOp});
            if (elapsed < MIN_TRIAL_MS * 1_000_000L && ops < 100_000_000) {
                ops = (int) Math.min(100_000_000L, ops * 4L);
            }
        }
        double[] flat = new double[trials.size()];
        for (int i = 0; i < flat.length; i++) flat[i] = trials.get(i)[0];
        return new Result(name, flat, bh.sink);
    }

    /** Correctness self-check: any failure immediately aborts, guarding the red line of "optimization must not change behavior". */
    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("SELF-CHECK FAILED: " + message);
        }
    }

    public static void report(PrintStream out, List<Result> results) {
        for (Result r : results) {
            out.printf("%-46s %12.1f ns/op  %14.1f ops/ms  cv=%.3f  bh=%d%n",
                    r.name, r.medianNsPerOp, r.opsPerMs, r.cv, r.blackhole);
        }
    }

    public static String toJson(List<Result> results) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n  \"timestamp\": ").append(System.currentTimeMillis()).append(",\n");
        sb.append("  \"java\": \"").append(escape(System.getProperty("java.version", "?"))).append("\",\n");
        sb.append("  \"vm\": \"").append(escape(System.getProperty("java.vm.name", "?"))).append(" ")
                .append(escape(System.getProperty("java.vm.version", ""))).append("\",\n");
        sb.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            sb.append("    {\"name\": \"").append(escape(r.name))
                    .append("\", \"nsPerOp\": ").append(String.format(java.util.Locale.ROOT, "%.3f", r.medianNsPerOp))
                    .append(", \"nsPerOpMin\": ").append(String.format(java.util.Locale.ROOT, "%.3f", r.minNsPerOp))
                    .append(", \"nsPerOpMax\": ").append(String.format(java.util.Locale.ROOT, "%.3f", r.maxNsPerOp))
                    .append(", \"opsPerMs\": ").append(String.format(java.util.Locale.ROOT, "%.3f", r.opsPerMs))
                    .append(", \"cv\": ").append(String.format(java.util.Locale.ROOT, "%.4f", r.cv))
                    .append(", \"blackhole\": ").append(r.blackhole)
                    .append("}");
            if (i < results.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Bench() {}
}
