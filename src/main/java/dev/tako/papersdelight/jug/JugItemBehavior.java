package dev.tako.papersdelight.jug;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

public final class JugItemBehavior extends ItemBehavior {
    public static final String BEHAVIOR_ID = "papersdelight:jug_item";
    private static final String MODEL = "model";
    public static final ItemBehaviorFactory<JugItemBehavior> FACTORY = new Factory();

    private final String modelPrefix;

    private JugItemBehavior(String modelPrefix) {
        this.modelPrefix = modelPrefix;
    }

    String modelPrefix() {
        return modelPrefix;
    }

    public static void register() {
        ItemBehaviors.register(Key.of(BEHAVIOR_ID), FACTORY);
    }

    @Nullable
    public static String modelPrefix(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        var definition = CraftEngineItems.byItemStack(item);
        if (definition == null) return null;
        JugItemBehavior behavior = definition.behavior().getFirst(JugItemBehavior.class);
        return behavior == null ? null : behavior.modelPrefix();
    }

    static String modelPath(String prefix, String texture, int level) {
        return JugItemModelLayout.of(prefix, texture, level).itemDefinition();
    }

    static String fluidModelPath(String prefix, String texture, int level) {
        validateModelPrefix(prefix);
        String fluid = texture == null || texture.isBlank() ? "water" : texture.trim();
        int normalizedLevel = Math.min(Math.max(level, 1), 16);
        int separator = prefix.indexOf(':');
        String namespace = prefix.substring(0, separator);
        String path = prefix.substring(separator + 1);
        int lastSlash = path.lastIndexOf('/');
        return namespace + ":" + path.substring(0, lastSlash + 1) + path.substring(lastSlash + 1)
                + "_" + fluid + "_model_"
                + String.format(Locale.ROOT, "%02d", normalizedLevel);
    }

    static void validateModelPrefix(String prefix) {
        if (prefix == null || !prefix.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Jug item model must be a namespace:path/model key");
        }
    }

    private static final class Factory implements ItemBehaviorFactory<JugItemBehavior> {
        @Override
        public JugItemBehavior create(Pack pack, Path path, Key itemId, ConfigSection section) {
            String prefix = section.getNonEmptyString(MODEL).trim();
            validateModelPrefix(prefix);
            return new JugItemBehavior(prefix);
        }
    }
}
