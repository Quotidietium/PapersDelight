package dev.tako.papersdelight.recipe;

import org.bukkit.Bukkit;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class CampfireRecipeUtil {

    private static final Map<String, CampfireRecipe> recipeCache = new ConcurrentHashMap<>();
    private static final Set<String> notCampfireIngredient = ConcurrentHashMap.newKeySet();

    private CampfireRecipeUtil() {}

    public static int getCookingTime(ItemStack input, int fallbackTicks) {
        if (input == null || input.isEmpty()) return fallbackTicks;
        CampfireRecipe recipe = findRecipe(input);
        return recipe != null ? recipe.getCookingTime() : fallbackTicks;
    }

    public static ItemStack getResult(ItemStack input) {
        if (input == null || input.isEmpty()) return null;
        CampfireRecipe recipe = findRecipe(input);
        return recipe != null ? recipe.getResult() : null;
    }

    public static boolean isIngredient(ItemStack input) {
        if (input == null || input.isEmpty()) return false;
        return findRecipe(input) != null;
    }

    public static CampfireRecipe findRecipe(ItemStack input) {
        if (input == null || input.isEmpty()) return null;
        String key = input.getType().name();

        CampfireRecipe cached = recipeCache.get(key);
        if (cached != null && cached.getInputChoice().test(input)) return cached;

        if (notCampfireIngredient.contains(key)) return null;

        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe r = iter.next();
            if (r instanceof CampfireRecipe cr) {
                if (cr.getInputChoice().test(input)) {
                    recipeCache.put(key, cr);
                    return cr;
                }
            }
        }
        notCampfireIngredient.add(key);
        return null;
    }

    public static void clearCache() {
        recipeCache.clear();
        notCampfireIngredient.clear();
    }
}
