package dev.tako.papersdelight.jug;

import java.util.concurrent.atomic.AtomicInteger;

final class JugDeliveryTransaction {
    interface Scheduler {
        Handle entityLater(Runnable task, Runnable retired, long delay);
        void region(Runnable task, Runnable retired);
    }
    interface Handle { boolean isCancelled(); }
    interface Compensation { void rollback(); void drop(); }

    private final AtomicInteger state = new AtomicInteger();
    private final Scheduler scheduler;
    private final Compensation compensation;

    JugDeliveryTransaction(Scheduler scheduler, Compensation compensation) {
        this.scheduler = scheduler;
        this.compensation = compensation;
    }

    boolean register(Runnable payout, boolean online) {
        Handle handle = scheduler.entityLater(() -> {
            if (!state.compareAndSet(1, 2)) return;
            if (!online) {
                scheduler.region(() -> { compensation.rollback(); compensation.drop(); },
                        () -> compensation.drop());
                return;
            }
            payout.run();
        }, () -> {
            if (state.compareAndSet(0, 2) || state.compareAndSet(1, 2)) scheduler.region(compensation::rollback, compensation::drop);
        }, 1L);
        if (handle == null || handle.isCancelled() || !state.compareAndSet(0, 1)) return false;
        return true;
    }
}
