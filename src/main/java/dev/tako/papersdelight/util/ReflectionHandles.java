package dev.tako.papersdelight.util;

import net.momirealms.craftengine.core.util.ReflectionUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionHandles {

    private record Key(Class<?> type, String method) {
    }

    private static final Map<Key, Optional<MethodHandle>> HANDLES = new ConcurrentHashMap<>();

    private ReflectionHandles() {
    }

    public static Object callNoArg(Object target, String methodName) {
        if (target == null) return null;
        Optional<MethodHandle> handle = HANDLES.computeIfAbsent(new Key(target.getClass(), methodName),
                key -> find(key.type(), key.method(), true));
        if (handle.isEmpty()) return null;
        try {
            return handle.get().invokeExact(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object callStaticNoArg(Class<?> type, String methodName) {
        if (type == null) return null;
        Optional<MethodHandle> handle = HANDLES.computeIfAbsent(new Key(type, methodName),
                key -> find(key.type(), key.method(), false));
        if (handle.isEmpty()) return null;
        try {
            return handle.get().invokeExact();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Optional<MethodHandle> find(Class<?> type, String methodName, boolean expectReceiver) {
        try {
            java.lang.reflect.Method method = type.getMethod(methodName);
            if (method.getParameterCount() != 0) return Optional.empty();
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            if (expectReceiver == isStatic) return Optional.empty();
            MethodHandle handle = ReflectionUtils.LOOKUP.unreflect(method);
            MethodType target = isStatic
                    ? MethodType.methodType(Object.class)
                    : MethodType.methodType(Object.class, Object.class);
            return Optional.of(handle.asType(target));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }
}
