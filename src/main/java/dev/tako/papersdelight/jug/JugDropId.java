package dev.tako.papersdelight.jug;

import org.jetbrains.annotations.Nullable;

final class JugDropId {

    static final String FALLBACK_JUG_ID = "farmersdelight:jug";

    private JugDropId() {
    }

    @Nullable
    static String resolve(@Nullable String blockId, @Nullable String fallback) {
        String resolved = trimToNull(blockId);
        return resolved != null ? resolved : trimToNull(fallback);
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
