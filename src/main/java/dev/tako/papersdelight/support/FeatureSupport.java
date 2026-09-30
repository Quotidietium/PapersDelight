package dev.tako.papersdelight.support;

public final class FeatureSupport {

    private static final boolean EXTENDED = false;
    private static final boolean TryUnlockAllFeatures = false;

    private FeatureSupport() {
    }

    public static boolean tryUnlockAllFeatures() {
        return TryUnlockAllFeatures;
    }

    public static boolean handheldSkillet() {
        return EXTENDED;
    }

    public static boolean handheldSkewer() {
        return EXTENDED;
    }

    public static boolean villagerTrade() {
        return EXTENDED;
    }

    public static boolean villagerHarvest() {
        return EXTENDED;
    }

    public static boolean villagerBreed() {
        return EXTENDED;
    }

    public static boolean villagerPickup() {
        return EXTENDED;
    }

    public static boolean petFood() {
        return EXTENDED;
    }

    public static boolean recipeBrowser() {
        return EXTENDED;
    }

    public static boolean cookingPotRecipeControls() {
        return EXTENDED;
    }

    public static boolean customRecipes() {
        return EXTENDED;
    }
}
