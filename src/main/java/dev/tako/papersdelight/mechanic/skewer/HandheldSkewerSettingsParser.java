package dev.tako.papersdelight.mechanic.skewer;

import java.util.Map;

final class HandheldSkewerSettingsParser {
    private static final int DEFAULT_COOK_TICKS = 120;

    private HandheldSkewerSettingsParser() {
    }

    static HandheldSkewerBehavior.Settings parse(Map<String, ?> values) {
        return parse(requiredString(values, "cooking_proxy"), requiredString(values, "result"),
                optionalPositiveInt(values.get("cook_ticks"), DEFAULT_COOK_TICKS));
    }

    static HandheldSkewerBehavior.Settings parse(String cookingProxy, String result, int cookTicks) {
        return new HandheldSkewerBehavior.Settings(cookingProxy, result, cookTicks);
    }

    private static String requiredString(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be configured");
        }
        return text;
    }

    private static int optionalPositiveInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("cook_ticks must be a number");
        }
        return Math.max(1, number.intValue());
    }
}
