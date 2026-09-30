package dev.tako.papersdelight.mechanic.skewer;

final class HandheldSkewerUseGate {
    static final int PROXY_ACTIVATION_WAIT_TICKS = 8;

    enum Result {
        WAITING,
        ACTIVE,
        CANCEL
    }

    private final int waitTicks;
    private int remainingWaitTicks;
    private boolean activeUseObserved;

    HandheldSkewerUseGate() {
        this(PROXY_ACTIVATION_WAIT_TICKS);
    }

    HandheldSkewerUseGate(int waitTicks) {
        this.waitTicks = Math.max(0, waitTicks);
        reset();
    }

    Result tick(boolean activeUsing) {
        if (activeUsing) {
            activeUseObserved = true;
            return Result.ACTIVE;
        }
        if (activeUseObserved || remainingWaitTicks <= 0) return Result.CANCEL;
        remainingWaitTicks--;
        return Result.WAITING;
    }

    void reset() {
        remainingWaitTicks = waitTicks;
        activeUseObserved = false;
    }
}
