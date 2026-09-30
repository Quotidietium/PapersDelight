package dev.tako.papersdelight;

import dev.tako.papersdelight.mechanic.cutting.CuttingBoardManager;
import dev.tako.papersdelight.mechanic.cutting.CuttingRecipe;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import dev.tako.papersdelight.recipe.CustomRecipe;
import dev.tako.papersdelight.recipe.CustomRecipeManager;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.registration.RuntimeConfigHandoff;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.Objects;

final class PapersDelightRuntimeTargets implements RuntimeConfigHandoff.RuntimeTargets {
    private final RecipeManager recipeManager;
    private final CuttingBoardManager cuttingBoardManager;
    private final CustomRecipeManager customRecipeManager;
    PapersDelightRuntimeTargets(JavaPlugin plugin, RecipeManager recipes, CuttingBoardManager cutting, CustomRecipeManager custom) {
        this.recipeManager = Objects.requireNonNull(recipes);
        this.cuttingBoardManager = Objects.requireNonNull(cutting);
        this.customRecipeManager = Objects.requireNonNull(custom);
    }
    public RuntimeConfigHandoff.SynchronousState captureSynchronousState() {
        return new State(recipeManager.captureRuntimeState(), cuttingBoardManager.captureRuntimeState(), customRecipeManager.captureRuntimeState());
    }
    public void restoreSynchronousState(RuntimeConfigHandoff.SynchronousState state) {
        State s = (State) state;
        customRecipeManager.restoreRuntimeState(s.custom());
        cuttingBoardManager.restoreRuntimeState(s.cutting());
        recipeManager.restoreRuntimeState(s.recipes());
    }
    public void publishRecipes(List<CookingRecipe> recipes) { recipeManager.publishRuntimeConfig(recipes); }
    public void publishJugRecipes(List<JugFluidFillingRecipe> a, List<JugFluidEmptyingRecipe> b, List<JugSoakingRecipe> c) { recipeManager.publishJugRecipes(a, b, c); }
    public CuttingBoardManager.RuntimeSettings cuttingSettingsCandidate() { return cuttingBoardManager.reloadRuntimeSettings(); }
    public void publishCutting(List<CuttingRecipe> recipes, CuttingBoardManager.RuntimeSettings settings) { cuttingBoardManager.publishRuntimeConfig(recipes, settings); }
    public void publishCustomRecipes(List<CustomRecipe.Single> singles, List<CustomRecipe.Decomposition> decompositions) {
        if (dev.tako.papersdelight.support.FeatureSupport.customRecipes()) customRecipeManager.publishRecipes(singles, decompositions);
    }
    private record State(RecipeManager.RuntimeSnapshot recipes, CuttingBoardManager.RuntimeSnapshot cutting, CustomRecipeManager.RuntimeSnapshot custom) implements RuntimeConfigHandoff.SynchronousState { }
}
