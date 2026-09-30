package dev.tako.papersdelight.mechanic.skewer;

record HandheldSkewerStackState(int originalAmount, int remainingRawAmount) {

    HandheldSkewerStackState {
        if (originalAmount < 1 || remainingRawAmount < 0 || remainingRawAmount > originalAmount) {
            throw new IllegalArgumentException("invalid handheld skewer stack counts");
        }
    }

    static HandheldSkewerStackState start(int sourceAmount) {
        return new HandheldSkewerStackState(sourceAmount, sourceAmount);
    }

    HandheldSkewerStackState consumeOne() {
        if (!hasRaw()) throw new IllegalStateException("no raw skewers left to cook");
        return new HandheldSkewerStackState(originalAmount, remainingRawAmount - 1);
    }

    int deliveredAmount() {
        return originalAmount - remainingRawAmount;
    }

    boolean hasRaw() {
        return remainingRawAmount > 0;
    }

}
