package dev.tako.papersdelight.mechanic.skillet;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ItemModelManifest {

    private static final String NAMESPACE = "farmersdelight";
    private static final String ASSET_PREFIX = "assets/farmersdelight/items/handheld_skillet/";
    private static final String DEFINITION_ROOT = "assets/farmersdelight/items/";

    private ItemModelManifest() {
    }

    static Optional<String> definitionFromManifestFile(String generatedFile) {
        if (generatedFile == null
                || !generatedFile.startsWith(ASSET_PREFIX)
                || !generatedFile.endsWith(".json")) {
            return Optional.empty();
        }
        String definitionPath = generatedFile.substring(
                DEFINITION_ROOT.length(),
                generatedFile.length() - ".json".length());
        return Optional.of(NAMESPACE + ":" + definitionPath);
    }

    static Set<String> restoreDefinitions(List<Path> manifests) throws java.io.IOException {
        Set<String> definitions = new LinkedHashSet<>();
        for (Path manifest : manifests) {
            if (!Files.isRegularFile(manifest)) continue;
            for (String generatedFile : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                definitionFromManifestFile(generatedFile).ifPresent(definitions::add);
            }
        }
        return Set.copyOf(definitions);
    }
}
