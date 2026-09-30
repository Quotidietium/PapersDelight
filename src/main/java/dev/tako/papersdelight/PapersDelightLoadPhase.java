package dev.tako.papersdelight;

import dev.tako.papersdelight.registration.CraftEngineConfigRegistrations;
import dev.tako.papersdelight.registration.RuntimeConfigHandoff;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class PapersDelightLoadPhase {

    private PapersDelightLoadPhase() {
    }

    static Result initialize(
            Logger logger,
            Supplier<Map<String, CraftEngineConfigRegistrations.RegistrationOutcome>> parserRegistration
    ) {
        return initialize(logger, parserRegistration, RuntimeConfigHandoff.CeSnapshots::current);
    }

    static Result initialize(
            Logger logger,
            Supplier<Map<String, CraftEngineConfigRegistrations.RegistrationOutcome>> parserRegistration,
            Supplier<RuntimeConfigHandoff.CeSnapshots> ceSnapshotSupplier
    ) {
        Objects.requireNonNull(logger, "logger");
        Map<String, CraftEngineConfigRegistrations.RegistrationOutcome> outcomes =
                Collections.unmodifiableMap(new LinkedHashMap<>(
                        Objects.requireNonNull(parserRegistration.get(), "parser registration outcomes")));
        boolean normal = outcomes.size() == CraftEngineConfigRegistrations.sectionIds().size()
                && outcomes.values().stream().allMatch(outcome ->
                outcome == CraftEngineConfigRegistrations.RegistrationOutcome.REGISTERED
                        || outcome == CraftEngineConfigRegistrations.RegistrationOutcome.ALREADY_REGISTERED);
        if (!normal) {
            throw new IllegalStateException("CraftEngine parser registration transaction failed: " + outcomes);
        }
        RuntimeConfigHandoff handoff = new RuntimeConfigHandoff(
                logger, Objects.requireNonNull(ceSnapshotSupplier, "ceSnapshotSupplier"));
        return new Result(outcomes, handoff);
    }

    record Result(
            Map<String, CraftEngineConfigRegistrations.RegistrationOutcome> parserRegistrations,
            RuntimeConfigHandoff handoff
    ) {
    }
}
