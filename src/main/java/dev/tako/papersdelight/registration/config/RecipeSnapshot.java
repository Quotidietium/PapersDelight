package dev.tako.papersdelight.registration.config;

import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import dev.tako.papersdelight.mechanic.cutting.CuttingRecipe;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.recipe.CustomRecipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RecipeSnapshot(
        Map<String, CookingRecipe> cooking,
        Map<String, CuttingRecipe> cutting,
        Map<String, CustomRecipe.Single> single,
        Map<String, CustomRecipe.Decomposition> decomposition,
        Map<String, JugFluidFillingRecipe> fluidFilling,
        Map<String, JugFluidEmptyingRecipe> fluidEmptying,
        Map<String, JugSoakingRecipe> soaking
) {
    public RecipeSnapshot {
        cooking = immutableOrderedCopy(cooking);
        cutting = immutableOrderedCopy(cutting);
        single = immutableOrderedCopy(single);
        decomposition = immutableOrderedCopy(decomposition);
        fluidFilling = immutableOrderedCopy(fluidFilling);
        fluidEmptying = immutableOrderedCopy(fluidEmptying);
        soaking = immutableOrderedCopy(soaking);
    }

    public RecipeSnapshot(Map<String, CookingRecipe> cooking,
                          Map<String, CuttingRecipe> cutting,
                          Map<String, CustomRecipe.Single> single,
                          Map<String, CustomRecipe.Decomposition> decomposition) {
        this(cooking, cutting, single, decomposition, Map.of(), Map.of(), Map.of());
    }

    public static RecipeSnapshot empty() {
        return new RecipeSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static <T> Map<String, T> immutableOrderedCopy(Map<String, T> recipes) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
    }

    public int totalCount() {
        return cooking.size() + cutting.size() + single.size() + decomposition.size()
                + fluidFilling.size() + fluidEmptying.size() + soaking.size();
    }
}
