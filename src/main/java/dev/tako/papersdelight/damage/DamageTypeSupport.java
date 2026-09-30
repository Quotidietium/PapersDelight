package dev.tako.papersdelight.damage;

import io.papermc.paper.ServerBuildInfo;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

public final class DamageTypeSupport {

    private static final @NotNull String REGISTRY_EVENTS_CLASS_NAME =
            "io.papermc.paper.registry.event.RegistryEvents";
    private static final @NotNull String DAMAGE_TYPE_FIELD_NAME = "DAMAGE_TYPE";

    private static final @NotNull String ADAPTER_V1_21_4 =
            "dev.tako.papersdelight.bridge.v1_21_4.DamageTypeComposeRegistrar";
    private static final @NotNull String ADAPTER_V1_21_10 =
            "dev.tako.papersdelight.bridge.v1_21_10.DamageTypeComposeRegistrar";
    private static final @NotNull String ADAPTER_V1_21_11 =
            "dev.tako.papersdelight.bridge.v1_21_11.DamageTypeComposeRegistrar";

    public static final @NotNull String ADAPTER_CLASS_NAME = ADAPTER_V1_21_4;

    private static final boolean REGISTRY_EVENT_CAPABILITY_PRESENT = computeRegistryEventCapabilityPresent();
    private static final boolean ADAPTER_CLASS_PRESENT = computeAdapterClassPresent();

    private DamageTypeSupport() {
        throw new UnsupportedOperationException("DamageTypeSupport is a utility class");
    }


    public static boolean isRegistryEventCapabilityPresent() {
        return REGISTRY_EVENT_CAPABILITY_PRESENT;
    }

    public static boolean isAdapterClassPresent() {
        return ADAPTER_CLASS_PRESENT;
    }


    public static @NotNull String currentAdapterClassName() {
        return adapterClassName(ServerBuildInfo.buildInfo().minecraftVersionId());
    }


    public static @NotNull String adapterClassName(@NotNull String minecraftVersion) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");

        String[] parts = minecraftVersion.split("-")[0].split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw unsupported(minecraftVersion);
        }
        int major;
        int minor;
        int patch;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
            patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
        } catch (NumberFormatException e) {
            throw unsupported(minecraftVersion);
        }

        if (major == 1 && minor == 21 && patch <= 4) {
            return ADAPTER_V1_21_4;
        } else if (major == 1 && minor == 21 && patch <= 10) {
            return ADAPTER_V1_21_10;
        } else if (major == 1 && minor == 21 && patch == 11) {
            return ADAPTER_V1_21_11;
        } else if (major == 26 && (minor == 1 || minor == 2)) {
            return ADAPTER_V1_21_11;
        } else {
            throw unsupported(minecraftVersion);
        }
    }

    private static IllegalStateException unsupported(String minecraftVersion) {
        return new IllegalStateException("不支持的 Minecraft 版本，无法选择伤害类型适配器："
                + minecraftVersion + "；支持范围为 1.21.0～1.21.11、26.1.x～26.2.x");
    }

    private static boolean computeRegistryEventCapabilityPresent() {
        try {
            ClassLoader classLoader = DamageTypeSupport.class.getClassLoader();
            Class<?> registryEventsClass = Class.forName(REGISTRY_EVENTS_CLASS_NAME, false, classLoader);
            Field damageTypeField = registryEventsClass.getField(DAMAGE_TYPE_FIELD_NAME);
            return Modifier.isStatic(damageTypeField.getModifiers());
        } catch (ClassNotFoundException | NoSuchFieldException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean computeAdapterClassPresent() {
        try {
            ClassLoader classLoader = DamageTypeSupport.class.getClassLoader();
            Class.forName(ADAPTER_CLASS_NAME, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
