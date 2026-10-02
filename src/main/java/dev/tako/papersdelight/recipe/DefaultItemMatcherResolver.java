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
        // 空 tags 下 TagExpander.anyMatch 等价于 matchesRuntimeTag(normalize(tag))，
        // 直接调用省去 lambda 捕获与递归机器（toLowerCase 对已是小写的输入零分配）。
        return matchesRuntimeTag(item, tagId.toLowerCase(java.util.Locale.ROOT));
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

    /** 标签 id → 解析后键的进程级缓存：键对象不可变，条目数受配置内标签种类约束。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, NamespacedKey> NS_KEYS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Key> CE_KEYS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean matchesRuntimeTag(ItemStack item, String tagId) {
        try {
            var definition = CraftEngineItems.byItemStack(item);
            if (definition != null && definition.is(ceKey(tagId))) return true;
            if (CraftEngineItems.isCustomItem(item)) return false;
        } catch (Throwable ignored) {

        }

        NamespacedKey key = NS_KEYS.computeIfAbsent(tagId, NamespacedKey::fromString);
        if (key == null) return false;
        Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        return tag != null && tag.isTagged(item.getType());
    }

    private static Key ceKey(String tagId) {
        return CE_KEYS.computeIfAbsent(tagId, Key::of);
    }
}
