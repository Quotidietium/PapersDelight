package dev.tako.papersdelight.cookingpot;

import dev.tako.papersdelight.recipe.CookingRecipe;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CookingPotRecipeCache {

    private static final int INGREDIENT_SLOTS = 6;

    public sealed interface CacheResult {
        CacheResult MISS = Miss.INSTANCE;

        static CacheResult hit(@Nullable CookingRecipe recipe) {
            return new Hit(recipe);
        }

        record Hit(@Nullable CookingRecipe recipe) implements CacheResult {}
        enum Miss implements CacheResult { INSTANCE }
    }

    private static final CookingRecipe NO_MATCH_SENTINEL = new CookingRecipe(
            "sentinel", "sentinel:no_match", 0, null, 0, 0, List.of(), "sentinel");

    private record Entry(long fingerprint, long epoch, CookingRecipe recipe) {}

    private final Map<Location, Entry> entries = new ConcurrentHashMap<>();

    public CacheResult get(Location loc, ItemStack[] inputs, long currentEpoch) {
        return lookup(loc, fingerprint(inputs), currentEpoch);
    }

    /** 指纹已由调用方算好的查询入口（get 的零行为差异拆分，基准可直接度量缓存门成本） */
    CacheResult lookup(Location loc, long fingerprint, long currentEpoch) {
        Entry entry = entries.get(loc);
        if (entry == null) return CacheResult.MISS;
        if (entry.epoch != currentEpoch) return CacheResult.MISS;
        if (entry.fingerprint != fingerprint) return CacheResult.MISS;
        CookingRecipe r = entry.recipe;
        return CacheResult.hit(r == NO_MATCH_SENTINEL ? null : r);
    }

    public void put(Location loc, ItemStack[] inputs, long currentEpoch,
                    @Nullable CookingRecipe recipe) {
        insert(loc, fingerprint(inputs), currentEpoch, recipe);
    }

    /** 指纹已由调用方算好的写入入口（put 的零行为差异拆分） */
    void insert(Location loc, long fingerprint, long currentEpoch,
                @Nullable CookingRecipe recipe) {
        entries.put(loc, new Entry(fingerprint, currentEpoch,
                recipe == null ? NO_MATCH_SENTINEL : recipe));
    }

    public void remove(Location loc) {
        entries.remove(loc);
    }

    public void clear() {
        entries.clear();
    }

    static long fingerprint(ItemStack[] inputs) {
        long fp = 0;
        for (int i = 0; i < INGREDIENT_SLOTS && i < inputs.length; i++) {
            ItemStack item = inputs[i];
            if (item == null || item.isEmpty()) continue;
            fp = fp * 31 + (long) item.getType().ordinal() * 127
                    + item.getAmount() + i * 8191L;
        }
        return fp;
    }
}
