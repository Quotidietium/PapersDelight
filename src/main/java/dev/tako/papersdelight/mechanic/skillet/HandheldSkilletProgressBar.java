package dev.tako.papersdelight.mechanic.skillet;

final class HandheldSkilletProgressBar {
    static final int STAGES = 13;

    private HandheldSkilletProgressBar() {
    }

    static int stageFor(int elapsedTicks, int totalTicks) {
        if (totalTicks <= 0 || elapsedTicks <= 0) return 0;
        if (elapsedTicks >= totalTicks) return STAGES;
        return (int) Math.min(STAGES, ((long) elapsedTicks * STAGES) / totalTicks);
    }

    static int damageFor(int maxDamage, int stage) {
        if (maxDamage <= 0 || stage < 0 || stage > STAGES) return 0;
        return (int) (maxDamage - ((long) maxDamage * stage) / STAGES);
    }
}
