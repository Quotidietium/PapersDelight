package dev.tako.papersdelight.registration.config;

import net.momirealms.craftengine.core.util.Key;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdvancedTagSnapshot {

    private static final AdvancedTagSnapshot EMPTY = new AdvancedTagSnapshot(Map.of());

    private final Map<Key, List<Key>> tags;
    private final Map<Key, Set<String>> matchIndex;

    public AdvancedTagSnapshot(Map<Key, List<Key>> tags) {
        LinkedHashMap<Key, List<Key>> copy = new LinkedHashMap<>(tags.size());
        LinkedHashMap<Key, Set<String>> index = new LinkedHashMap<>(tags.size());
        for (Map.Entry<Key, List<Key>> entry : tags.entrySet()) {
            List<Key> members = List.copyOf(entry.getValue());
            copy.put(entry.getKey(), members);

            Set<String> ids = new HashSet<>(members.size());
            for (Key member : members) {
                ids.add(member.asString().toLowerCase(Locale.ROOT));
            }
            index.put(entry.getKey(), Set.copyOf(ids));
        }
        this.tags = Collections.unmodifiableMap(copy);
        this.matchIndex = Collections.unmodifiableMap(index);
    }

    public static AdvancedTagSnapshot empty() {
        return EMPTY;
    }

    public Map<Key, List<Key>> tags() {
        return this.tags;
    }

    public boolean isEmpty() {
        return this.tags.isEmpty();
    }

    public List<Key> resolve(Key id) {
        return this.tags.getOrDefault(id, List.of());
    }

    public boolean containsItem(Key tagId, String itemId) {
        if (tagId == null || itemId == null || itemId.isEmpty()) return false;
        Set<String> members = this.matchIndex.get(tagId);
        if (members == null || members.isEmpty()) return false;
        return members.contains(itemId.toLowerCase(Locale.ROOT));
    }

    public List<String> resolve(String id) {
        return resolve(Key.of(id)).stream().map(Key::asString).toList();
    }
}
