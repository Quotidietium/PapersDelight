package dev.tako.papersdelight.recipe;

import dev.tako.papersdelight.registration.config.AdvancedTagParser;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class AdvancedTagService {

    private AdvancedTagService() {
    }

    public static boolean isAdvancedTagged(ItemStack item, String tagId) {
        return DefaultItemMatcherResolver.INSTANCE.matchesAdvancedTag(item, tagId);
    }

    public static List<String> resolveItems(String tagId) {
        if (tagId == null || tagId.isBlank()) return List.of();
        String normalized = tagId.startsWith("advtag:")
                ? tagId.substring("advtag:".length()).trim() : tagId.trim();
        if (normalized.isEmpty()) return List.of();
        return AdvancedTagParser.resolve(Key.of(normalized)).stream().map(Key::asString).toList();
    }
}
