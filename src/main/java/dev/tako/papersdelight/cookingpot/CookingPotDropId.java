package dev.tako.papersdelight.cookingpot;

final class CookingPotDropId {

    private CookingPotDropId() {
    }

    static String resolve(String blockId, String fallback) {
        String resolved = trimToNull(blockId);
        return resolved != null ? resolved : trimToNull(fallback);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
