package dev.tako.papersdelight.jug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JugItemModelGeneratorTest {

    @Test
    void emptyManifestEntryDoesNotDeleteResourcePackRoot(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("assets"));
        Files.writeString(root.resolve(".papersdelight-generated-jug-item-models"), "\n", StandardCharsets.UTF_8);

        Method replaceManifest = JugItemModelGenerator.class
                .getDeclaredMethod("replaceManifest", Path.class, Set.class);
        replaceManifest.setAccessible(true);

        assertDoesNotThrow(() -> replaceManifest.invoke(null, root, Set.of()));
    }
}
