package dev.tako.papersdelight.util;

import org.bukkit.World;

public final class WorldLookup {

    private WorldLookup() {
    }

    public static World worldOf(Object holder) {
        Object value = ReflectionHandles.callNoArg(holder, "getWorld");
        return value instanceof World world ? world : null;
    }
}
