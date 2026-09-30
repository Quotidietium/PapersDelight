package dev.tako.papersdelight.config;

public final class StoveConfig {
    public final float displayScale;
    public final float displayPitch;
    public final double displayBaseY;
    public final double itemSmokeChance;
    public final int itemSmokeCount;
    public final ConfigManager.SoundConfig soundPlaceFood;
    public final long particleIntervalTicks;
    public final double particleViewDistance;
    public final ParticleThrottleConfig particleThrottle;
    public final ParticleThrottleConfig ambientSoundThrottle;
    public final double ambientSoundViewDistance;
    public final AmbientSound ambientSound;

    private StoveConfig(
            float displayScale,
            float displayPitch,
            double displayBaseY,
            double itemSmokeChance,
            int itemSmokeCount,
            ConfigManager.SoundConfig soundPlaceFood,
            long particleIntervalTicks,
            double particleViewDistance,
            ParticleThrottleConfig particleThrottle,
            ParticleThrottleConfig ambientSoundThrottle,
            double ambientSoundViewDistance,
            AmbientSound ambientSound) {
        this.displayScale = displayScale;
        this.displayPitch = displayPitch;
        this.displayBaseY = displayBaseY;
        this.itemSmokeChance = itemSmokeChance;
        this.itemSmokeCount = itemSmokeCount;
        this.soundPlaceFood = soundPlaceFood;
        this.particleIntervalTicks = particleIntervalTicks;
        this.particleViewDistance = particleViewDistance;
        this.particleThrottle = particleThrottle;
        this.ambientSoundThrottle = ambientSoundThrottle;
        this.ambientSoundViewDistance = ambientSoundViewDistance;
        this.ambientSound = ambientSound;
    }

    public static StoveConfig load(AmbientSound ambientSound) {
        return new StoveConfig(
                (float) ConfigManager.getDouble("stove.display.scale", 0.375D),
                (float) ConfigManager.getDouble("stove.display.rotation_pitch", 90.0D),
                ConfigManager.getDouble("stove.display.translate_y", 1.01D),
                Math.max(0.0D, Math.min(1.0D,
                        ConfigManager.getDouble("stove.particles.item_smoke_chance", 0.2D))),
                Math.max(0, ConfigManager.getInt("stove.particles.item_smoke_count", 1)),
                ConfigManager.readSoundConfig("stove.sounds.place_food",
                        "minecraft:block.lantern.place", 0.5F, 1.0F),
                Math.max(1L, ConfigManager.getInt("stove.particles.interval_ticks", 8)),
                Math.max(0.0D, ConfigManager.getDouble("stove.particles.view_distance_blocks", 24.0D)),
                ParticleThrottleConfig.load(
                        "particle_throttle.stove_threshold", 8,
                        "particle_throttle.stove_max_rate", 0.1D),
                ParticleThrottleConfig.load(
                        "particle_throttle.ambient_sound_threshold", 4,
                        "particle_throttle.ambient_sound_max_rate", 0.001D, 0.001D),
                Math.max(0.0D, ConfigManager.getDouble("stove.ambient_sound.view_distance_blocks", 16.0D)),
                ambientSound
        );
    }

    public StoveConfig withAmbientSound(AmbientSound ambientSound) {
        return new StoveConfig(
                displayScale, displayPitch, displayBaseY,
                itemSmokeChance, itemSmokeCount, soundPlaceFood,
                particleIntervalTicks, particleViewDistance,
                particleThrottle, ambientSoundThrottle, ambientSoundViewDistance,
                ambientSound
        );
    }

    public record AmbientSound(
            String soundKey,
            float intervalMin,
            float intervalMax,
            float volumeMin,
            float volumeMax,
            float pitchMin,
            float pitchMax) {
        public static AmbientSound defaults() {
            return new AmbientSound(
                    "farmersdelight:block.stove.crackle",
                    60.0F, 100.0F,
                    1.0F, 1.0F,
                    0.9F, 1.1F
            );
        }
    }
}
