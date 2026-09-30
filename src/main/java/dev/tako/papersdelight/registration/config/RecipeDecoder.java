package dev.tako.papersdelight.registration.config;

import dev.tako.papersdelight.config.ConfigManager.SoundConfig;
import dev.tako.papersdelight.jug.JugRecipeDecoderBridge;
import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import dev.tako.papersdelight.mechanic.cutting.CuttingRecipe;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.recipe.CustomRecipe;
import dev.tako.papersdelight.recipe.IngredientDef;
import dev.tako.papersdelight.api.item.ItemMatcher;
import dev.tako.papersdelight.api.item.ItemResult;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class RecipeDecoder {

    private static final Set<String> LEGACY_INGREDIENT_KEYS = Set.of("material", "tag", "item", "ce_item");

    private final Consumer<String> warnCallback;

    RecipeDecoder(Consumer<String> warnCallback) {
        this.warnCallback = warnCallback;
    }

    @Nullable
    CookingRecipe decodeCooking(String id, String source, ConfigSection section) {

        Object ingredientsRaw = section.get("ingredients");
        if (ingredientsRaw == null) {
            warn(id, source, "missing required field 'ingredients'");
            return null;
        }

        Object resultRaw = section.get("result");
        String resultId = resultRaw instanceof Map<?, ?> resultMap
                ? getString(resultMap, "id")
                : section.getString("result");
        if (resultId == null || resultId.isBlank()) {
            warn(id, source, "missing required field 'result'");
            return null;
        }

        List<IngredientDef> ingredients = decodeIngredientList(id, source, ingredientsRaw);
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }

        int resultCount = 1;
        if (resultRaw instanceof Map<?, ?> resultMap) {
            resultCount = getInt(resultMap, "count", 1);
        }

        String container = section.getString("container");
        int time = section.getInt("time", 200);
        float experience = (float) section.getDouble("experience", 0.0);

        return new CookingRecipe("cooking", resultId, resultCount, container, time, experience, ingredients, source);
    }

    @Nullable
    CuttingRecipe decodeCutting(String id, String source, ConfigSection section) {

        String ingredient = section.getString("ingredient");
        ItemMatcher ingredientMatcher = null;
        if (ingredient == null || ingredient.isBlank()) {
            Object ingredientRaw = section.get("ingredient");
            if (ingredientRaw instanceof Map<?, ?> ingredientMap) {
                List<String> items = getStringList(ingredientMap, "items");
                if (!items.isEmpty()) {
                    ingredientMatcher = ItemMatcher.anyOf(items);
                    ingredient = items.get(0);
                }
            }
            if (ingredient == null || ingredient.isBlank()) {
                warn(id, source, "missing required field 'ingredient'");
                return null;
            }
        }

        Object resultsRaw = section.get("results");
        if (resultsRaw == null) {
            warn(id, source, "missing required field 'results'");
            return null;
        }

        List<ItemResult> results = decodeItemResults(id, source, resultsRaw);
        if (results == null || results.isEmpty()) {
            return null;
        }

        List<String> tools = section.getStringList("tools");

        SoundConfig sound = decodeSound(section.get("sound"));

        if (ingredientMatcher != null) {
            return new CuttingRecipe(ingredient, tools, results, sound, source, ingredientMatcher, null);
        }
        return new CuttingRecipe(ingredient, tools, results, sound, source);
    }

    @Nullable
    CustomRecipe.Single decodeSingle(String id, String source, ConfigSection section) {

        String item = section.getString("item");
        if (item == null || item.isBlank()) {
            warn(id, source, "missing required field 'item'");
            return null;
        }

        List<String> description = section.getStringList("description");
        if (description == null) description = List.of();

        return new CustomRecipe.Single(item, description);
    }

    @Nullable
    JugFluidFillingRecipe decodeFluidFilling(String id, String source, ConfigSection section) {
        return JugRecipeDecoderBridge.decodeFluidFilling(id, source, section);
    }

    @Nullable
    JugFluidEmptyingRecipe decodeFluidEmptying(String id, String source, ConfigSection section) {
        return JugRecipeDecoderBridge.decodeFluidEmptying(id, source, section);
    }

    @Nullable
    JugSoakingRecipe decodeSoaking(String id, String source, ConfigSection section) {
        return JugRecipeDecoderBridge.decodeSoaking(id, source, section);
    }

    @Nullable
    CustomRecipe.Decomposition decodeDecomposition(String id, String source, ConfigSection section) {

        String ingredient = section.getString("ingredient");
        if (ingredient == null || ingredient.isBlank()) {
            warn(id, source, "missing required field 'ingredient'");
            return null;
        }

        String result = section.getString("result");
        if (result == null || result.isBlank()) {
            warn(id, source, "missing required field 'result'");
            return null;
        }

        List<String> catalysts = section.getStringList("catalysts");
        if (catalysts == null) catalysts = List.of();

        return new CustomRecipe.Decomposition(ingredient, result, catalysts);
    }

    @Nullable
    private List<IngredientDef> decodeIngredientList(String id, String source, Object raw) {
        if (!(raw instanceof List<?> list)) {
            warn(id, source, "field 'ingredients' must be a list");
            return null;
        }

        List<IngredientDef> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String str) {

                result.add(new IngredientDef(null, null, null, List.of(), ItemMatcher.of(str)));
            } else if (item instanceof Map<?, ?> map) {

                if (map.keySet().stream().anyMatch(k -> LEGACY_INGREDIENT_KEYS.contains(String.valueOf(k)))) {
                    warn(id, source, "legacy wrapped ingredient format (material/tag/item/ce_item) is no longer supported");
                    return null;
                }

                List<String> items = getStringList(map, "items");
                if (items.isEmpty()) {
                    warn(id, source, "ingredient map must contain 'items' field");
                    return null;
                }
                result.add(new IngredientDef(null, null, null, List.of(), ItemMatcher.anyOf(items)));
            } else {
                warn(id, source, "invalid ingredient format: " + item);
                return null;
            }
        }

        return result;
    }

    @Nullable
    private List<ItemResult> decodeItemResults(String id, String source, Object raw) {
        if (!(raw instanceof List<?> list)) {
            warn(id, source, "field 'results' must be a list");
            return null;
        }

        List<ItemResult> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String str) {
                results.add(new ItemResult(str, 1, 1.0));
            } else if (item instanceof Map<?, ?> map) {
                String itemId = getString(map, "id");
                if (itemId == null || itemId.isBlank()) {
                    warn(id, source, "result map missing 'id' field");
                    continue;
                }

                int count = getInt(map, "count", 1);
                double chance = getDouble(map, "chance", 1.0);

                if (count < 1) {
                    warn(id, source, "invalid count " + count + " for result '" + itemId + "', clamping to 1");
                    count = 1;
                }
                if (chance < 0 || chance > 1) {
                    warn(id, source, "invalid chance " + chance + " for result '" + itemId + "', clamping to [0,1]");
                    chance = Math.max(0, Math.min(1, chance));
                }

                results.add(new ItemResult(itemId, count, chance));
            } else {
                warn(id, source, "invalid result format: " + item);
                continue;
            }
        }

        return results;
    }

    @Nullable
    private SoundConfig decodeSound(Object raw) {
        if (raw == null) return null;

        if (raw instanceof String str) {
            return new SoundConfig(str, 1.0f, 1.0f, 1.0f);
        }

        if (raw instanceof Map<?, ?> map) {
            String soundId = getString(map, "id");
            if (soundId == null || soundId.isBlank()) return null;

            float volume = (float) getDouble(map, "volume", 1.0);
            float pitch = (float) getDouble(map, "pitch", 1.0);

            return new SoundConfig(soundId, volume, pitch, pitch);
        }

        return null;
    }

    @Nullable
    private static String getString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private static int getInt(Map<?, ?> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        return defaultValue;
    }

    private static double getDouble(Map<?, ?> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    private static List<String> getStringList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) result.add(s);
        }
        return result;
    }

    private void warn(String id, String source, String message) {
        warnCallback.accept("Recipe " + id + " at " + source + ": " + message);
    }
}
