package dev.tako.papersdelight.jug;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record JugDiagnosticsReport(
        boolean libuidAvailable,
        boolean runtimeInstalled,
        int filling,
        int emptying,
        int soaking,
        List<FluidEntry> fluids,
        List<String> missingFluids
) {

    public JugDiagnosticsReport {
        fluids = fluids == null ? List.of() : List.copyOf(fluids);
        missingFluids = missingFluids == null ? List.of() : List.copyOf(missingFluids);
    }

    public int totalRecipes() {
        return filling + emptying + soaking;
    }

    public record FluidEntry(String fluidKey, @Nullable String displayItemId) {
    }
}
