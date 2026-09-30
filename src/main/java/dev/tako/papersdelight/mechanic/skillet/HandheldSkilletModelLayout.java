package dev.tako.papersdelight.mechanic.skillet;

import java.util.Objects;
import java.util.regex.Pattern;

final class HandheldSkilletModelLayout {
    private static final String OUTPUT_NAMESPACE = "farmersdelight";
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private final String sourceNamespace;
    private final String sourcePath;

    private HandheldSkilletModelLayout(String sourceNamespace, String sourcePath) {
        this.sourceNamespace = sourceNamespace;
        this.sourcePath = sourcePath;
    }

    static HandheldSkilletModelLayout fromItemModel(String itemModel) {
        Objects.requireNonNull(itemModel, "itemModel");
        int separator = itemModel.indexOf(':');
        if (separator <= 0 || separator != itemModel.lastIndexOf(':')) {
            throw new IllegalArgumentException("Invalid item model key: " + itemModel);
        }
        String namespace = itemModel.substring(0, separator);
        String path = itemModel.substring(separator + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()
                || path.startsWith("/") || path.contains("//") || path.contains("..")) {
            throw new IllegalArgumentException("Unsafe item model key: " + itemModel);
        }
        return new HandheldSkilletModelLayout(namespace, path.startsWith("item/") ? path.substring(5) : path);
    }

    String parentModel() {
        return sourceNamespace + ":item/" + sourcePath;
    }

    String itemDefinition() {
        return OUTPUT_NAMESPACE + ":handheld_skillet/ingredients/" + sourceNamespace + "/" + sourcePath;
    }

    String flippedItemDefinition() {
        return OUTPUT_NAMESPACE + ":handheld_skillet/flipped/" + sourceNamespace + "/" + sourcePath;
    }

    String derivedModel() {
        return OUTPUT_NAMESPACE + ":item/handheld_skillet/ingredients/" + sourceNamespace + "/" + sourcePath;
    }

    String flippedDerivedModel() {
        return OUTPUT_NAMESPACE + ":item/handheld_skillet/flipped/" + sourceNamespace + "/" + sourcePath;
    }

    String modelFile() {
        return "assets/" + OUTPUT_NAMESPACE + "/models/item/handheld_skillet/ingredients/" + sourceNamespace + "/" + sourcePath + ".json";
    }

    String flippedModelFile() {
        return "assets/" + OUTPUT_NAMESPACE + "/models/item/handheld_skillet/flipped/" + sourceNamespace + "/" + sourcePath + ".json";
    }

    String definitionFile() {
        return "assets/" + OUTPUT_NAMESPACE + "/items/handheld_skillet/ingredients/" + sourceNamespace + "/" + sourcePath + ".json";
    }

    String flippedDefinitionFile() {
        return "assets/" + OUTPUT_NAMESPACE + "/items/handheld_skillet/flipped/" + sourceNamespace + "/" + sourcePath + ".json";
    }

    String modelJson() {
        return """
                {
                  "parent": "%s",
                  "elements": [{
                    "name": "ingredient_placeholder",
                    "from": [3.999, 1.079, 3.999],
                    "to": [12.001, 2.081, 12.001],
                    "rotation": {"angle": 180, "axis": "y", "origin": [8, 8, 8]},
                    "faces": {
                      "north": {"uv": [0, 0, 16, 1], "texture": "#layer0"},
                      "east": {"uv": [0, 0, 1, 16], "texture": "#layer0"},
                      "south": {"uv": [0, 0, 16, 1], "texture": "#layer0"},
                      "west": {"uv": [0, 0, 1, 16], "texture": "#layer0"},
                      "up": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
                      "down": {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                    }
                  }],
                  "display": {
                    "thirdperson_righthand": {"rotation": [70, 0, 0], "translation": [0, 13, 1.5], "scale": [0.85, 0.85, 0.85]},
                    "thirdperson_lefthand": {"rotation": [70, 0, 0], "translation": [0, 13, 1.5], "scale": [0.85, 0.85, 0.85]},
                    "firstperson_righthand": {"translation": [0, 5, -7], "scale": [0.9, 0.9, 0.9]},
                    "firstperson_lefthand": {"translation": [0, 5, -7], "scale": [0.9, 0.9, 0.9]},
                    "ground": {"scale": [0.25, 0.25, 0.25]},
                    "gui": {"rotation": [31, -38, 0], "translation": [1, 3.75, 0], "scale": [0.6, 0.6, 0.6]},
                    "head": {"rotation": [-180, 90, 0], "scale": [1.1, 1.1, 1.1]},
                    "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -6.25], "scale": [0.75, 0.75, 0.75]}
                  }
                }
                """.formatted(parentModel());
    }

    String flippedModelJson() {
        return modelJson().replaceFirst("(?m)^\\s*\\\"rotation\\\": \\{\\\"angle\\\": 180, \\\"axis\\\": \\\"y\\\", \\\"origin\\\": \\[8, 8, 8]},\\R", "");
    }

    String definitionJson() {
        return definitionJson(derivedModel());
    }

    String flippedDefinitionJson() {
        return definitionJson(flippedDerivedModel());
    }

    private static String definitionJson(String model) {
        return """
                {
                  "model": {
                    "type": "minecraft:composite",
                    "models": [
                      {"type": "minecraft:model", "model": "farmersdelight:item/skillet_cooking"},
                      {"type": "minecraft:model", "model": "%s"}
                    ]
                  }
                }
                """.formatted(model);
    }
}
