package dev.tako.papersdelight.bridge;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VillagerFoodBridge {

    private static final Map<Class<?>, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Boolean> FAILED = new ConcurrentHashMap<>();

    private VillagerFoodBridge() {
    }

    public static int get(Object villager) {
        Field field = resolve(villager);
        if (field == null) return -1;
        try {
            return field.getInt(villager);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            return -1;
        }
    }

    public static boolean add(Object villager, int amount) {
        if (amount <= 0) return false;
        Field field = resolve(villager);
        if (field == null) return false;
        try {
            int current = field.getInt(villager);
            field.setInt(villager, current + amount);
            return true;
        } catch (IllegalAccessException | IllegalArgumentException e) {
            return false;
        }
    }

    private static Field resolve(Object villager) {
        if (villager == null) return null;
        Class<?> type = villager.getClass();

        Field cached = FIELD_CACHE.get(type);
        if (cached != null) return cached;
        if (FAILED.containsKey(type)) return null;

        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("foodLevel");
                if (field.getType() != int.class) continue;
                field.setAccessible(true);
                FIELD_CACHE.put(type, field);
                return field;
            } catch (NoSuchFieldException ignored) {

            } catch (RuntimeException ignored) {

                break;
            }
        }

        FAILED.put(type, Boolean.TRUE);
        return null;
    }
}
