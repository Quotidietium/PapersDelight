package dev.tako.papersdelight.recipe;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import dev.tako.papersdelight.api.item.ItemMatcher;

public record IngredientDef(@Nullable Material material, @Nullable String tag, @Nullable String ceItem,
                            List<String> anyOf, ItemMatcher matcher) {

    public IngredientDef {
        anyOf = anyOf == null ? List.of() : List.copyOf(anyOf);
        if (matcher == null) matcher = legacyMatcher(material, tag, ceItem, anyOf);
    }

    public IngredientDef(@Nullable Material material, @Nullable String tag, @Nullable String ceItem,
                         List<String> anyOf) {
        this(material, tag, ceItem, anyOf, null);
    }

    public IngredientDef(@Nullable Material material, @Nullable String tag, @Nullable String ceItem) {
        this(material, tag, ceItem, List.of(), null);
    }

    public List<String> displayExpressions() {
        if (!anyOf.isEmpty()) return anyOf;
        if (material != null) return List.of(material.getKey().toString());
        if (ceItem != null && !ceItem.isBlank()) return List.of(ceItem);
        if (tag != null && !tag.isBlank()) return List.of(tag.startsWith("#") ? tag : "#" + tag);
        return matcher.expressions();
    }

    private static ItemMatcher legacyMatcher(@Nullable Material material, @Nullable String tag,
                                             @Nullable String ceItem, List<String> anyOf) {
        List<String> expressions = new ArrayList<>();
        if (material != null) expressions.add(material.getKey().toString());
        if (tag != null && !tag.isBlank()) expressions.add(tag.startsWith("#") ? tag : "#" + tag);
        if (ceItem != null && !ceItem.isBlank()) expressions.add(ceItem);
        expressions.addAll(anyOf);
        return ItemMatcher.anyOf(expressions);
    }
}
