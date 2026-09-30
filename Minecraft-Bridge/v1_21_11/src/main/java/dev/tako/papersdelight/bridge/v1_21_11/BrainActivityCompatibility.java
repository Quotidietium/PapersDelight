package dev.tako.papersdelight.bridge.v1_21_11;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

final class BrainActivityCompatibility {

    private BrainActivityCompatibility() {
    }

    static boolean addActivityWithConditions(Object brain, Object activity, List<?> behaviors, Set<?> conditions) {
        if (brain == null || activity == null || behaviors == null || conditions == null) return false;
        for (Method method : brain.getClass().getMethods()) {
            if (!method.getName().equals("addActivityWithConditions") || method.getParameterCount() != 3) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (!parameters[0].isInstance(activity)
                    || !parameters[1].isAssignableFrom(behaviors.getClass())
                    || !parameters[2].isAssignableFrom(conditions.getClass())) continue;
            try {
                method.invoke(brain, activity, behaviors, conditions);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        Set<?> emptyMemoriesToErase = Set.of();
        for (Method method : brain.getClass().getMethods()) {
            if (!method.getName().equals("addActivity") || method.getParameterCount() != 4) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (!parameters[0].isInstance(activity)
                    || !parameters[1].isAssignableFrom(behaviors.getClass())
                    || !parameters[2].isAssignableFrom(conditions.getClass())
                    || !parameters[3].isAssignableFrom(emptyMemoriesToErase.getClass())) continue;
            try {
                method.invoke(brain, activity, behaviors, conditions, emptyMemoriesToErase);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }
}
