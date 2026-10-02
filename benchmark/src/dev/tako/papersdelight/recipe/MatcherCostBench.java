package dev.tako.papersdelight.recipe;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;
import dev.tako.papersdelight.api.item.ItemMatcherResolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单次原料匹配开销基准：真实 IngredientDef + 真实 ItemMatcher（papersdelight-api）+ 受控 resolver。
 * 这是 RecipeTrie DFS 与线性回退共用的最小匹配原语，也是 R1 优化降低调用次数的对象。
 * 使用 Integer 作为物品类型，保证在无服务器环境下可运行、且在 baseline/candidate 两个 jar 上完全可比。
 */
public final class MatcherCostBench {

    /** 受控 resolver：与 DefaultItemMatcherResolver 相同的三方法协议，匹配逻辑为确定性查表。 */
    static final class IntResolver implements ItemMatcherResolver<Integer> {
        final Map<String, Integer> items = new HashMap<>();
        final Map<String, Set<Integer>> tags = new HashMap<>();

        @Override
        public boolean matchesItem(Integer item, String itemId) {
            return item != null && itemId != null && Integer.valueOf(item).equals(items.get(itemId));
        }

        @Override
        public boolean matchesTag(Integer item, String tagId) {
            if (item == null || tagId == null) return false;
            Set<Integer> members = tags.get(tagId);
            return members != null && members.contains(item);
        }

        @Override
        public boolean matchesAdvancedTag(Integer item, String tagId) {
            return matchesTag(item, "adv$" + tagId);
        }
    }

    static IntResolver buildResolver() {
        IntResolver r = new IntResolver();
        for (int i = 0; i < 80; i++) r.items.put("bench:i" + i, i);
        for (int t = 0; t < 6; t++) {
            Set<Integer> members = new HashSet<>();
            for (int i = t; i < t + 6; i++) members.add(i % 16);
            r.tags.put("bench:t" + t, members);
            r.tags.put("adv$bench:a" + t, members);
        }
        return r;
    }

    public static void run(List<Result> results) {
        selfCheck();

        IntResolver resolver = buildResolver();
        IngredientDef itemDef = new IngredientDef(null, null, "bench:i7");
        IngredientDef tagDef = new IngredientDef(null, "#bench:t3", null);
        IngredientDef anyDef = new IngredientDef(null, null, null, List.of("bench:i3", "#bench:t1", "bench:miss"));

        results.add(Bench.measure("matcher.cost[item-id,hit]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(itemDef.matcher().matches(7, resolver));
            }
        }));
        results.add(Bench.measure("matcher.cost[item-id,miss]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(itemDef.matcher().matches((i & 15) == 7 ? 9 : (i & 15), resolver));
            }
        }));
        results.add(Bench.measure("matcher.cost[tag,hit]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(tagDef.matcher().matches(4, resolver));
            }
        }));
        results.add(Bench.measure("matcher.cost[anyOf3,mixed]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                int item = i & 15;
                bh.consume(anyDef.matcher().matches(item, resolver));
            }
        }));
    }

    private static void selfCheck() {
        IntResolver resolver = buildResolver();
        IngredientDef itemDef = new IngredientDef(null, null, "bench:i7");
        IngredientDef tagDef = new IngredientDef(null, "#bench:t3", null);
        IngredientDef anyDef = new IngredientDef(null, null, null, List.of("bench:i3", "#bench:t1", "bench:miss"));

        Bench.check(itemDef.matcher().matches(7, resolver), "item-id hit");
        Bench.check(!itemDef.matcher().matches(8, resolver), "item-id miss");
        Bench.check(tagDef.matcher().matches(4, resolver), "tag hit (t3 contains 4)");
        Bench.check(!tagDef.matcher().matches(15, resolver), "tag miss");
        Bench.check(anyDef.matcher().matches(3, resolver), "anyOf item hit");
        Bench.check(anyDef.matcher().matches(1, resolver), "anyOf tag hit (t1 contains 1)");
        Bench.check(!anyDef.matcher().matches(15, resolver), "anyOf miss");
        // stableKey 语义：相等定义产生相等 key，不同定义产生不同 key（不假定具体格式）
        IngredientDef itemDefAgain = new IngredientDef(null, null, "bench:i7");
        Bench.check(itemDef.matcher().stableKey().equals(itemDefAgain.matcher().stableKey()),
                "stableKey stable for equal defs");
        Bench.check(!itemDef.matcher().stableKey().equals(tagDef.matcher().stableKey()),
                "stableKey differs across defs");
        Bench.check(!itemDef.matcher().stableKey().isEmpty(), "stableKey non-empty");
    }

    private MatcherCostBench() {}
}
