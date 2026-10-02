package dev.tako.papersdelight.bridge;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VillagerBehaviorSurgery {

    private static volatile Field behaviorsField;

    private VillagerBehaviorSurgery() {
    }

    public static int removeBehaviors(Object brain, Class<?> targetType) {
        if (brain == null || targetType == null) return 0;
        Field field = resolveField(brain);
        if (field == null) return -1;
        try {
            Object raw = field.get(brain);
            if (!(raw instanceof Map<?, ?> byPriority)) return -1;
            int removed = 0;
            for (Object perPriority : byPriority.values()) {
                if (!(perPriority instanceof Map<?, ?> byActivity)) continue;
                for (Object behaviors : byActivity.values()) {
                    if (!(behaviors instanceof Collection<?> collection)) continue;
                    int before = collection.size();
                    collection.removeIf(targetType::isInstance);
                    removed += before - collection.size();

                    for (Object behavior : collection) {
                        removed += removeNested(behavior, targetType);
                    }
                }
            }
            return removed;
        } catch (ReflectiveOperationException | RuntimeException e) {

            return -1;
        }
    }

    private static int removeNested(Object container, Class<?> targetType) {
        if (container == null) return 0;
        int removed = 0;
        for (Field field : container.getClass().getDeclaredFields()) {
            Object value = readField(container, field);
            if (value == null) continue;
            List<?> entries = shufflingEntries(value);
            if (entries == null) continue;
            int before = entries.size();
            entries.removeIf(entry -> targetType.isInstance(weightedData(entry)));
            removed += before - entries.size();
            for (Object entry : entries) {
                removed += removeNested(weightedData(entry), targetType);
            }
        }
        return removed;
    }

    private static List<?> shufflingEntries(Object candidate) {
        for (Class<?> type = candidate.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                if (!field.getName().contains("entries")) continue;
                Object value = readField(candidate, field);
                if (value instanceof List<?> list) return list;
            }
        }
        return null;
    }

    private static Object weightedData(Object entry) {
        if (entry == null) return null;
        try {
            java.lang.reflect.Method getData = entry.getClass().getMethod("getData");
            getData.setAccessible(true);
            return getData.invoke(entry);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object readField(Object owner, Field field) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Field resolveField(Object brain) {
        Field cached = behaviorsField;

        if (cached != null && cached.getDeclaringClass().isInstance(brain)) return cached;
        for (Class<?> type = brain.getClass(); type != null; type = type.getSuperclass()) {
            for (Field candidate : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(candidate.getType())) continue;
                if (!candidate.getName().contains("availableBehaviors")) continue;
                try {
                    candidate.setAccessible(true);
                } catch (RuntimeException e) {
                    continue;
                }
                behaviorsField = candidate;
                return candidate;
            }
        }
        return null;
    }

    public static boolean hasBehavior(Object brain, Class<?> targetType) {
        if (brain == null || targetType == null) return false;
        Field field = resolveField(brain);
        if (field == null) return false;
        try {
            Object raw = field.get(brain);
            if (!(raw instanceof Map<?, ?> byPriority)) return false;
            for (Object perPriority : byPriority.values()) {
                if (!(perPriority instanceof Map<?, ?> byActivity)) continue;
                for (Object behaviors : byActivity.values()) {
                    if (!(behaviors instanceof Collection<?> collection)) continue;
                    for (Object behavior : collection) {
                        if (targetType.isInstance(behavior)) return true;
                        if (hasNested(behavior, targetType)) return true;
                    }
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static boolean hasNested(Object container, Class<?> targetType) {
        if (container == null) return false;
        for (Field field : container.getClass().getDeclaredFields()) {
            Object value = readField(container, field);
            if (value == null) continue;
            List<?> entries = shufflingEntries(value);
            if (entries == null) continue;
            for (Object entry : entries) {
                Object data = weightedData(entry);
                if (targetType.isInstance(data)) return true;
                if (hasNested(data, targetType)) return true;
            }
        }
        return false;
    }

    public static int removeEmptyContainers(Object brain) {
        if (brain == null) return 0;
        Field field = resolveField(brain);
        if (field == null) return -1;
        try {
            Object raw = field.get(brain);
            if (!(raw instanceof Map<?, ?> byPriority)) return -1;
            int removed = 0;
            for (Object perPriority : byPriority.values()) {
                if (!(perPriority instanceof Map<?, ?> byActivity)) continue;
                for (Object behaviors : byActivity.values()) {
                    if (!(behaviors instanceof Collection<?> collection)) continue;
                    int before = collection.size();
                    collection.removeIf(VillagerBehaviorSurgery::isEmptyContainer);
                    removed += before - collection.size();
                }
            }
            return removed;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static boolean isEmptyContainer(Object behavior) {
        if (behavior == null) return false;
        boolean sawContainer = false;
        for (Field field : behavior.getClass().getDeclaredFields()) {
            Object value = readField(behavior, field);
            if (value == null) continue;
            List<?> entries = shufflingEntries(value);
            if (entries == null) continue;
            sawContainer = true;
            if (!entries.isEmpty()) return false;
        }
        return sawContainer;
    }

    public static boolean containsNone(Set<?> behaviors, Class<?> targetType) {
        return behaviors == null || behaviors.stream().noneMatch(targetType::isInstance);
    }
}
