package dev.tako.papersdelight.jug;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class JugSupport {

    private static final String PROBE_CLASS = "dev.tako.libuid.api.FluidRegistry";
    private static final String PLUGIN_NAME = "Libuid";

    private JugSupport() {
    }

    public static boolean isAvailable(@Nullable Plugin plugin) {
        return plugin != null && isAvailable(plugin, plugin.getClass().getClassLoader());
    }

    public static boolean isAvailable(@Nullable Plugin plugin, @Nullable ClassLoader loader) {
        if (plugin == null) return false;
        try {
            Plugin libuid = plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
            return libuid != null && libuid.isEnabled() && isClassVisible(loader);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPresentAndVisible(@Nullable Plugin plugin) {
        return plugin != null && isPresentAndVisible(plugin, plugin.getClass().getClassLoader());
    }

    public static boolean isPresentAndVisible(@Nullable Plugin plugin, @Nullable ClassLoader loader) {
        if (plugin == null) return false;
        try {
            return plugin.getServer().getPluginManager().getPlugin(PLUGIN_NAME) != null
                    && isClassVisible(loader);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isClassVisible(@Nullable ClassLoader loader) {
        return isClassVisible(PROBE_CLASS, loader);
    }

    public static boolean isClassVisible(@Nullable String className, @Nullable ClassLoader loader) {
        if (className == null || loader == null) return false;
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
