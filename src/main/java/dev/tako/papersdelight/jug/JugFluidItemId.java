package dev.tako.papersdelight.jug;

import java.util.List;
import java.util.Locale;

final class JugFluidItemId {

    static final String NAMESPACE = "farmersdelight";

    static final String PREFIX = "jug_fluid";

    static final String GENERIC = NAMESPACE + ":" + PREFIX;

    private JugFluidItemId() {
    }

    static List<String> candidates(String fluidKey) {
        String sanitized = sanitize(fluidKey);
        if (sanitized == null) return List.of(GENERIC);
        String[] parts = splitNsPath(sanitized);
        if (parts == null) return List.of(GENERIC);

        String namespace = parts[0];
        String path = parts[1];
        String full = NAMESPACE + ":" + PREFIX + "_" + namespace + "_" + path;
        String shortName = NAMESPACE + ":" + PREFIX + "_" + path;

        if (full.equals(shortName)) return List.of(full, GENERIC);
        return List.of(full, shortName, GENERIC);
    }

    static List<String> candidatesForTexture(String texture) {
        if (texture == null || texture.isBlank()) return List.of(NAMESPACE + ":" + PREFIX + "_water");
        String value = texture.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("(?:[a-z0-9._-]+:)?[a-z0-9._/-]+")) {
            return List.of(NAMESPACE + ":" + PREFIX + "_water");
        }
        String water = NAMESPACE + ":" + PREFIX + "_water";
        if (!value.contains(":")) return List.of(NAMESPACE + ":" + PREFIX + "_" + value.replace('/', '_'), water);
        String[] parts = splitNsPath(value);
        if (parts == null) return List.of(water);
        String shortName = NAMESPACE + ":" + PREFIX + "_" + parts[1];
        String full = NAMESPACE + ":" + PREFIX + "_" + parts[0] + "_" + parts[1];
        if (full.equals(shortName)) return List.of(full, water);
        return List.of(full, shortName, water);
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == ':' || c == '/';
            sb.append(legal ? c : '_');
        }
        return sb.toString();
    }

    private static String[] splitNsPath(String sanitized) {
        int sep = sanitized.indexOf(':');
        if (sep <= 0 || sep == sanitized.length() - 1) return null;
        String namespace = sanitized.substring(0, sep).replace('/', '_');
        String path = sanitized.substring(sep + 1).replace('/', '_').replace(':', '_');
        if (namespace.isEmpty() || path.isEmpty()) return null;
        return new String[]{namespace, path};
    }
}
