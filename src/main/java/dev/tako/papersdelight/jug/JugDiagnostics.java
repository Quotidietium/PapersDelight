package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.util.ReflectionHandles;
import dev.tako.papersdelight.recipe.RecipeManager;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public final class JugDiagnostics {

    private static final String FLUID_REGISTRY = "dev.tako.libuid.api.FluidRegistry";

    private JugDiagnostics() {
    }

    public static JugDiagnosticsReport collect(
            boolean libuidAvailable,
            boolean runtimeInstalled,
            @Nullable RecipeManager.JugRecipes recipes,
            @Nullable List<String> fluidKeys,
            @Nullable Predicate<String> itemExists
    ) {
        Predicate<String> exists = itemExists == null ? id -> false : itemExists;
        List<String> keys = sanitizeKeys(fluidKeys);

        List<JugDiagnosticsReport.FluidEntry> entries = new ArrayList<>(keys.size());
        for (String key : keys) {
            entries.add(new JugDiagnosticsReport.FluidEntry(key,
                    JugFluidItemReport.resolveDisplayItem(key, exists)));
        }

        return new JugDiagnosticsReport(
                libuidAvailable,
                runtimeInstalled,
                recipes == null ? 0 : recipes.filling().size(),
                recipes == null ? 0 : recipes.emptying().size(),
                recipes == null ? 0 : recipes.soaking().size(),
                entries,
                JugFluidItemReport.missingFluids(keys, exists));
    }

    public static List<String> fluidKeys(@Nullable Plugin plugin) {
        if (plugin == null) return List.of();
        ClassLoader loader = plugin.getClass().getClassLoader();
        if (!JugSupport.isClassVisible(FLUID_REGISTRY, loader)) return List.of();
        try {
            Object all = ReflectionHandles.callStaticNoArg(Class.forName(FLUID_REGISTRY, true, loader), "all");
            if (!(all instanceof Collection<?> types)) return List.of();
            List<String> keys = new ArrayList<>(types.size());
            for (Object type : types) {
                String key = keyOf(type);
                if (key != null) keys.add(key);
            }
            return List.copyOf(keys);
        } catch (Throwable ignored) {

            return List.of();
        }
    }

    @Nullable
    private static String keyOf(@Nullable Object fluidType) {
        if (fluidType == null) return null;
        try {
            Object key = ReflectionHandles.callNoArg(fluidType, "key");
            if (key == null) return null;
            String text = key.toString();
            return text.isBlank() ? null : text;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> sanitizeKeys(@Nullable List<String> fluidKeys) {
        if (fluidKeys == null || fluidKeys.isEmpty()) return List.of();
        List<String> keys = new ArrayList<>(fluidKeys.size());
        for (String key : fluidKeys) {
            if (key != null && !key.isBlank()) keys.add(key);
        }
        return keys;
    }
}
