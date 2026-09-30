package dev.tako.papersdelight.mechanic.skewer;

final class HandheldSkewerProgressBar {
    static final int BAR_MAX_DAMAGE = 128;

    private HandheldSkewerProgressBar() {
    }

    static int damageFor(int maxDamage, int elapsedTicks, int totalTicks) {
        if (maxDamage <= 0 || totalTicks <= 0) return 0;
        if (elapsedTicks <= 0) return maxDamage;
        if (elapsedTicks >= totalTicks) return 0;
        return Math.max(0, maxDamage - (int) ((long) maxDamage * elapsedTicks / totalTicks));
    }
}
