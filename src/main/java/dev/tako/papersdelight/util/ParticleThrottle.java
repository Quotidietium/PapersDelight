package dev.tako.papersdelight.util;

import java.util.concurrent.ThreadLocalRandom;

public final class ParticleThrottle {

    private ParticleThrottle() {}

    public static double retentionRate(int nearby, int threshold, double minimumRate) {
        if (nearby <= threshold) return 1.0;
        return Math.max(minimumRate, (double) threshold / nearby);
    }

    public static boolean shouldSkip(int nearby, int threshold, double minimumRate) {
        double rate = retentionRate(nearby, threshold, minimumRate);
        return ThreadLocalRandom.current().nextDouble() > rate;
    }
}
