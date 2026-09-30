package dev.tako.papersdelight.jug;

final class JugFluidLevel {

    static final int CAPACITY = 16000;

    static final int BUCKET_VOLUME = 1000;

    static final int BOTTLE_VOLUME = 250;

    static final int PROGRESS_STAGES = 24;

    private JugFluidLevel() {
    }

    static int comparatorSignal(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) return 0;
        if (amount >= capacity) return 15;
        return (int) Math.floor((double) amount / capacity * 14.0) + 1;
    }

    static int buckets(int amount) {
        return amount <= 0 ? 0 : amount / BUCKET_VOLUME;
    }

    static int bottles(int amount) {
        return amount <= 0 ? 0 : amount / BOTTLE_VOLUME;
    }

    static int progressStage(int elapsed, int total) {
        if (total <= 0) return 0;
        if (elapsed <= 0) return 1;
        if (elapsed >= total) return PROGRESS_STAGES;
        int stage = (int) Math.ceil((double) elapsed * PROGRESS_STAGES / total);
        return Math.min(Math.max(stage, 1), PROGRESS_STAGES);
    }
}
