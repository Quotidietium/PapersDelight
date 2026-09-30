package dev.tako.papersdelight.common;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ExplosionStaging<E, T> {
    private final Map<E, List<T>> entries = new IdentityHashMap<>();

    public synchronized void stage(E event, T entry) {
        entries.computeIfAbsent(event, ignored -> new ArrayList<>()).add(entry);
    }

    public synchronized List<T> drain(E event) {
        List<T> staged = entries.remove(event);
        return staged == null ? List.of() : List.copyOf(staged);
    }
}
