package dev.tako.papersdelight.jug;

final class JugCapacityDisplay {
    private static final int MAX_STACK_SIZE = 99;

    private JugCapacityDisplay() {
    }

    static int buckets(int capacity) {
        return Math.min(MAX_STACK_SIZE, Math.max(0, capacity) / 1_000);
    }

    static int bottles(int capacity) {
        return Math.min(MAX_STACK_SIZE, Math.max(0, capacity) / 250);
    }

    static int itemAmount(int displayedAmount) {
        return Math.max(1, displayedAmount);
    }
}
