package dev.tako.papersdelight.effect;

import net.kyori.adventure.bossbar.BossBar;


public record TimedEffectSession(BossBar bossBar, int endTick, int totalDurationTicks, int amplifier) {
}
