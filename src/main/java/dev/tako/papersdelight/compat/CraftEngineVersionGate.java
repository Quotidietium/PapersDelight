package dev.tako.papersdelight.compat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class CraftEngineVersionGate {

    private static final String FALLBACK_VERSION = "26.8.1";

    public static final String REQUIRED_VERSION = loadRequiredVersion();

    private static final int[] REQUIRED_PARTS = parse(REQUIRED_VERSION);

    private CraftEngineVersionGate() {
    }

    public static boolean isBelowRequired(String version) {
        int[] actual = parse(version);
        return actual.length != 0 && compare(actual, REQUIRED_PARTS) < 0;
    }

    private static String loadRequiredVersion() {
        try (InputStream in = CraftEngineVersionGate.class.getResourceAsStream("/papersdelight-build.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String value = properties.getProperty("craftengine.version");
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        } catch (IOException ignored) {
        }
        return FALLBACK_VERSION;
    }

    private static int compare(int[] left, int[] right) {
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        if (version == null) {
            return new int[0];
        }
        String trimmed = version.trim();
        if (trimmed.isEmpty()) {
            return new int[0];
        }
        int dash = trimmed.indexOf('-');
        if (dash >= 0) {
            trimmed = trimmed.substring(0, dash);
        }
        String[] segments = trimmed.split("\\.");
        int[] parts = new int[segments.length];
        int count = 0;
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(segment);
            } catch (NumberFormatException ignored) {
                break;
            }
            parts[count++] = value;
        }
        if (count == 0) {
            return new int[0];
        }
        int[] result = new int[count];
        System.arraycopy(parts, 0, result, 0, count);
        return result;
    }
}
