package dev.tako.papersdelight.mechanic.function;

final class UpgradeEffectMath {

    private UpgradeEffectMath() {
        throw new UnsupportedOperationException("UpgradeEffectMath is a utility class");
    }

    static int upgradedAmplifier(int current, int increment, int maxAmplifier) {
        return Math.min(current + increment, maxAmplifier);
    }

    static int mergedDuration(int currentDuration, int configDuration) {
        return currentDuration == -1 ? -1 : Math.max(currentDuration, configDuration);
    }
}
