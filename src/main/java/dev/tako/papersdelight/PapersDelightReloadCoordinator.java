package dev.tako.papersdelight;

import dev.tako.papersdelight.registration.RuntimeConfigHandoff;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class PapersDelightReloadCoordinator {

    private final Runnable configReload;
    private final RuntimeConfigHandoff handoff;
    private final PapersDelightRuntimeReload runtimeReload;
    private final Supplier<Runnable> stateRollbackCapture;

    PapersDelightReloadCoordinator(
            Runnable configReload,
            RuntimeConfigHandoff handoff
    ) {
        this(configReload, handoff, null, () -> () -> { });
    }

    PapersDelightReloadCoordinator(
            Runnable configReload,
            RuntimeConfigHandoff handoff,
            PapersDelightRuntimeReload runtimeReload
    ) {
        this(configReload, handoff, runtimeReload, () -> () -> { });
    }

    PapersDelightReloadCoordinator(
            Runnable configReload,
            RuntimeConfigHandoff handoff,
            PapersDelightRuntimeReload runtimeReload,
            Supplier<Runnable> stateRollbackCapture
    ) {
        this.configReload = Objects.requireNonNull(configReload, "configReload");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.runtimeReload = runtimeReload;
        this.stateRollbackCapture = Objects.requireNonNull(stateRollbackCapture, "stateRollbackCapture");
    }

    boolean reload() {
        Runnable stateRollback = runOnce(Objects.requireNonNull(
                stateRollbackCapture.get(), "stateRollbackCapture returned null"));
        try {
            configReload.run();
            if (!handoff.reapplyAccepted()) {
                throw new IllegalStateException("Runtime configuration handoff failed; previous runtime remains active");
            }
            if (runtimeReload != null) runtimeReload.commit(stateRollback);
            return true;
        } catch (Throwable failure) {
            rollbackState(stateRollback, failure);
            if (failure instanceof RuntimeException runtimeException) throw runtimeException;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Runtime reload failed", failure);
        }
    }

    private static Runnable runOnce(Runnable rollback) {
        AtomicBoolean restored = new AtomicBoolean();
        return () -> {
            if (restored.compareAndSet(false, true)) rollback.run();
        };
    }

    private static void rollbackState(Runnable rollback, Throwable failure) {
        try {
            rollback.run();
        } catch (Throwable rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

}
