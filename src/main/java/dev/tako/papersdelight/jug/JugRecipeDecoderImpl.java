package dev.tako.papersdelight.jug;

import dev.tako.libuid.api.FluidIngredient;
import dev.tako.libuid.api.SizedFluidIngredient;
import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.logging.Logger;

public final class JugRecipeDecoderImpl implements JugRecipeDecoderBridge.JugRecipeDecoder {

    private static final int DEFAULT_AMOUNT = 1000;
    private static final Logger LOGGER = Logger.getLogger(JugRecipeDecoderImpl.class.getName());

    @Override
    public @Nullable JugFluidFillingRecipe decodeFluidFilling(String id, String source, ConfigSection section) {
        DecodedFluid fluid = decodeFluid(id, section);
        String emptyInput = required(id, section, "empty_input");
        String filledResult = required(id, section, "filled_result");
        if (fluid == null || emptyInput == null || filledResult == null) return null;
        return new JugFluidFillingRecipe(id, fluid.expression(), fluid.amount(), emptyInput, filledResult, source);
    }

    @Override
    public @Nullable JugFluidEmptyingRecipe decodeFluidEmptying(String id, String source, ConfigSection section) {
        DecodedFluid fluid = decodeFluid(id, section);
        String filledInput = required(id, section, "filled_input");
        String emptyResult = required(id, section, "empty_result");
        if (fluid == null || filledInput == null || emptyResult == null) return null;

        if (!isLosslesslyConstructibleEmptyingFluid(fluid.expression())) {
            warn(id, "fluid_emptying requires a single fluid id without components, tags, or alternatives");
            return null;
        }
        return new JugFluidEmptyingRecipe(id, fluid.expression(), fluid.amount(), filledInput, emptyResult, source);
    }

    @Override
    public @Nullable JugSoakingRecipe decodeSoaking(String id, String source, ConfigSection section) {
        DecodedFluid fluid = decodeFluid(id, section);
        String ingredient = required(id, section, "ingredient");
        String result = required(id, section, "result");
        int time = section.getInt("time", 0);
        if (time < 0) {
            warn(id, "field 'time' must not be negative");
            return null;
        }
        if (fluid == null || ingredient == null || result == null) return null;
        return new JugSoakingRecipe(id, ingredient, fluid.expression(), fluid.amount(), result, time,
                section.getBoolean("consume_fluid", true), source);
    }

    @Nullable
    private static DecodedFluid decodeFluid(String recipeId, ConfigSection section) {
        Object raw = section.get("fluid");
        if (!hasExpression(raw)) {
            warn(recipeId, "missing required field 'fluid'");
            return null;
        }
        int amount = amount(raw, section.getInt("amount", DEFAULT_AMOUNT));
        if (amount <= 0) {
            warn(recipeId, "field 'amount' must be positive");
            return null;
        }
        try {
            SizedFluidIngredient ignored = FluidIngredient.parseSized(raw, amount);
            return new DecodedFluid(raw, amount);
        } catch (IllegalArgumentException exception) {
            warn(recipeId, "invalid fluid expression: " + exception.getMessage());
            return null;
        }
    }

    @Nullable
    private static String required(String recipeId, ConfigSection section, String field) {
        String value = section.getString(field);
        if (value == null || value.isBlank()) {
            warn(recipeId, "missing required field '" + field + "'");
            return null;
        }
        return value;
    }

    private static boolean hasExpression(@Nullable Object raw) {
        return raw instanceof String value ? !value.isBlank() : raw instanceof Map<?, ?> map && !map.isEmpty();
    }

    private static boolean isLosslesslyConstructibleEmptyingFluid(Object expression) {
        if (expression instanceof String value) return !value.isBlank() && !value.startsWith("#");
        if (!(expression instanceof Map<?, ?> map)) return false;
        if (!(map.get("fluid") instanceof String fluid) || fluid.isBlank() || fluid.startsWith("#")) return false;
        return map.keySet().stream().allMatch(key -> "fluid".equals(key) || "amount".equals(key));
    }

    private static int amount(@Nullable Object raw, int fallback) {
        if (raw instanceof Map<?, ?> map && map.get("amount") instanceof Number value) {
            return value.intValue();
        }
        return fallback;
    }

    private static void warn(String recipeId, String message) {
        LOGGER.warning("Skipping jug recipe " + recipeId + ": " + message);
    }

    private record DecodedFluid(Object expression, int amount) {
    }
}
