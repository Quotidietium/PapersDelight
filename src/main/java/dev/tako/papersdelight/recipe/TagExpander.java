package dev.tako.papersdelight.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class TagExpander {

    private TagExpander() {
    }

    public static List<String> expand(Map<String, List<String>> tags, String rootTag) {
        List<String> result = new ArrayList<>();
        expandInto(tags, normalizeTag(rootTag), result, new HashSet<>());
        return result;
    }

    public static boolean anyMatch(
            Map<String, List<String>> tags,
            String rootTag,
            Predicate<String> tagPredicate,
            Predicate<String> itemPredicate
    ) {
        // 空 tags 的等价快路径：anyMatch0 对空 map 只会做 tagPredicate.test(normalize(root))
        // （tags.get 恒为 null、无嵌套递归、visited 首次 add 恒成功），语义完全一致。
        if (tags.isEmpty()) {
            return tagPredicate.test(normalizeTag(rootTag));
        }
        return anyMatch0(tags, normalizeTag(rootTag), tagPredicate, itemPredicate, new HashSet<>(Math.max(2, tags.size())));
    }

    private static void expandInto(Map<String, List<String>> tags, String tagName, List<String> out, Set<String> visited) {
        if (!visited.add(tagName)) return;
        List<String> items = tags.get(tagName);
        if (items == null) return;
        for (String id : items) {
            if (id.startsWith("#")) {
                expandInto(tags, normalizeTag(id.substring(1)), out, visited);
            } else {
                out.add(id);
            }
        }
    }

    private static boolean anyMatch0(
            Map<String, List<String>> tags,
            String tagName,
            Predicate<String> tagPredicate,
            Predicate<String> itemPredicate,
            Set<String> visited
    ) {
        if (!visited.add(tagName)) return false;
        if (tagPredicate.test(tagName)) return true;
        List<String> items = tags.get(tagName);
        if (items == null) return false;
        for (String id : items) {
            if (id.startsWith("#")) {
                if (anyMatch0(tags, normalizeTag(id.substring(1)), tagPredicate, itemPredicate, visited)) {
                    return true;
                }
            } else if (itemPredicate.test(id)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTag(String tagName) {
        return tagName.toLowerCase(Locale.ROOT);
    }
}
