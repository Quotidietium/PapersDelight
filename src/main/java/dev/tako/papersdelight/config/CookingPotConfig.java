package dev.tako.papersdelight.config;

public final class CookingPotConfig {
    public final long particleIntervalTicks;
    public final double particleViewDistance;
    public final ParticleThrottleConfig particleThrottle;
    public final ParticleThrottleConfig soundThrottle;

    private CookingPotConfig(
            long particleIntervalTicks,
            double particleViewDistance,
            ParticleThrottleConfig particleThrottle,
            ParticleThrottleConfig soundThrottle) {
        this.particleIntervalTicks = particleIntervalTicks;
        this.particleViewDistance = particleViewDistance;
        this.particleThrottle = particleThrottle;
        this.soundThrottle = soundThrottle;
    }

    public static CookingPotConfig load() {
        return new CookingPotConfig(
                Math.max(1L, ConfigManager.getInt("cooking_pot.particles.interval_ticks", 8)),
                Math.max(0.0D, ConfigManager.getDouble("cooking_pot.particles.view_distance_blocks", 24.0D)),
                ParticleThrottleConfig.load(
                        "particle_throttle.cooking_pot_threshold", 4,
                        "particle_throttle.cooking_pot_max_rate", 0.05D),
                ParticleThrottleConfig.load(
                        "particle_throttle.ambient_sound_threshold", 4,
                        "particle_throttle.ambient_sound_max_rate", 0.001D)
        );
    }
}
