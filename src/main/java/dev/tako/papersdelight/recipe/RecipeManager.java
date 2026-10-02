package dev.tako.papersdelight.recipe;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class RecipeManager {

    private static final AtomicLong EPOCH_COUNTER = new AtomicLong();

    private static final AtomicReference<RecipeSnapshot> SNAPSHOT =
            new AtomicReference<>(RecipeSnapshot.create(List.of()));
    private static final AtomicReference<JugRecipes> JUG_RECIPES =
            new AtomicReference<>(JugRecipes.empty());

    /**
     * 产物原型缓存：配方结果物品的共享只读实例，供烹饪锅等容器的逐 tick
     * 判定（isSimilar/数量比较）复用，替代每 tick 走一次 CraftEngine
     * buildBukkitItem 构建管线。快照替换与恢复时整体失效；
     * 调用方不得修改返回的实例。
     */
    private static final ConcurrentHashMap<CookingRecipe, Prototype> RESULT_PROTOTYPES =
            new ConcurrentHashMap<>();

    private record Prototype(ItemStack stack) {}

    public ItemStack resultPrototype(CookingRecipe recipe) {
        Prototype proto = RESULT_PROTOTYPES.get(recipe);
        if (proto == null) {
            proto = new Prototype(CraftEngineUtil.createItem(recipe.result, recipe.resultCount));
            RESULT_PROTOTYPES.put(recipe, proto);
        }
        return proto.stack();
    }

    public long snapshotEpoch() {
        return SNAPSHOT.get().epoch();
    }

    public CookingRecipe findMatch(ItemStack[] inputs) {
        RecipeSnapshot snapshot = SNAPSHOT.get();
        CookingRecipe result = snapshot.trie().findMatch(inputs);
        if (result != null) return result;

        for (CookingRecipe recipe : snapshot.recipes()) {
            if (matches(recipe, inputs)) return recipe;
        }
        return null;
    }

    public int count() {
        return SNAPSHOT.get().recipes().size();
    }

    public List<CookingRecipe> recipes() {
        return SNAPSHOT.get().recipes();
    }

    public RuntimeSnapshot captureRuntimeState() {

        return new RuntimeSnapshot(SNAPSHOT.get(), JUG_RECIPES.get());
    }

    public void restoreRuntimeState(RuntimeSnapshot snapshot) {
        RuntimeSnapshot state = Objects.requireNonNull(snapshot, "snapshot");
        SNAPSHOT.set(state.snapshot);
        JUG_RECIPES.set(state.jugRecipes);
        RESULT_PROTOTYPES.clear();
    }

    public static final class RuntimeSnapshot {
        private final RecipeSnapshot snapshot;
        private final JugRecipes jugRecipes;

        private RuntimeSnapshot(RecipeSnapshot snapshot, JugRecipes jugRecipes) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.jugRecipes = Objects.requireNonNull(jugRecipes, "jugRecipes");
        }
    }

    void replaceSnapshot(List<CookingRecipe> recipes) {
        SNAPSHOT.set(RecipeSnapshot.create(recipes));
        RESULT_PROTOTYPES.clear();
    }

    public void publishRuntimeConfig(List<CookingRecipe> recipes) {
        replaceSnapshot(recipes);
    }

    public void publishJugRecipes(
            List<JugFluidFillingRecipe> filling,
            List<JugFluidEmptyingRecipe> emptying,
            List<JugSoakingRecipe> soaking
    ) {
        JUG_RECIPES.set(new JugRecipes(filling, emptying, soaking));
    }

    public JugRecipes jugRecipes() {
        return JUG_RECIPES.get();
    }

    public record JugRecipes(
            List<JugFluidFillingRecipe> filling,
            List<JugFluidEmptyingRecipe> emptying,
            List<JugSoakingRecipe> soaking
    ) {
        public JugRecipes {
            filling = List.copyOf(filling == null ? List.of() : filling);
            emptying = List.copyOf(emptying == null ? List.of() : emptying);
            soaking = List.copyOf(soaking == null ? List.of() : soaking);
        }

        static JugRecipes empty() {
            return new JugRecipes(List.of(), List.of(), List.of());
        }
    }

    private boolean matches(CookingRecipe recipe, ItemStack[] inputs) {

        int nonEmptyCount = 0;
        for (ItemStack input : inputs) {
            if (input != null && !input.isEmpty()) nonEmptyCount++;
        }
        if (nonEmptyCount != recipe.ingredients.size()) return false;

        if (inputs.length <= 64) {
            // 位掩码版：零分配（烹饪锅等容器输入槽远小于 64）。
            // 保持初版贪心语义：每个原料取第一个可用匹配槽，不回溯重试。
            return matchesMasked(recipe, inputs, 0L, 0);
        }
        return matchesUsed(recipe, inputs, new boolean[inputs.length], 0);
    }

    private boolean matchesMasked(CookingRecipe recipe, ItemStack[] inputs, long usedMask, int defIndex) {
        if (defIndex == recipe.ingredients.size()) return true;
        IngredientDef ingredient = recipe.ingredients.get(defIndex);
        for (int i = 0; i < inputs.length; i++) {
            if ((usedMask >>> i & 1L) != 0L || inputs[i] == null || inputs[i].isEmpty()) continue;
            if (matchesIngredient(inputs[i], ingredient)) {
                return matchesMasked(recipe, inputs, usedMask | (1L << i), defIndex + 1);
            }
        }
        return false;
    }

    private boolean matchesUsed(CookingRecipe recipe, ItemStack[] inputs, boolean[] used, int defIndex) {
        if (defIndex == recipe.ingredients.size()) return true;
        IngredientDef ingredient = recipe.ingredients.get(defIndex);
        for (int i = 0; i < inputs.length; i++) {
            if (used[i] || inputs[i] == null || inputs[i].isEmpty()) continue;
            if (matchesIngredient(inputs[i], ingredient)) {
                used[i] = true;
                return matchesUsed(recipe, inputs, used, defIndex + 1);
            }
        }
        return false;
    }

    public boolean matchesIngredient(ItemStack stack, IngredientDef def) {
        return def != null && def.matcher().matches(stack, DefaultItemMatcherResolver.INSTANCE);
    }

    public static List<String> getTagItems(String tagName) {
        return TagExpander.expand(Map.of(), tagName);
    }

    private record RecipeSnapshot(List<CookingRecipe> recipes,
                                  RecipeTrie trie,
                                  long epoch) {
        private static RecipeSnapshot create(List<CookingRecipe> recipes) {
            List<CookingRecipe> immutableRecipes = recipes == null ? List.of() : List.copyOf(recipes);
            RecipeTrie trie = new RecipeTrie(new DefaultItemMatcherResolver());
            for (CookingRecipe recipe : immutableRecipes) trie.insert(recipe);
            return new RecipeSnapshot(immutableRecipes, trie, EPOCH_COUNTER.incrementAndGet());
        }
    }

}
