package dev.tako.papersdelight.recipe;

import dev.tako.papersdelight.registration.config.AdvancedTagParser;
import dev.tako.papersdelight.registration.config.AdvancedTagSnapshot;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import dev.tako.papersdelight.api.item.ItemMatcherResolver;

public final class DefaultItemMatcherResolver implements ItemMatcherResolver<ItemStack> {

    public static final DefaultItemMatcherResolver INSTANCE = new DefaultItemMatcherResolver();

    private final Supplier<AdvancedTagSnapshot> advancedTags;

    public DefaultItemMatcherResolver() {
        this(AdvancedTagParser::activeSnapshot);
    }

    DefaultItemMatcherResolver(Supplier<AdvancedTagSnapshot> advancedTags) {
        this.advancedTags = Objects.requireNonNull(advancedTags, "advancedTags");
    }

    @Override
    public boolean matchesItem(ItemStack item, String itemId) {
        return CraftEngineUtil.isItem(item, itemId);
    }

    @Override
    public boolean matchesTag(ItemStack item, String tagId) {
        if (item == null || item.isEmpty() || tagId == null || tagId.isBlank()) return false;
        return TagExpander.anyMatch(
                Map.of(),
                tagId,
                nestedTag -> matchesRuntimeTag(item, nestedTag),
                nestedItem -> matchesItem(item, nestedItem)
        );
    }

    @Override
    public boolean matchesAdvancedTag(ItemStack item, String tagId) {
        if (item == null || item.isEmpty() || tagId == null || tagId.isBlank()) return false;

        String itemId = CraftEngineUtil.getItemIdentifier(item);
        if (itemId == null) return false;

        try {
            return advancedTags.get().containsItem(Key.of(tagId), itemId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean matchesRuntimeTag(ItemStack item, String tagId) {
        try {
            var definition = CraftEngineItems.byItemStack(item);
            if (definition != null && definition.is(Key.of(tagId))) return true;
            if (CraftEngineItems.isCustomItem(item)) return false;
        } catch (Throwable ignored) {

        }

        NamespacedKey key = NamespacedKey.fromString(tagId);
        if (key == null) return false;
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        return tag != null && tag.isTagged(item.getType());
    }
}
