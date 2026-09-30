package dev.tako.papersdelight.jug;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.Inventory;

final class JugMenuSessionRegistry<K> {
    static final long NO_GENERATION = -1L;

    private final Map<K, Session> sessions = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong generations = new java.util.concurrent.atomic.AtomicLong();

    UUID tryOpen(K key, UUID playerId) {
        Session created = new Session(playerId, generations.incrementAndGet());
        Session existing = sessions.putIfAbsent(key, created);
        return existing == null || existing.playerId.equals(playerId) ? null : existing.playerId;
    }

    long generation(K key, UUID playerId) {
        Session session = sessions.get(key);
        return session != null && session.playerId.equals(playerId) ? session.generation : NO_GENERATION;
    }

    boolean isCurrentGeneration(K key, UUID playerId, long generation) {
        return generation != NO_GENERATION && generation(key, playerId) == generation;
    }

    boolean isOwner(K key, UUID playerId) {
        Session session = sessions.get(key);
        return session != null && session.playerId.equals(playerId);
    }

    UUID owner(K key) {
        Session session = sessions.get(key);
        return session == null ? null : session.playerId;
    }

    boolean close(K key, UUID playerId) {
        Session session = sessions.get(key);
        return session != null && session.playerId.equals(playerId) && sessions.remove(key, session);
    }

    boolean requestClose(K key, UUID playerId) {
        Session session = sessions.get(key);
        if (session == null || !session.playerId.equals(playerId)) return false;
        synchronized (session) {
            if (session.closeRequested) return false;
            session.closeRequested = true;
            return true;
        }
    }

    void setInventory(K key, UUID playerId, Inventory inventory) {
        Session session = sessions.get(key);
        if (session != null && session.playerId.equals(playerId)) session.inventory = inventory;
    }

    Inventory inventory(K key, UUID playerId) {
        Session session = sessions.get(key);
        return session != null && session.playerId.equals(playerId) ? session.inventory : null;
    }

    boolean hasInventory(K key, UUID playerId, Inventory inventory) {
        return inventory != null && inventory == inventory(key, playerId);
    }

    void skipNextInputRead(K key) {
        Session session = sessions.get(key);
        if (session != null) session.skipNextInputRead = true;
    }

    boolean consumeSkipNextInputRead(K key) {
        Session session = sessions.get(key);
        if (session == null || !session.skipNextInputRead) return false;
        session.skipNextInputRead = false;
        return true;
    }

    void clearSkipNextInputRead(K key) {
        Session session = sessions.get(key);
        if (session != null) session.skipNextInputRead = false;
    }

    void clear() {
        sessions.clear();
    }

    private static final class Session {
        private final UUID playerId;
        private final long generation;
        private volatile Inventory inventory;
        private volatile boolean skipNextInputRead;
        private boolean closeRequested;

        private Session(UUID playerId, long generation) {
            this.playerId = playerId;
            this.generation = generation;
        }
    }
}
