package dev.tako.papersdelight.recipe;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import dev.tako.papersdelight.api.item.ItemMatcher;

final class TrieNode {
    final Map<String, TrieNode> children = new LinkedHashMap<>();
    @Nullable
    ItemMatcher matcher;
    @Nullable
    CookingRecipe recipe;
}
