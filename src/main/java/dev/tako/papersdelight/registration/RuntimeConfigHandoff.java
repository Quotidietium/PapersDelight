package dev.tako.papersdelight.registration;

import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import dev.tako.papersdelight.mechanic.cutting.CuttingBoardManager;
import dev.tako.papersdelight.mechanic.cutting.CuttingRecipe;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.recipe.CustomRecipe;
import dev.tako.papersdelight.registration.config.AdvancedTagParser;
import dev.tako.papersdelight.registration.config.AdvancedTagSnapshot;
import dev.tako.papersdelight.registration.config.PapersDelightRecipeParser;
import dev.tako.papersdelight.registration.config.RecipeSnapshot;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RuntimeConfigHandoff implements Listener {
    private final Logger logger;
    private final Supplier<CeSnapshots> snapshotSupplier;
    private final AdvancedTagPublication advancedTags;
    private volatile RuntimeTargets targets;
    private volatile CeSnapshots accepted;

    public RuntimeConfigHandoff(Logger logger, Supplier<CeSnapshots> snapshotSupplier) {
        this(logger, snapshotSupplier, null, new DefaultAdvancedTags());
    }

    RuntimeConfigHandoff(Logger logger, Supplier<CeSnapshots> snapshotSupplier, RuntimeTargets targets) {
        this(logger, snapshotSupplier, targets, new DefaultAdvancedTags());
    }

    RuntimeConfigHandoff(Logger logger, Supplier<CeSnapshots> snapshotSupplier, RuntimeTargets targets,
                         AdvancedTagPublication advancedTags) {
        this.logger = Objects.requireNonNull(logger);
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier);
        this.targets = targets;
        this.advancedTags = advancedTags;
    }

    public void attachTargets(RuntimeTargets targets) { this.targets = Objects.requireNonNull(targets); }
    public synchronized boolean applyStartup() { return accepted != null ? apply(accepted, false) : applyLatest(); }
    public synchronized boolean reapplyAccepted() { return accepted != null && apply(accepted, false); }
    public synchronized boolean applyLatest() {
        if (targets == null) return false;
        try {
            CeSnapshots candidate = Objects.requireNonNull(snapshotSupplier.get());
            boolean ok = apply(candidate, true);
            if (ok) accepted = candidate;
            return ok;
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to apply CraftEngine runtime configuration", t);
            return false;
        }
    }
    @EventHandler public void onCraftEngineReload(CraftEngineReloadEvent event) { applyLatest(); }

    private boolean apply(CeSnapshots snapshot, boolean publishTags) {
        RuntimeTargets t = targets;
        if (t == null) return false;
        RuntimeConfiguration c = merge(snapshot);
        SynchronousState previous = t.captureSynchronousState();
        try {
            t.publishRecipes(c.cooking());
            t.publishJugRecipes(c.jugFilling(), c.jugEmptying(), c.jugSoaking());
            CuttingBoardManager.RuntimeSettings settings = t.cuttingSettingsCandidate();
            t.publishCutting(c.cutting(), settings);
            t.publishCustomRecipes(c.singles(), c.decompositions());
            if (publishTags && snapshot.advancedTagsPublished()) {
                if (!advancedTags.commitPending(snapshot.advancedTags(), snapshot.advancedTagsEpoch())) return false;
            }
            return true;
        } catch (Throwable failure) {
            try { t.restoreSynchronousState(previous); } catch (Throwable ignored) { }
            if (publishTags && snapshot.advancedTagsPublished()) advancedTags.discardPending(snapshot.advancedTags(), snapshot.advancedTagsEpoch());
            logger.log(Level.WARNING, "Failed to apply runtime configuration", failure);
            return false;
        }
    }

    public static RuntimeConfiguration merge(CeSnapshots ce) {
        RecipeSnapshot recipes = ce.recipesPublished() ? ce.recipes() : RecipeSnapshot.empty();
        return new RuntimeConfiguration(
                List.copyOf(recipes.cooking().values()), List.copyOf(recipes.cutting().values()),
                List.copyOf(recipes.single().values()), List.copyOf(recipes.decomposition().values()),
                List.copyOf(recipes.fluidFilling().values()), List.copyOf(recipes.fluidEmptying().values()),
                List.copyOf(recipes.soaking().values()));
    }

    public interface SynchronousState { }
    private enum EmptyState implements SynchronousState { INSTANCE }
    public interface RuntimeTargets {
        default SynchronousState captureSynchronousState() { return EmptyState.INSTANCE; }
        default void restoreSynchronousState(SynchronousState state) { }
        void publishRecipes(List<CookingRecipe> recipes);
        default void publishJugRecipes(List<JugFluidFillingRecipe> filling, List<JugFluidEmptyingRecipe> emptying, List<JugSoakingRecipe> soaking) { }
        default CuttingBoardManager.RuntimeSettings cuttingSettingsCandidate() { return null; }
        default void publishCutting(List<CuttingRecipe> recipes, CuttingBoardManager.RuntimeSettings settings) { publishCutting(recipes); }
        default void publishCutting(List<CuttingRecipe> recipes) { }
        void publishCustomRecipes(List<CustomRecipe.Single> singles, List<CustomRecipe.Decomposition> decompositions);
    }

    interface AdvancedTagPublication {
        AdvancedTagSnapshot captureActive();
        boolean commitPending(AdvancedTagSnapshot snapshot, long epoch);
        void restoreActive(AdvancedTagSnapshot snapshot);
        void discardPending(AdvancedTagSnapshot snapshot, long epoch);
    }
    private static final class DefaultAdvancedTags implements AdvancedTagPublication {
        public AdvancedTagSnapshot captureActive() { return AdvancedTagParser.activeSnapshot(); }
        public boolean commitPending(AdvancedTagSnapshot s, long e) { return AdvancedTagParser.commitPending(s, e); }
        public void restoreActive(AdvancedTagSnapshot s) { AdvancedTagParser.restoreActiveSnapshot(s); }
        public void discardPending(AdvancedTagSnapshot s, long e) { AdvancedTagParser.discardPending(s, e); }
    }

    public record CeSnapshots(RecipeSnapshot recipes, long recipeEpoch, AdvancedTagSnapshot advancedTags, long advancedTagsEpoch) {
        public CeSnapshots { recipes = recipes == null ? RecipeSnapshot.empty() : recipes; advancedTags = advancedTags == null ? AdvancedTagSnapshot.empty() : advancedTags; }
        public static CeSnapshots unpublished() { return new CeSnapshots(RecipeSnapshot.empty(), 0L, AdvancedTagSnapshot.empty(), 0L); }
        public boolean recipesPublished() { return recipeEpoch > 0L; }
        public boolean advancedTagsPublished() { return advancedTagsEpoch > 0L; }
        public static CeSnapshots current() { return new CeSnapshots(PapersDelightRecipeParser.snapshot(), PapersDelightRecipeParser.publicationEpoch(), AdvancedTagParser.pendingSnapshot(), AdvancedTagParser.publicationEpoch()); }
    }

    public record RuntimeConfiguration(List<CookingRecipe> cooking, List<CuttingRecipe> cutting, List<CustomRecipe.Single> singles,
                                       List<CustomRecipe.Decomposition> decompositions, List<JugFluidFillingRecipe> jugFilling,
                                       List<JugFluidEmptyingRecipe> jugEmptying, List<JugSoakingRecipe> jugSoaking) { }
}
