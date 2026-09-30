package dev.tako.papersdelight.recipe;

import java.util.List;

public sealed interface CustomRecipe {

    record Decomposition(String input, String output, List<String> catalysts) implements CustomRecipe {
        public Decomposition {
            catalysts = catalysts == null ? List.of() : List.copyOf(catalysts);
        }
    }

    record Single(String item, List<String> description) implements CustomRecipe {
        public Single {
            description = description == null ? List.of() : List.copyOf(description);
        }
    }
}
