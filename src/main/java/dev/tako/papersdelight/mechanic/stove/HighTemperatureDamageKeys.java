package dev.tako.papersdelight.mechanic.stove;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class HighTemperatureDamageKeys {

    static final Key DEFAULT_FALLBACK = Key.key("minecraft", "on_fire");

    private HighTemperatureDamageKeys() {
        throw new UnsupportedOperationException("HighTemperatureDamageKeys is a utility class");
    }

    @Nullable
    static Key customDamageKey(String damageType) {
        if (damageType == null || damageType.isBlank()) return null;
        Key key;
        try {
            key = Key.key(damageType);
        } catch (RuntimeException invalid) {
            return null;
        }
        return Key.MINECRAFT_NAMESPACE.equals(key.namespace()) ? null : key;
    }

    @NotNull
    static Key parseFallbackKey(String fallbackType) {
        if (fallbackType == null || fallbackType.isBlank()) return DEFAULT_FALLBACK;
        try {
            return Key.key(fallbackType);
        } catch (RuntimeException invalid) {
            return DEFAULT_FALLBACK;
        }
    }
}
