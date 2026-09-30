package dev.tako.papersdelight;

import dev.tako.papersdelight.registration.RuntimeConfigHandoff;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.function.Consumer;

final class PapersDelightEnablePhase {

    private PapersDelightEnablePhase() {
    }

    static boolean activate(
            RuntimeConfigHandoff handoff,
            RuntimeConfigHandoff.RuntimeTargets runtimeTargets,
            Consumer<Listener> listenerRegistration
    ) {
        RuntimeConfigHandoff configuredHandoff = Objects.requireNonNull(handoff, "handoff");
        configuredHandoff.attachTargets(Objects.requireNonNull(runtimeTargets, "runtimeTargets"));
        Objects.requireNonNull(listenerRegistration, "listenerRegistration").accept(configuredHandoff);
        return configuredHandoff.applyStartup();
    }
}
