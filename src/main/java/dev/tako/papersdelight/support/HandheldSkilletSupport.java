package dev.tako.papersdelight.support;

public final class HandheldSkilletSupport {
    private HandheldSkilletSupport() {
    }

    public static boolean isSupported(String minecraftVersion) {
        int[] parts = parse(minecraftVersion);
        if (parts == null) return false;
        int major = parts[0];
        int minor = parts[1];
        int patch = parts[2];
        return major > 1 || major == 1 && (minor > 21 || minor == 21 && patch >= 4);
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) return null;
        String core = version.trim().split("-", 2)[0];
        String[] segments = core.split("\\.");
        if (segments.length < 2 || segments.length > 3) return null;
        try {
            return new int[]{
                    Integer.parseInt(segments[0]),
                    Integer.parseInt(segments[1]),
                    segments.length == 3 ? Integer.parseInt(segments[2]) : 0
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
