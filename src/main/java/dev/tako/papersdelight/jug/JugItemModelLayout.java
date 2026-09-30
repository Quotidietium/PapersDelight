package dev.tako.papersdelight.jug;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class JugItemModelLayout {
    private static final String OUTPUT_NAMESPACE = "farmersdelight";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.:/-]+");

    private final String namespace;
    private final String modelPrefix;
    private final String texture;
    private final int stage;

    private JugItemModelLayout(String namespace, String modelPrefix, String texture, int stage) {
        this.namespace = namespace;
        this.modelPrefix = modelPrefix;
        this.texture = texture;
        this.stage = stage;
    }

    static JugItemModelLayout of(String modelPrefix, String texture, int stage) {
        Objects.requireNonNull(modelPrefix, "modelPrefix");
        if (!KEY.matcher(modelPrefix).matches() || modelPrefix.contains("..") || modelPrefix.contains("//")) {
            throw new IllegalArgumentException("Invalid Jug model prefix: " + modelPrefix);
        }
        String value = texture == null || texture.isBlank() ? "water" : texture.trim().toLowerCase(Locale.ROOT);
        if (!TEXTURE.matcher(value).matches() || value.contains("..") || value.contains("//")) {
            throw new IllegalArgumentException("Invalid Jug texture: " + texture);
        }
        int separator = modelPrefix.indexOf(':');
        int normalizedStage = Math.min(Math.max(stage, 1), 16);
        return new JugItemModelLayout(modelPrefix.substring(0, separator), modelPrefix, value, normalizedStage);
    }

    String itemDefinition() {
        return OUTPUT_NAMESPACE + ":pd_jug/" + safePath(texture) + "/" + stageName();
    }

    String definitionFile() {
        return "assets/" + OUTPUT_NAMESPACE + "/items/pd_jug/" + safePath(texture) + "/" + stageName() + ".json";
    }

    String definitionJson() {
        String fluidModel = modelPrefix.substring(0, modelPrefix.indexOf(':')) + ":"
                + modelPrefix.substring(modelPrefix.indexOf(':') + 1)
                .substring(0, modelPrefix.substring(modelPrefix.indexOf(':') + 1).lastIndexOf('/') + 1)
                + "glass_jug_fluid_" + safePath(texture) + "_model_" + stageName();
        return """
                {
                  "model": {
                    "type": "minecraft:composite",
                    "models": [
                      {"type": "minecraft:model", "model": "%s:block/glass_jug"},
                      {"type": "minecraft:model", "model": "%s", "tints": [{"type": "dye", "default": 16777215}]}
                    ]
                  }
                }
                """.formatted(namespace, fluidModel);
    }

    private String fluidTexture() {
        if (texture.contains(":")) return texture;
        if (texture.contains("/")) return "minecraft:" + texture;
        return "minecraft:block/" + texture + "_still";
    }

    private String stageName() {
        return String.format(Locale.ROOT, "%02d", stage);
    }

    private static String safePath(String value) {
        return value.replace(':', '_').replace('/', '_');
    }
}
