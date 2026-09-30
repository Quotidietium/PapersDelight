package dev.tako.papersdelight.client;

import java.util.List;

public final class StoveBurnDamageTypes {

    public static final String STOVE_BURN_KEY = "farmersdelight:stove_burn";

    public static final String STOVE_BURN_MESSAGE_ID = "farmersdelight.stove";

    public static final float STOVE_BURN_EXHAUSTION = 0.1f;

    public static final String STOVE_BURN_FALLBACK_KEY = "minecraft:on_fire";

    public static final List<String> STOVE_BURN_TAG_KEYS = List.of(
            "minecraft:is_fire",
            "minecraft:no_knockback",
            "minecraft:burn_from_stepping",
            "minecraft:panic_environmental_causes");

    private StoveBurnDamageTypes() {
        throw new UnsupportedOperationException("StoveBurnDamageTypes is a constants holder");
    }
}
