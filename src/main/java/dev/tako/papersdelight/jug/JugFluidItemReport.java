package dev.tako.papersdelight.jug;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class JugFluidItemReport {

    private JugFluidItemReport() {
    }

    @Nullable
    static String resolveDisplayItem(String fluidKey, Predicate<String> exists) {
        for (String candidate : JugFluidItemId.candidates(fluidKey)) {
            if (exists.test(candidate)) return candidate;
        }
        return null;
    }

    static List<String> missingFluids(List<String> fluidKeys, Predicate<String> exists) {
        List<String> missing = new ArrayList<>();
        if (fluidKeys == null) return missing;
        for (String fluidKey : fluidKeys) {
            if (fluidKey == null) continue;
            List<String> candidates = JugFluidItemId.candidates(fluidKey);
            boolean specificExists = false;
            for (int i = 0; i < candidates.size() - 1; i++) {
                if (exists.test(candidates.get(i))) {
                    specificExists = true;
                    break;
                }
            }
            if (!specificExists) missing.add(fluidKey);
        }
        return missing;
    }
}
