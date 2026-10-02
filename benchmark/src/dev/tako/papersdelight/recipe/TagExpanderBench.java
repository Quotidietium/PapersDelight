package dev.tako.papersdelight.recipe;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Blackhole;
import dev.tako.papersdelight.bench.Bench.Result;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * TagExpander benchmark.
 * Runtime hot path (recipe ingredient tag matching): anyMatch with empty tags map,
 * results are entirely determined by tagPredicate —— this is exactly the actual call shape of DefaultItemMatcherResolver.matchesTag.
 * Parsing path: anyMatch with nested tags map (cycle-terminated).
 */
public final class TagExpanderBench {

    public static void run(List<Result> results) {
        selfCheck();

        // Runtime hot path: empty map + tag predicate does one string comparison
        Map<String, List<String>> empty = Map.of();
        results.add(Bench.measure("tagExpander.anyMatch[empty-map,tag-miss]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TagExpander.anyMatch(empty, "minecraft:planks_" + (i & 7),
                        t -> t.equals("minecraft:planks_3"), it -> false));
            }
        }));
        results.add(Bench.measure("tagExpander.anyMatch[empty-map,tag-hit]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TagExpander.anyMatch(empty, "minecraft:planks",
                        t -> t.equals("minecraft:planks"), it -> false));
            }
        }));

        // Parse path: nested tags map (2-level nesting + cycle reference)
        Map<String, List<String>> tags = new HashMap<>();
        tags.put("fd:raw_veggies", List.of("minecraft:carrot", "minecraft:potato", "#fd:extra"));
        tags.put("fd:extra", List.of("fd:onion", "#fd:raw_veggies"));   // cycle
        tags.put("fd:tools", List.of("fd:iron_knife", "fd:golden_knife"));
        results.add(Bench.measure("tagExpander.anyMatch[nested-map,item-hit]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TagExpander.anyMatch(tags, "fd:raw_veggies",
                        t -> false, it -> it.equals("fd:onion")));
            }
        }));
        results.add(Bench.measure("tagExpander.expand[nested-map,6-items]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                List<String> out = TagExpander.expand(tags, "fd:raw_veggies");
                bh.consume(out.size());
            }
        }));
    }

    private static void selfCheck() {
        Map<String, List<String>> empty = Map.of();
        // Empty map: only tagPredicate participates (identical to the semantics of baseline anyMatch0)
        Bench.check(TagExpander.anyMatch(empty, "a:b", t -> true, it -> true), "empty-map tag-hit");
        Bench.check(!TagExpander.anyMatch(empty, "a:b", t -> false, it -> true), "empty-map must not match item");
        // Normalization: uppercase input is equivalent to lowercase
        Bench.check(TagExpander.anyMatch(empty, "A:B", t -> t.equals("a:b"), it -> false), "normalize lowercase");

        Map<String, List<String>> tags = new HashMap<>();
        tags.put("fd:g1", List.of("x:1", "#fd:g2"));
        tags.put("fd:g2", List.of("x:2", "#fd:g1"));  // cycle
        Bench.check(TagExpander.anyMatch(tags, "fd:g1", t -> false, it -> it.equals("x:2")),
                "nested item-hit across cycle");
        Bench.check(!TagExpander.anyMatch(tags, "fd:g1", t -> false, it -> it.equals("nope")),
                "nested miss");
        List<String> expanded = new ArrayList<>(TagExpander.expand(tags, "fd:g1"));
        Bench.check(expanded.size() == 2 && expanded.contains("x:1") && expanded.contains("x:2"),
                "expand dedupes across cycle: " + expanded);
    }

    private TagExpanderBench() {}
}
