package dev.tako.papersdelight.mechanic.nourishment;

final class NourishmentHungerMath {

    static final int FULL_FOOD_LEVEL = 20;

    static final int FAKED_FOOD_LEVEL = 19;

    private NourishmentHungerMath() {
    }

    static boolean shouldFakeHunger(boolean holdingFood, int foodLevel, boolean alreadyFaked) {
        return holdingFood && !alreadyFaked && foodLevel >= FULL_FOOD_LEVEL;
    }

    static boolean shouldRestoreHunger(boolean holdingFood, boolean alreadyFaked) {
        return alreadyFaked && !holdingFood;
    }
}
