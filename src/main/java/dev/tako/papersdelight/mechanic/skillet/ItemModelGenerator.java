package dev.tako.papersdelight.mechanic.skillet;

import dev.tako.papersdelight.util.ItemMetaUtil;
import dev.tako.papersdelight.support.HandheldSkilletSupport;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class ItemModelGenerator implements Listener {
    private static final String NAMESPACE = "farmersdelight";
    private static final String GENERATED_PACK = "pd_generated_model";
    private static final String GENERATED_PACK_RESOURCEPACK = "pd_generated_model/resourcepack";
    private static final String LEGACY_GENERATED_PACK = "pd_skillet_model";
    private static final String FALLBACK_DEFINITION = NAMESPACE + ":handheld_skillet/fallback";
    private static final String MANIFEST = ".papersdelight-generated-item-models";
    private static final String LEGACY_MANIFEST = ".papersdelight-handheld-skillet-models";

    private final Plugin plugin;
    private volatile Set<String> generatedDefinitions = Set.of();

    public ItemModelGenerator(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        if (event.isFirstReload()) refreshGeneratedDefinitions();
        else regenerate();
    }

    private synchronized void refreshGeneratedDefinitions() {
        generatedDefinitions = Set.of();
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) return;
        Path manifestRoot = craftEngine.getDataFolder().toPath().resolve("resources")
                .resolve(GENERATED_PACK_RESOURCEPACK);
        List<Path> manifests = List.of(manifestRoot.resolve(MANIFEST), manifestRoot.resolve(LEGACY_MANIFEST));
        if (manifests.stream().noneMatch(Files::isRegularFile)) return;
        try {
            generatedDefinitions = ItemModelManifest.restoreDefinitions(manifests);
            plugin.getLogger().info(dev.tako.papersdelight.config.ConfigManager.getOr(
                    "item_model_restored", "Restored %count% item model definitions from manifest.")
                    .replace("%count%", String.valueOf(generatedDefinitions.size())));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to restore generated handheld skillet model definitions", exception);
        }
    }

    public synchronized boolean regenerate() {
        if (!HandheldSkilletSupport.isSupported(Bukkit.getMinecraftVersion())) return false;
        try {
            deleteLegacyGeneratedPack();
            Path root = generatedPackRoot();
            Set<HandheldSkilletModelLayout> layouts = collectLayouts();
            Set<String> definitions = new LinkedHashSet<>();
            Set<String> generatedFiles = new LinkedHashSet<>();
            String fallback = "assets/farmersdelight/items/handheld_skillet/fallback.json";
            write(root.resolve(fallback), fallbackJson());
            generatedFiles.add(fallback);
            for (HandheldSkilletModelLayout layout : layouts) {
                write(root.resolve(layout.modelFile()), layout.modelJson());
                write(root.resolve(layout.definitionFile()), layout.definitionJson());
                write(root.resolve(layout.flippedModelFile()), layout.flippedModelJson());
                write(root.resolve(layout.flippedDefinitionFile()), layout.flippedDefinitionJson());
                generatedFiles.add(layout.modelFile());
                generatedFiles.add(layout.definitionFile());
                generatedFiles.add(layout.flippedModelFile());
                generatedFiles.add(layout.flippedDefinitionFile());
                definitions.add(layout.itemDefinition());
                definitions.add(layout.flippedItemDefinition());
            }
            replaceManifest(root, generatedFiles);
            generatedDefinitions = Set.copyOf(definitions);
            plugin.getLogger().info(dev.tako.papersdelight.config.ConfigManager.getOr(
                    "item_model_generated", "Generated %count% item model definitions.")
                    .replace("%count%", String.valueOf(definitions.size())));
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, dev.tako.papersdelight.config.ConfigManager.getOr(
                    "item_model_generate_fail", "Failed to generate item models; existing resources were retained."), exception);
            return false;
        }
    }

    public NamespacedKey itemDefinitionFor(ItemStack stack) {
        return definitionFor(stack, false);
    }

    public NamespacedKey flippedItemDefinitionFor(ItemStack stack) {
        return definitionFor(stack, true);
    }

    private NamespacedKey definitionFor(ItemStack stack, boolean flipped) {
        String itemModel = itemModel(stack);
        if (itemModel == null) return NamespacedKey.fromString(FALLBACK_DEFINITION);
        try {
            HandheldSkilletModelLayout layout = HandheldSkilletModelLayout.fromItemModel(itemModel);
            String definition = flipped ? layout.flippedItemDefinition() : layout.itemDefinition();
            return generatedDefinitions.contains(definition)
                    ? NamespacedKey.fromString(definition)
                    : NamespacedKey.fromString(FALLBACK_DEFINITION);
        } catch (IllegalArgumentException ignored) {
            return NamespacedKey.fromString(FALLBACK_DEFINITION);
        }
    }

    private Set<HandheldSkilletModelLayout> collectLayouts() {
        List<CampfireRecipe> recipes = new java.util.ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof CampfireRecipe campfire) recipes.add(campfire);
        });
        Set<ItemStack> candidates = new LinkedHashSet<>();
        for (CampfireRecipe recipe : recipes) addChoiceCandidates(recipe.getInputChoice(), candidates);
        try {
            for (var key : CraftEngineItems.loadedItems().keySet()) {
                var definition = CraftEngineItems.byId(key);
                if (definition == null) continue;
                ItemStack candidate = definition.buildBukkitItem();
                if (recipes.stream().anyMatch(recipe -> recipe.getInputChoice().test(candidate))) candidates.add(candidate);
            }
        } catch (RuntimeException ignored) {

        }
        Set<HandheldSkilletModelLayout> layouts = new java.util.TreeSet<>(java.util.Comparator.comparing(HandheldSkilletModelLayout::itemDefinition));
        for (ItemStack candidate : candidates) {
            String itemModel = itemModel(candidate);
            if (itemModel == null) continue;
            try {
                layouts.add(HandheldSkilletModelLayout.fromItemModel(itemModel));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning(dev.tako.papersdelight.config.ConfigManager.getOr(
                        "item_model_key_invalid", "Skipping invalid item model key: %item_model%")
                        .replace("%item_model%", itemModel));
            }
        }
        return layouts;
    }

    private static void addChoiceCandidates(RecipeChoice choice, Collection<ItemStack> candidates) {
        if (choice instanceof RecipeChoice.ExactChoice exact) {
            exact.getChoices().forEach(stack -> candidates.add(stack.clone()));
        } else if (choice instanceof RecipeChoice.MaterialChoice material) {
            for (Material type : material.getChoices()) candidates.add(new ItemStack(type));
        }
    }

    private boolean legacyGeneratedPackExists() {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        return craftEngine != null && Files.exists(craftEngine.getDataFolder().toPath().resolve("resources").resolve(LEGACY_GENERATED_PACK));
    }

    private void deleteLegacyGeneratedPack() throws IOException {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) return;
        Path legacy = craftEngine.getDataFolder().toPath().resolve("resources").resolve(LEGACY_GENERATED_PACK);
        if (!Files.exists(legacy)) return;
        try (var paths = Files.walk(legacy)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
        } catch (java.io.UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private Path generatedPackRoot() throws IOException {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) throw new IllegalStateException(dev.tako.papersdelight.config.ConfigManager.getOr(
                "craftengine_not_installed", "CraftEngine is not installed."));
        Path resources = craftEngine.getDataFolder().toPath().resolve("resources");
        Path pack = resources.resolve(GENERATED_PACK);
        Path descriptor = pack.resolve("pack.yml");
        Files.createDirectories(pack.resolve("resourcepack"));
        if (Files.notExists(descriptor)) {
            Files.writeString(descriptor, """
                    author: PapersDelight
                    description: Runtime-generated item models
                    namespace: pd_generated_model
                    enable: true
                    """, StandardCharsets.UTF_8);
        }
        return resources.resolve(GENERATED_PACK_RESOURCEPACK);
    }

    private static String itemModel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NamespacedKey key = ItemMetaUtil.getItemModel(stack.getItemMeta());
        return key != null ? key.asString() : stack.getType().getKey().asString();
    }

    private static void replaceManifest(Path root, Set<String> generatedFiles) throws IOException {
        Path manifest = root.resolve(MANIFEST);
        for (Path previousManifest : List.of(manifest, root.resolve(LEGACY_MANIFEST))) {
            if (!Files.isRegularFile(previousManifest)) continue;
            for (String previous : Files.readAllLines(previousManifest, StandardCharsets.UTF_8)) {
                if (previous.startsWith("assets/farmersdelight/") && !generatedFiles.contains(previous)) {
                    Path file = root.resolve(previous).normalize();
                    if (file.startsWith(root) && Files.isRegularFile(file)) Files.delete(file);
                }
            }
            if (!previousManifest.equals(manifest)) Files.deleteIfExists(previousManifest);
        }
        write(manifest, String.join("\n", new java.util.TreeSet<>(generatedFiles)) + "\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fallbackJson() {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "farmersdelight:item/skillet_cooking"
                  }
                }
                """;
    }
}
