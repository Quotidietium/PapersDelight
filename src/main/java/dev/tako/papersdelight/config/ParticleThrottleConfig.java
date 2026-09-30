package dev.tako.papersdelight.config;

public final class ParticleThrottleConfig {
    public final int threshold;
    public final double maxRate;

    private ParticleThrottleConfig(int threshold, double maxRate) {
        this.threshold = threshold;
        this.maxRate = maxRate;
    }

    public static ParticleThrottleConfig load(
            String thresholdPath, int defaultThreshold,
            String maxRatePath, double defaultMaxRate) {
        return load(thresholdPath, defaultThreshold, maxRatePath, defaultMaxRate, 0.01D);
    }

    public static ParticleThrottleConfig load(
            String thresholdPath, int defaultThreshold,
            String maxRatePath, double defaultMaxRate,
            double minimumMaxRate) {
        return new ParticleThrottleConfig(
                Math.max(1, ConfigManager.getInt(thresholdPath, defaultThreshold)),
                Math.max(minimumMaxRate,
                        Math.min(1.0D, ConfigManager.getDouble(maxRatePath, defaultMaxRate)))
        );
    }

}
