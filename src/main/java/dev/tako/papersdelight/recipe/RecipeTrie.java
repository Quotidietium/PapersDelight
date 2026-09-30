package dev.tako.papersdelight.recipe;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.tako.papersdelight.api.item.ItemMatcher;
import dev.tako.papersdelight.api.item.ItemMatcherResolver;

public final class RecipeTrie {

    private final Map<Integer, TrieNode> roots = new HashMap<>();
    private final ItemMatcherResolver<ItemStack> resolver;

    public RecipeTrie() {
        this(DefaultItemMatcherResolver.INSTANCE);
    }

    RecipeTrie(ItemMatcherResolver<ItemStack> resolver) {
        this.resolver = resolver;
    }

    public void insert(CookingRecipe recipe) {
        int size = recipe.ingredients.size();
        TrieNode root = roots.computeIfAbsent(size, ignored -> new TrieNode());
        List<IngredientDef> ingredients = recipe.ingredients.stream()
                .sorted(Comparator.comparing(RecipeTrie::ingredientKey))
                .toList();
        TrieNode node = root;
        for (IngredientDef ingredient : ingredients) {
            String key = ingredientKey(ingredient);
            node = node.children.computeIfAbsent(key, ignored -> new TrieNode());
            if (node.matcher == null) node.matcher = ingredient.matcher();
        }
        if (node.recipe == null) node.recipe = recipe;
    }

    @Nullable
    public CookingRecipe findMatch(ItemStack[] inputs) {
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack input : inputs) {
            if (input != null && !input.isEmpty()) nonEmpty.add(input);
        }
        if (nonEmpty.isEmpty()) return null;
        TrieNode root = roots.get(nonEmpty.size());
        if (root == null) return null;

        return findMatch(nonEmpty, resolver);
    }

    @Nullable
    <T> CookingRecipe findMatch(List<T> inputs, ItemMatcherResolver<? super T> resolver) {
        if (inputs.isEmpty()) return null;
        TrieNode root = roots.get(inputs.size());
        if (root == null) return null;
        return dfsMatch(root, inputs, resolver, new boolean[inputs.size()], 0);
    }

    @Nullable
    private <T> CookingRecipe dfsMatch(TrieNode node, List<T> inputs,
                                        ItemMatcherResolver<? super T> resolver,
                                        boolean[] used, int depth) {
        if (depth == inputs.size()) return node.recipe;
        for (TrieNode child : node.children.values()) {
            ItemMatcher matcher = child.matcher;
            if (matcher == null) continue;
            for (int i = 0; i < inputs.size(); i++) {
                if (used[i] || !matcher.matches(inputs.get(i), resolver)) continue;
                used[i] = true;
                CookingRecipe result = dfsMatch(child, inputs, resolver, used, depth + 1);
                if (result != null) return result;
                used[i] = false;
            }
        }
        return null;
    }

    static String ingredientKey(IngredientDef def) {
        return def.matcher().stableKey();
    }

    public int size() {
        int count = 0;
        for (TrieNode root : roots.values()) {
            count += countRecipes(root);
        }
        return count;
    }

    private int countRecipes(TrieNode node) {
        int count = node.recipe != null ? 1 : 0;
        for (TrieNode child : node.children.values()) {
            count += countRecipes(child);
        }
        return count;
    }
}
