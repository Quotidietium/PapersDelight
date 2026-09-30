package dev.tako.papersdelight.common;

import dev.tako.papersdelight.config.ConfigManager;

public final class TickBatch {

    private TickBatch() {
    }

    public static int interval() {
        return Math.max(1, ConfigManager.getInt("container.tick_interval_ticks", 1));
    }

    public static int due(int lastPassTick, int now, int interval) {
        if (lastPassTick == 0) return 1;
        int elapsed = now - lastPassTick;
        if (elapsed < interval) return 0;
        return interval == 1 ? 1 : Math.min(elapsed, interval * 8);
    }
}
