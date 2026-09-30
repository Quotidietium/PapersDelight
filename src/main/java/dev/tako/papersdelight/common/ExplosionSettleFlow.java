package dev.tako.papersdelight.common;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ExplosionSettleFlow<K, P> {
    public interface FloatSupplier { float get(); }
    private final Map<K, P> pending = new ConcurrentHashMap<>();

    public void settle(boolean cancelled, K key, P value, boolean hasGui, Runnable requestClose, Consumer<P> finish) {
        if (cancelled) return;
        if (hasGui) {
            if (pending.putIfAbsent(key, value) == null) requestClose.run();
            return;
        }
        finish.accept(value);
    }

    public Optional<P> complete(K key, Consumer<P> finish) {
        P value = pending.remove(key);
        if (value == null) return Optional.empty();
        finish.accept(value);
        return Optional.of(value);
    }

    public boolean discard(K key) { return pending.remove(key) != null; }
    public int pendingSize() { return pending.size(); }
    public void clear() { pending.clear(); }

    public static <T> void settleStaged(List<T> staged, boolean cancelled, Consumer<T> settle) {
        if (!cancelled) staged.forEach(settle);
    }

    public static float radius(boolean modern, boolean droppingItems, float yield, FloatSupplier modernRadius) {
        if (!modern) return 1f / yield;
        return droppingItems ? modernRadius.get() : Float.POSITIVE_INFINITY;
    }

    public static boolean survives(float radius, float randomValue) { return randomValue < 1f / radius; }
}
