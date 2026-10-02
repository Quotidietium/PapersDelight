package dev.tako.papersdelight.common;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;

import java.util.List;

/**
 * TickBatch.due pure-function benchmark (container tick batching pass counting).
 * This method is called once per tick by every container manager and is the basis of all batch processing.
 */
public final class TickBatchBench {

    public static void run(List<Result> results) {
        selfCheck();

        results.add(Bench.measure("tickBatch.due[interval=1,typical]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TickBatch.due(1_000, 1_000 + (i & 15), 1));
            }
        }));
        results.add(Bench.measure("tickBatch.due[interval=4,mixed]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                int elapsed = i % 5;            // 0..4: covers not-due / just-due / one tick over
                bh.consume(TickBatch.due(2_000, 2_000 + elapsed, 4));
            }
        }));
        results.add(Bench.measure("tickBatch.due[interval=8,replay-cap]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TickBatch.due(3_000, 3_000 + 1_000 + i, 8));  // elapsed>64: capped at 64
            }
        }));
    }

    private static void selfCheck() {
        Bench.check(TickBatch.due(0, 12345, 4) == 1, "first pass returns 1");
        Bench.check(TickBatch.due(100, 103, 4) == 0, "not due returns 0");
        Bench.check(TickBatch.due(100, 104, 4) == 4, "due returns interval");
        Bench.check(TickBatch.due(100, 101, 1) == 1, "interval=1 when due");
        Bench.check(TickBatch.due(100, 200, 8) == 64, "replay capped at interval*8");
        Bench.check(TickBatch.due(100, 132, 8) == 32, "replay exact elapsed");
    }

    private TickBatchBench() {}
}
