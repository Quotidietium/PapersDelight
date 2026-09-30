package dev.tako.papersdelight.mechanic.skewer;

final class HandheldSkewerProgress {
    enum Result {
        CONTINUE,
        COMPLETE,
        CANCEL
    }

    private final int totalTicks;
    private int elapsedTicks;

    HandheldSkewerProgress(int totalTicks) {
        this.totalTicks = Math.max(1, totalTicks);
    }

    Result tick(boolean stillUsing) {
        if (!stillUsing) return Result.CANCEL;
        if (elapsedTicks < totalTicks) elapsedTicks++;
        return isComplete() ? Result.COMPLETE : Result.CONTINUE;
    }

    boolean isComplete() {
        return elapsedTicks >= totalTicks;
    }

    int totalTicks() {
        return totalTicks;
    }

    int elapsedTicks() {
        return elapsedTicks;
    }

    void reset() {
        elapsedTicks = 0;
    }
}
