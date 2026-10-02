package dev.tako.papersdelight.bridge.api;

import org.bukkit.Bukkit;

public final class BridgeProvider {

    private BridgeProvider() {}

    private static final class InstanceHolder {
        static final Bridge INSTANCE;
        static {
            String implClass = implementationClassName(Bukkit.getMinecraftVersion());
            try {
                INSTANCE = (Bridge) Bridge.class.getClassLoader()
                        .loadClass(implClass)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    public static Bridge get() {
        return InstanceHolder.INSTANCE;
    }

    public static String implementationClassName(String minecraftVersion) {

        String[] parts = minecraftVersion.split("-")[0].split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw unsupported(minecraftVersion);
        }
        for (String part : parts) {
            try {
                Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw unsupported(minecraftVersion);
            }
        }

        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;

        if (major == 1 && minor == 21 && patch <= 1) {
            return "dev.tako.papersdelight.bridge.v1_21_1.BridgeV1_21_1";
        } else if (major == 1 && minor == 21 && patch <= 4) {
            return "dev.tako.papersdelight.bridge.v1_21_4.BridgeV1_21_4";
        } else if (major == 1 && minor == 21 && patch <= 10) {
            return "dev.tako.papersdelight.bridge.v1_21_10.BridgeV1_21_10";
        } else if (major == 1 && minor == 21 && patch == 11) {
            return "dev.tako.papersdelight.bridge.v1_21_11.BridgeV1_21_11";
        } else if (major == 26 && (minor == 1 || minor == 2)) {
            return "dev.tako.papersdelight.bridge.v1_21_11.BridgeV1_21_11";
        } else {
            throw unsupported(minecraftVersion);
        }
    }

    private static IllegalStateException unsupported(String version) {
        return new IllegalStateException(
                "不支持的 Minecraft 版本：" + version + "；支持范围为 1.21.0～1.21.11、26.1.x～26.2.x"
        );
    }
}
