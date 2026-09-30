package dev.tako.papersdelight.common;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MenuCloseFlow {
    private MenuCloseFlow() { }

    public interface Scheduler {
        void runEntity(Runnable task);
        void runRegion(Runnable task);
    }

    public static void requestOwnerClose(Scheduler scheduler, BooleanSupplier isCurrentSession,
            BooleanSupplier ownsCurrentTopInventory, Runnable closeInventory) {
        scheduler.runEntity(() -> {
            if (!isCurrentSession.getAsBoolean() || !ownsCurrentTopInventory.getAsBoolean()) return;
            closeInventory.run();
        });
    }

    public static <T> void persistInput(Scheduler scheduler, BooleanSupplier isCurrentSession,
            Supplier<T> readInput, Consumer<T> writeController, Runnable releaseSession) {
        T input = readInput.get();
        scheduler.runRegion(() -> {
            if (!isCurrentSession.getAsBoolean()) return;
            writeController.accept(input);
            releaseSession.run();
        });
    }
}
