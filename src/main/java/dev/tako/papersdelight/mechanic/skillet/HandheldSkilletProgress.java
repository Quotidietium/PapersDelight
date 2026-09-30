package dev.tako.papersdelight.mechanic.skillet;

final class HandheldSkilletProgress {
    enum Result {
        CONTINUE,
        COMPLETE,
        CANCEL
    }

    private final int totalTicks;
    private int elapsedTicks;

    HandheldSkilletProgress(int totalTicks) {
        this.totalTicks = Math.max(1, totalTicks);
    }

    Result tick(boolean stillUsing) {
        if (!stillUsing) return Result.CANCEL;
        elapsedTicks++;
        return elapsedTicks >= totalTicks ? Result.COMPLETE : Result.CONTINUE;
    }

    int totalTicks() {
        return totalTicks;
    }

    int elapsedTicks() {
        return elapsedTicks;
    }
}
