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
        return anyMatch0(tags, normalizeTag(rootTag), tagPredicate, itemPredicate, new HashSet<>());
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
