package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.util.ReflectionHandles;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

public final class JugItemModelGenerator implements Listener {
    private static final String PACK = "pd_generated_model";
    private static final String PACK_RESOURCEPACK = PACK + "/resourcepack";
    private static final String MANIFEST = ".papersdelight-generated-jug-item-models";

    private final Plugin plugin;
    private volatile Set<String> generatedDefinitions = Set.of();

    public JugItemModelGenerator(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        if (event.isFirstReload()) refreshGeneratedDefinitions();
        else regenerate();
    }

    public boolean regenerate() {
        try {
            Path root = generatedPackRoot();
            Set<JugItemModelLayout> layouts = collectLayouts();
            Set<String> generatedFiles = new LinkedHashSet<>();
            Set<String> definitions = new LinkedHashSet<>();
            for (JugItemModelLayout layout : layouts) {
                write(root.resolve(layout.definitionFile()), layout.definitionJson());
                generatedFiles.add(layout.definitionFile());
                definitions.add(layout.itemDefinition());
            }
            replaceManifest(root, generatedFiles);
            generatedDefinitions = Set.copyOf(definitions);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to generate Jug item models; existing resources were retained.", exception);
            return false;
        }
    }

    private Set<JugItemModelLayout> collectLayouts() {
        Set<String> textures = new TreeSet<>();
        textures.add("water");
        try {
            Class<?> registry = Class.forName("dev.tako.libuid.api.FluidRegistry");
            Object values = ReflectionHandles.callStaticNoArg(registry, "all");
            if (values instanceof Iterable<?> fluids) {
                for (Object fluid : fluids) {
                    Object optional = ReflectionHandles.callNoArg(fluid, "texture");
                    if (optional instanceof java.util.Optional<?> value && value.isPresent()
                            && value.get() instanceof String texture && !texture.isBlank()) {
                        textures.add(texture.trim().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {

        }
        textures.removeIf(texture -> CraftEngineUtil.createItem(
                "farmersdelight:jug_fluid_" + texture, 1) == null);
        Set<JugItemModelLayout> layouts = new LinkedHashSet<>();
        for (String texture : textures) {
            for (int stage = 1; stage <= 16; stage++) {
                layouts.add(JugItemModelLayout.of(
                        "farmersdelight:block/jug_fluid/glass_jug_fluid", texture, stage));
            }
        }
        return layouts;
    }

    private void refreshGeneratedDefinitions() {
        try {
            Path root = generatedPackRoot();
            Path manifest = root.resolve(MANIFEST);
            if (!Files.isRegularFile(manifest)) return;
            Set<String> definitions = new LinkedHashSet<>();
            for (String file : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                if (file.startsWith("assets/farmersdelight/items/") && file.endsWith(".json")) {
                    definitions.add("farmersdelight:" + file.substring("assets/farmersdelight/items/".length(), file.length() - 5));
                }
            }
            generatedDefinitions = Set.copyOf(definitions);
        } catch (IOException ignored) {
            generatedDefinitions = Set.of();
        }
    }

    public boolean hasDefinition(String definition) {
        return generatedDefinitions.contains(definition);
    }

    private Path generatedPackRoot() throws IOException {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) throw new IllegalStateException("CraftEngine is not installed.");
        Path pack = craftEngine.getDataFolder().toPath().resolve("resources").resolve(PACK);
        Files.createDirectories(pack.resolve("resourcepack"));
        Path descriptor = pack.resolve("pack.yml");
        if (Files.notExists(descriptor)) {
            Files.writeString(descriptor, "author: PapersDelight\ndescription: Runtime-generated item models\nnamespace: pd_generated_model\nenable: true\n", StandardCharsets.UTF_8);
        }
        return pack.resolve("resourcepack");
    }

    private static void replaceManifest(Path root, Set<String> generatedFiles) throws IOException {
        Path manifest = root.resolve(MANIFEST);
        if (Files.isRegularFile(manifest)) {
            for (String previous : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                if (!previous.isBlank() && !generatedFiles.contains(previous)) {
                    Files.deleteIfExists(root.resolve(previous).normalize());
                }
            }
        }
        write(manifest, String.join("\n", new TreeSet<>(generatedFiles)) + "\n");
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
}
