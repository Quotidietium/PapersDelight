package dev.tako.papersdelight.registration.config;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;


public final class ParserGeneration {
    private static final AtomicLong IDS = new AtomicLong();
    private static final ReentrantReadWriteLock COMMIT_LOCK = new ReentrantReadWriteLock(true);

    private final long id = IDS.incrementAndGet();
    private volatile boolean active;

    private ParserGeneration(boolean active) {
        this.active = active;
    }


    public static ParserGeneration candidate() {
        return new ParserGeneration(false);
    }

    public static ParserGeneration active() {
        return new ParserGeneration(true);
    }

    public long id() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        runExclusive(() -> active = true);
    }


    public boolean commitIfActive(Runnable commit) {
        COMMIT_LOCK.readLock().lock();
        try {
            if (!active) return false;
            commit.run();
            return true;
        } finally {
            COMMIT_LOCK.readLock().unlock();
        }
    }


    public static void runExclusive(Runnable action) {
        supplyExclusive(() -> {
            action.run();
            return null;
        });
    }


    public static <T> T supplyExclusive(Supplier<T> action) {
        COMMIT_LOCK.writeLock().lock();
        try {
            return action.get();
        } finally {
            COMMIT_LOCK.writeLock().unlock();
        }
    }

    public void invalidate() {
        runExclusive(() -> active = false);
    }
}
