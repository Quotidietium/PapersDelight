package dev.tako.papersdelight.mechanic.skillet;

import dev.tako.papersdelight.config.ConfigManager;

public final class SkilletCookingTime {
    private static final int MINIMUM_COOKING_TIME = 60;

    private SkilletCookingTime() {
    }

    public static int calculate(int originalCookingTime, int fireAspectLevel) {
        return calculate(originalCookingTime, fireAspectLevel,
                ConfigManager.getInt("skillet.cooking.default_cook_time", 600));
    }

    static int calculate(int originalCookingTime, int fireAspectLevel, int defaultCookingTime) {
        if (originalCookingTime <= 0) {
            originalCookingTime = defaultCookingTime;
        }
        float reduction = 0.2F - Math.max(0, fireAspectLevel) * 0.05F;
        int result = (int) ((originalCookingTime / 20) * reduction) * 20;
        return Math.max(MINIMUM_COOKING_TIME, Math.min(result, originalCookingTime));
    }
}
