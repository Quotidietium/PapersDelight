package dev.tako.papersdelight.recipe;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;

import java.util.ArrayList;
import java.util.List;

/**
 * RecipeTrie 配方树基准：真实 insert + 真实（包私有）findMatch(List, resolver)。
 * 输入集合形状模拟烹饪锅 6 输入槽的真实负载：
 *  - 64 条配方，原料数 1..6，匹配器混合 item-id / tag / anyOf
 *  - 含 stableKey 重复的配方对（验证“首个插入胜出”语义不变）
 *  - 查询混合：命中（乱序输入）/ 数量错配 miss / 全 miss
 */
public final class RecipeTrieBench {

    static final int RECIPES = 64;
    static final int POOL = 16;
    /** 唯一锚点物品数：bench:i0..bench:i{ANCHORS-1}，每条配方携带一个专属锚点保证 stableKey 集合唯一。 */
    static final int ANCHORS = 80;

    static CookingRecipe recipe(int idx, List<IngredientDef> defs) {
        return new CookingRecipe("bench_cooking", "bench:meal" + idx, 1, "minecraft:bowl",
                200, 1.0f, defs, "bench-src-" + idx);
    }

    static IngredientDef itemDef(int i) {
        return new IngredientDef(null, null, "bench:i" + i);
    }

    static IngredientDef tagDef(int t) {
        return new IngredientDef(null, "#bench:t" + t, null);
    }

    /** 构建与 MatcherCostBench 相同语义的 resolver（表驱动、确定性）。 */
    static MatcherCostBench.IntResolver buildResolver() {
        return MatcherCostBench.buildResolver();
    }

    /** 构建 fixture：返回 [trie, hitQueries, wrongCountQueries, missQueries]。 */
    static List<Object[]> buildFixture() {
        MatcherCostBench.IntResolver resolver = buildResolver();
        RecipeTrie trie = new RecipeTrie();

        List<List<Integer>> hitQueries = new ArrayList<>();
        List<List<Integer>> wrongCountQueries = new ArrayList<>();
        List<List<Integer>> missQueries = new ArrayList<>();
        java.util.Random random = new java.util.Random(20261002L);

        for (int r = 0; r < RECIPES; r++) {
            int size = 1 + (r % 6);
            List<IngredientDef> defs = new ArrayList<>(size);
            List<Integer> inputs = new ArrayList<>(size);

            // 锚点原料：bench:i{r} 为本配方专属，保证 stableKey 集合在配方间唯一
            defs.add(itemDef(r));
            inputs.add(r);

            for (int k = 1; k < size; k++) {
                if ((r + k) % 4 == 0) {
                    int tag = (r + k) % 6;
                    defs.add(tagDef(tag));
                    inputs.add((tag + k + 1) % 6 + tag);   // t{tag} 的成员
                } else if ((r + k) % 5 == 0) {
                    int slot = (r * 3 + k) % POOL;
                    defs.add(new IngredientDef(null, null, null,
                            List.of("bench:i" + slot, "#bench:t" + (slot % 6))));
                    inputs.add(slot);
                } else {
                    int slot = (r * 3 + k) % POOL;
                    defs.add(itemDef(slot));
                    inputs.add(slot);
                }
            }
            trie.insert(recipe(r, defs));

            // 命中查询：打乱输入顺序（树必须做无序多重集匹配）
            List<Integer> shuffled = new ArrayList<>(inputs);
            java.util.Collections.shuffle(shuffled, random);
            hitQueries.add(shuffled);

            // 数量错配：多塞一个
            if (size < 6) {
                List<Integer> wrong = new ArrayList<>(inputs);
                wrong.add((r + 11) % ANCHORS);
                wrongCountQueries.add(wrong);
            }
            // 全 miss：原料均不在任何物品/标签表内
            missQueries.add(java.util.List.of(100 + (r % 5)));
        }

        // stableKey 重复对：同 matcher 的两条配方，先插者胜
        List<IngredientDef> dupDefs = List.of(itemDef(9), tagDef(2));
        trie.insert(recipe(9000, dupDefs));
        trie.insert(recipe(9001, dupDefs));
        hitQueries.add(List.of(9, 3));   // t2 的成员 → 命中 9000

        List<Object[]> fixture = new ArrayList<>();
        fixture.add(new Object[]{trie});
        fixture.add(new Object[]{hitQueries});
        fixture.add(new Object[]{wrongCountQueries});
        fixture.add(new Object[]{missQueries});
        fixture.add(new Object[]{resolver});
        return fixture;
    }

    @SuppressWarnings("unchecked")
    public static void run(List<Result> results) {
        selfCheck();

        List<Object[]> fixture = buildFixture();
        RecipeTrie trie = (RecipeTrie) fixture.get(0)[0];
        List<List<Integer>> hits = (List<List<Integer>>) (List<?>) fixture.get(1)[0];
        List<List<Integer>> wrongs = (List<List<Integer>>) (List<?>) fixture.get(2)[0];
        List<List<Integer>> misses = (List<List<Integer>>) (List<?>) fixture.get(3)[0];
        MatcherCostBench.IntResolver resolver = (MatcherCostBench.IntResolver) fixture.get(4)[0];

        final int hitN = hits.size();
        results.add(Bench.measure("recipeTrie.findMatch[hits,shuffled]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(trie.findMatch(hits.get(i % hitN), resolver) != null);
            }
        }));
        final int wrongN = wrongs.size();
        results.add(Bench.measure("recipeTrie.findMatch[wrong-count,miss]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(trie.findMatch(wrongs.get(i % wrongN), resolver) != null);
            }
        }));
        final int missN = misses.size();
        results.add(Bench.measure("recipeTrie.findMatch[all-miss]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(trie.findMatch(misses.get(i % missN), resolver) != null);
            }
        }));
        results.add(Bench.measure("recipeTrie.findMatch[mixed-workload]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                int m = i % 10;
                List<Integer> q;
                if (m < 5) q = hits.get(i % hitN);
                else if (m < 8) q = wrongs.get(i % wrongN);
                else q = misses.get(i % missN);
                bh.consume(trie.findMatch(q, resolver) != null);
            }
        }));
    }

    private static void selfCheck() {
        List<Object[]> fixture = buildFixture();
        RecipeTrie trie = (RecipeTrie) fixture.get(0)[0];
        MatcherCostBench.IntResolver resolver = (MatcherCostBench.IntResolver) fixture.get(4)[0];

        // stableKey 重复：首个插入（9000）胜出
        CookingRecipe winner = trie.findMatch(List.of(9, 3), resolver);
        Bench.check(winner != null && "bench:meal9000".equals(winner.result),
                "duplicate stableKey: first insert wins, got " + (winner == null ? "null" : winner.result));
        // 无序性
        CookingRecipe direct = trie.findMatch(List.of(3, 9), resolver);
        Bench.check(direct != null && "bench:meal9000".equals(direct.result), "order-insensitive match");
        // 数量错配 → null
        Bench.check(trie.findMatch(List.of(77), resolver) == null, "unknown count bucket miss");
        // 空输入 → null
        Bench.check(trie.findMatch(List.of(), resolver) == null, "empty inputs");
        // 规模校验（重复 stableKey 的第二条不重复计数）
        Bench.check(trie.size() == RECIPES + 1, "trie size = " + trie.size());
    }

    private RecipeTrieBench() {}
}
