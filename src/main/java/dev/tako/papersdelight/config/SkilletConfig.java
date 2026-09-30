package dev.tako.papersdelight.config;

public final class SkilletConfig {
    public final float displayScale;
    public final float displayPitch;
    public final double displayTranslateY;
    public final double stackYOffset;
    public final double stackXzOffset;
    public final ConfigManager.SoundConfig soundAddFood;
    public final ConfigManager.SoundConfig soundAddFoodCold;
    public final ConfigManager.SoundConfig soundSizzle;
    public final double fireAspectXzSpread;
    public final double fireAspectVelocityYBase;
    public final double fireAspectVelocityYExtra;
    public final double fireAspectVelocityXz;
    public final double fireAspectOriginX;
    public final double fireAspectOriginY;
    public final double fireAspectOriginZ;
    public final long particleIntervalTicks;
    public final double particleViewDistance;
    public final ParticleThrottleConfig soundThrottle;

    private SkilletConfig(
            float displayScale,
            float displayPitch,
            double displayTranslateY,
            double stackYOffset,
            double stackXzOffset,
            ConfigManager.SoundConfig soundAddFood,
            ConfigManager.SoundConfig soundAddFoodCold,
            ConfigManager.SoundConfig soundSizzle,
            double fireAspectXzSpread,
            double fireAspectVelocityYBase,
            double fireAspectVelocityYExtra,
            double fireAspectVelocityXz,
            double fireAspectOriginX,
            double fireAspectOriginY,
            double fireAspectOriginZ,
            long particleIntervalTicks,
            double particleViewDistance,
            ParticleThrottleConfig soundThrottle) {
        this.displayScale = displayScale;
        this.displayPitch = displayPitch;
        this.displayTranslateY = displayTranslateY;
        this.stackYOffset = stackYOffset;
        this.stackXzOffset = stackXzOffset;
        this.soundAddFood = soundAddFood;
        this.soundAddFoodCold = soundAddFoodCold;
        this.soundSizzle = soundSizzle;
        this.fireAspectXzSpread = fireAspectXzSpread;
        this.fireAspectVelocityYBase = fireAspectVelocityYBase;
        this.fireAspectVelocityYExtra = fireAspectVelocityYExtra;
        this.fireAspectVelocityXz = fireAspectVelocityXz;
        this.fireAspectOriginX = fireAspectOriginX;
        this.fireAspectOriginY = fireAspectOriginY;
        this.fireAspectOriginZ = fireAspectOriginZ;
        this.particleIntervalTicks = particleIntervalTicks;
        this.particleViewDistance = particleViewDistance;
        this.soundThrottle = soundThrottle;
    }

    public static SkilletConfig load() {
        return new SkilletConfig(
                (float) ConfigManager.getDouble("skillet.display.scale", 0.5D),
                (float) ConfigManager.getDouble("skillet.display.rotation_pitch", 90.0D),
                ConfigManager.getDouble("skillet.display.translate_y", 0.1D),
                ConfigManager.getDouble("skillet.display.stack_y_offset", 0.03D),
                ConfigManager.getDouble("skillet.display.stack_xz_offset", 0.06D),
                ConfigManager.readSoundConfig("skillet.sounds.add_food",
                        "farmersdelight:block.skillet.add_food", 0.8F, 1.0F),
                ConfigManager.readSoundConfig("skillet.sounds.add_food_cold",
                        "minecraft:block.lantern.place", 0.7F, 1.0F),
                ConfigManager.readSoundConfig("skillet.sounds.sizzle",
                        "farmersdelight:block.skillet.sizzle", 0.4F, 0.9F, 1.1F),
                ConfigManager.getDouble("skillet.fire_aspect_particle.xz_spread", 0.6D),
                ConfigManager.getDouble("skillet.fire_aspect_particle.velocity_y_base", 0.3D),
                ConfigManager.getDouble("skillet.fire_aspect_particle.velocity_y_extra", 0.8D),
                ConfigManager.getDouble("skillet.fire_aspect_particle.velocity_xz", 0.5D),
                ConfigManager.getDouble("skillet.fire_aspect_particle.origin_x", 0.5D),
                ConfigManager.getDouble("skillet.fire_aspect_particle.origin_y", 0.1D),
                ConfigManager.getDouble("skillet.fire_aspect_particle.origin_z", 0.5D),
                Math.max(1L, ConfigManager.getInt("skillet.particles.interval_ticks", 8)),
                Math.max(0.0D, ConfigManager.getDouble("skillet.particles.view_distance_blocks", 24.0D)),
                ParticleThrottleConfig.load(
                        "particle_throttle.ambient_sound_threshold", 4,
                        "particle_throttle.ambient_sound_max_rate", 0.001D)
        );
    }
}
