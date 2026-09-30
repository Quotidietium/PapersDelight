package dev.tako.papersdelight.recipe;

import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

public final class CustomRecipeManager {

    record CustomRecipeSnapshot(List<CustomRecipe.Decomposition> decompositions, List<CustomRecipe.Single> singles) {}

    private final Plugin plugin;
    private volatile CustomRecipeSnapshot snapshot = new CustomRecipeSnapshot(List.of(), List.of());

    public CustomRecipeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void publishRecipes(List<CustomRecipe.Single> newSingles, List<CustomRecipe.Decomposition> newDecomps) {
        snapshot = new CustomRecipeSnapshot(List.copyOf(newDecomps), List.copyOf(newSingles));
    }

    public RuntimeSnapshot captureRuntimeState() {
        return new RuntimeSnapshot(snapshot);
    }

    public void restoreRuntimeState(RuntimeSnapshot runtimeSnapshot) {
        snapshot = Objects.requireNonNull(runtimeSnapshot, "runtimeSnapshot").snapshot;
    }

    public static final class RuntimeSnapshot {
        private final CustomRecipeSnapshot snapshot;

        private RuntimeSnapshot(CustomRecipeSnapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    public List<CustomRecipe.Decomposition> decompositions() { return snapshot.decompositions; }
    public List<CustomRecipe.Single> singles() { return snapshot.singles; }
    public int count() { return snapshot.decompositions.size() + snapshot.singles.size(); }
}
