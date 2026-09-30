package dev.tako.papersdelight.mechanic.skillet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemModelManifestTest {

    @Test
    void parsesDefinitionFromManifestLine() {
        var definition = ItemModelManifest.definitionFromManifestFile(
                "assets/farmersdelight/items/handheld_skillet/beef.json");

        assertTrue(definition.isPresent(), "合法清单行应解析出定义");
        assertEquals("farmersdelight:handheld_skillet/beef", definition.get());
    }

    @Test
    void ignoresLinesOutsideSkilletAssets() {
        assertTrue(ItemModelManifest.definitionFromManifestFile(
                "assets/farmersdelight/items/other/thing.json").isEmpty(),
                "非煎锅资源路径不应被计入映射");
    }

    @Test
    void ignoresNonJsonLines() {
        assertTrue(ItemModelManifest.definitionFromManifestFile(
                "assets/farmersdelight/items/handheld_skillet/beef.png").isEmpty(),
                "非 json 行不应被计入映射");
    }

    @Test
    void ignoresNullLine() {
        assertTrue(ItemModelManifest.definitionFromManifestFile(null).isEmpty(),
                "空行不应导致解析抛异常");
    }

    @Test
    void restoresDefinitionsFromManifestFile(@TempDir Path dir) throws Exception {
        Path manifest = dir.resolve("manifest");
        Files.write(manifest, List.of(
                "assets/farmersdelight/items/handheld_skillet/beef.json",
                "assets/farmersdelight/items/handheld_skillet/beef_flipped.json",
                "assets/farmersdelight/items/handheld_skillet/fallback.json"
        ), StandardCharsets.UTF_8);

        Set<String> restored = ItemModelManifest.restoreDefinitions(List.of(manifest));

        assertEquals(3, restored.size(), "清单中的全部煎锅定义都应被恢复");
        assertTrue(restored.contains("farmersdelight:handheld_skillet/beef"));
        assertTrue(restored.contains("farmersdelight:handheld_skillet/beef_flipped"));
    }

    @Test
    void mergesMultipleManifests(@TempDir Path dir) throws Exception {
        Path current = dir.resolve("current");
        Path legacy = dir.resolve("legacy");
        Files.write(current, List.of(
                "assets/farmersdelight/items/handheld_skillet/beef.json"), StandardCharsets.UTF_8);
        Files.write(legacy, List.of(
                "assets/farmersdelight/items/handheld_skillet/chicken.json"), StandardCharsets.UTF_8);

        Set<String> restored = ItemModelManifest.restoreDefinitions(List.of(current, legacy));

        assertEquals(2, restored.size(), "新旧清单的定义应合并");
    }

    @Test
    void toleratesMissingManifest(@TempDir Path dir) throws Exception {
        Set<String> restored = ItemModelManifest.restoreDefinitions(
                List.of(dir.resolve("absent")));

        assertTrue(restored.isEmpty(), "清单缺失时应返回空集合而非抛异常");
    }

    @Test
    void restoredSetIsNotEmptyForValidManifest(@TempDir Path dir) throws Exception {
        Path manifest = dir.resolve("manifest");
        Files.write(manifest, List.of(
                "assets/farmersdelight/items/handheld_skillet/beef.json"), StandardCharsets.UTF_8);

        assertFalse(ItemModelManifest.restoreDefinitions(List.of(manifest)).isEmpty(),
                "存在有效清单时映射不应为空，否则煎锅模型会全部回退 fallback");
    }
}
