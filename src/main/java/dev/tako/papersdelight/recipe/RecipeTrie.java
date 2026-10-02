package dev.tako.papersdelight.recipe;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.tako.papersdelight.api.item.ItemMatcher;
import dev.tako.papersdelight.api.item.ItemMatcherResolver;

/**
 * 多原料配方前缀树。
 *
 * <p>构建期：按原料数分根，原料按 stableKey 排序后建树；同一路径首条配方胜出
 * （{@code node.recipe == null} 才写入，matcher 同样首见写入）——该语义自初版起保持不变。
 *
 * <p>查询期（性能优化版）：首次查询时把整棵树惰性冻结为并行数组结构
 * （每节点 children/matchers 以数组存储，保留构建期的插入顺序），随后 DFS 使用
 * long 位掩码代替 boolean[]（输入数 ≤ 64 时零分配），避免 LinkedHashMap 的迭代器与
 * 节点指针追逐。冻结是纯函数式的（对同一棵树任何线程冻结结果等价），并发首查的
 * 重复计算良性无害；insert 会丢弃冻结结果。
 */
public final class RecipeTrie {

    private final Map<Integer, TrieNode> roots = new HashMap<>();
    private final ItemMatcherResolver<ItemStack> resolver;

    /** 冻结后的紧凑结构；volatile 保证安全发布，insert 置空使其失效。 */
    private volatile Frozen frozen;

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
        frozen = null;
    }

    @Nullable
    public CookingRecipe findMatch(ItemStack[] inputs) {
        if (inputs == null) return null;
        int nonEmptyCount = 0;
        for (ItemStack input : inputs) {
            if (input != null && !input.isEmpty()) nonEmptyCount++;
        }
        if (nonEmptyCount == 0) return null;

        Frozen snap = frozenOrBuild();
        FrozenNode root = snap.rootFor(nonEmptyCount);
        if (root == null) return null;

        // 压实非空输入：一次小数组分配，避免 ArrayList 装箱与扩容
        ItemStack[] compact = new ItemStack[nonEmptyCount];
        int idx = 0;
        for (ItemStack input : inputs) {
            if (input != null && !input.isEmpty()) compact[idx++] = input;
        }
        if (nonEmptyCount <= 64) {
            return dfsFrozenMask(root, compact, 0, resolver, 0L);
        }
        boolean[] used = new boolean[nonEmptyCount];
        CookingRecipe result = dfsFrozenUsed(root, compact, 0, resolver, used);
        if (result == null) Arrays.fill(used, false);
        return result;
    }

    @Nullable
    <T> CookingRecipe findMatch(List<T> inputs, ItemMatcherResolver<? super T> resolver) {
        if (inputs == null || inputs.isEmpty()) return null;
        Frozen snap = frozenOrBuild();
        FrozenNode root = snap.rootFor(inputs.size());
        if (root == null) return null;
        if (inputs.size() <= 64) {
            return dfsFrozenListMask(root, inputs, 0, resolver, 0L);
        }
        boolean[] used = new boolean[inputs.size()];
        CookingRecipe result = dfsFrozenListUsed(root, inputs, 0, resolver, used);
        if (result == null) Arrays.fill(used, false);
        return result;
    }

    static String ingredientKey(IngredientDef def) {
        return def.matcher().stableKey();
    }

    public int size() {
        Frozen snap = frozen;
        if (snap != null) return snap.recipeCount;
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

    // ------------------------------------------------------------------
    // 冻结结构与 DFS（查询热路径）
    // ------------------------------------------------------------------

    private Frozen frozenOrBuild() {
        Frozen snap = frozen;
        if (snap != null) return snap;
        snap = buildFrozen();
        frozen = snap;
        return snap;
    }

    private Frozen buildFrozen() {
        int[] sizes = new int[roots.size()];
        FrozenNode[] frozenRoots = new FrozenNode[roots.size()];
        int count = 0;
        int i = 0;
        for (Map.Entry<Integer, TrieNode> entry : roots.entrySet()) {
            sizes[i] = entry.getKey();
            frozenRoots[i] = freeze(entry.getValue());
            i++;
            count += countRecipes(entry.getValue());
        }
        return new Frozen(sizes, frozenRoots, count);
    }

    private static FrozenNode freeze(TrieNode node) {
        int n = node.children.size();
        FrozenNode[] children = new FrozenNode[n];
        ItemMatcher[] matchers = new ItemMatcher[n];
        int i = 0;
        // LinkedHashMap.values() 保持插入顺序：冻结后数组序 = 构建期迭代序（匹配优先级不变）
        for (TrieNode child : node.children.values()) {
            children[i] = freeze(child);
            matchers[i] = child.matcher;
            i++;
        }
        return new FrozenNode(matchers, children, node.recipe);
    }

    private static final class Frozen {
        final int[] sizes;
        final FrozenNode[] rootsBySize;
        final int recipeCount;

        Frozen(int[] sizes, FrozenNode[] rootsBySize, int recipeCount) {
            this.sizes = sizes;
            this.rootsBySize = rootsBySize;
            this.recipeCount = recipeCount;
        }

        @Nullable
        FrozenNode rootFor(int size) {
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i] == size) return rootsBySize[i];
            }
            return null;
        }
    }

    private record FrozenNode(ItemMatcher[] matchers, FrozenNode[] children, @Nullable CookingRecipe recipe) {}

    /** 数组输入 + 位掩码版 DFS：与初版 dfsMatch 的 child-major / input 内层序完全一致。 */
    @Nullable
    private static <T> CookingRecipe dfsFrozenMask(FrozenNode node, T[] inputs, int depth,
                                                   ItemMatcherResolver<? super T> resolver, long usedMask) {
        if (depth == inputs.length) return node.recipe;
        FrozenNode[] children = node.children;
        ItemMatcher[] matchers = node.matchers;
        for (int c = 0; c < children.length; c++) {
            ItemMatcher matcher = matchers[c];
            if (matcher == null) continue;
            for (int i = 0; i < inputs.length; i++) {
                if ((usedMask >>> i & 1L) != 0L) continue;
                if (!matcher.matches(inputs[i], resolver)) continue;
                CookingRecipe result = dfsFrozenMask(children[c], inputs, depth + 1, resolver, usedMask | (1L << i));
                if (result != null) return result;
            }
        }
        return null;
    }

    /** List 输入 + 位掩码版 DFS（保留包私有泛型入口供基准/测试直接驱动）。 */
    @Nullable
    private static <T> CookingRecipe dfsFrozenListMask(FrozenNode node, List<T> inputs, int depth,
                                                       ItemMatcherResolver<? super T> resolver, long usedMask) {
        if (depth == inputs.size()) return node.recipe;
        FrozenNode[] children = node.children;
        ItemMatcher[] matchers = node.matchers;
        for (int c = 0; c < children.length; c++) {
            ItemMatcher matcher = matchers[c];
            if (matcher == null) continue;
            for (int i = 0; i < inputs.size(); i++) {
                if ((usedMask >>> i & 1L) != 0L) continue;
                if (!matcher.matches(inputs.get(i), resolver)) continue;
                CookingRecipe result = dfsFrozenListMask(children[c], inputs, depth + 1, resolver, usedMask | (1L << i));
                if (result != null) return result;
            }
        }
        return null;
    }

    /** 输入数 > 64 时的回退 DFS：语义与位掩码版一致（used 失败路径复位）。 */
    @Nullable
    private static <T> CookingRecipe dfsFrozenUsed(FrozenNode node, T[] inputs, int depth,
                                                   ItemMatcherResolver<? super T> resolver, boolean[] used) {
        if (depth == inputs.length) return node.recipe;
        FrozenNode[] children = node.children;
        ItemMatcher[] matchers = node.matchers;
        for (int c = 0; c < children.length; c++) {
            ItemMatcher matcher = matchers[c];
            if (matcher == null) continue;
            for (int i = 0; i < inputs.length; i++) {
                if (used[i]) continue;
                if (!matcher.matches(inputs[i], resolver)) continue;
                used[i] = true;
                CookingRecipe result = dfsFrozenUsed(children[c], inputs, depth + 1, resolver, used);
                if (result != null) {
                    used[i] = false;
                    return result;
                }
                used[i] = false;
            }
        }
        return null;
    }

    @Nullable
    private static <T> CookingRecipe dfsFrozenListUsed(FrozenNode node, List<T> inputs, int depth,
                                                       ItemMatcherResolver<? super T> resolver, boolean[] used) {
        if (depth == inputs.size()) return node.recipe;
        FrozenNode[] children = node.children;
        ItemMatcher[] matchers = node.matchers;
        for (int c = 0; c < children.length; c++) {
            ItemMatcher matcher = matchers[c];
            if (matcher == null) continue;
            for (int i = 0; i < inputs.size(); i++) {
                if (used[i]) continue;
                if (!matcher.matches(inputs.get(i), resolver)) continue;
                used[i] = true;
                CookingRecipe result = dfsFrozenListUsed(children[c], inputs, depth + 1, resolver, used);
                if (result != null) {
                    used[i] = false;
                    return result;
                }
                used[i] = false;
            }
        }
        return null;
    }
}
