package dev.tako.papersdelight.bridge;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

public final class BackstabbingEnchantmentRegistrar {

    private BackstabbingEnchantmentRegistrar() {
    }

    public static Enchantment register(
            String namespace,
            String key,
            int weight,
            int maxLevel,
            int minCostBase,
            int minCostPerLevel,
            int maxCostBase,
            int maxCostPerLevel,
            int anvilCost
    ) {
        NamespacedKey namespacedKey = new NamespacedKey(namespace, key);
        Enchantment existing = getExisting(namespacedKey);
        if (existing != null) return existing;

        Spec spec = new Spec(namespace, key, weight, maxLevel, minCostBase, minCostPerLevel,
                maxCostBase, maxCostPerLevel, anvilCost);
        try {
            doRegister(spec);
            return getExisting(namespacedKey);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static Enchantment getExisting(NamespacedKey key) {
        try {
            return org.bukkit.Registry.ENCHANTMENT.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void doRegister(Spec spec) throws Throwable {

        Object enchRegistry = getEnchantmentRegistry();
        Field frozenField = findBooleanField(enchRegistry.getClass());

        boolean wasAlreadyFrozen = frozenField.getBoolean(enchRegistry);

        frozenField.setBoolean(enchRegistry, false);

        resetAllTagsToUnbound(enchRegistry);

        try {

            Object resourceId = makeResourceId(spec.namespace(), spec.key());
            Object resourceKey = makeEnchantmentResourceKey(resourceId);

            Object nmsEnchantment = buildEnchantment(resourceId, spec);

            registerMapping(enchRegistry, resourceKey, nmsEnchantment);

        } finally {

            try {
                Method freeze = enchRegistry.getClass().getMethod("freeze");
                freeze.invoke(enchRegistry);
            } catch (Throwable t) {

                frozenField.setBoolean(enchRegistry, true);
            }

            restoreIntrusiveHolders(enchRegistry);

            if (!wasAlreadyFrozen) {
                frozenField.setBoolean(enchRegistry, false);
            }
        }

        clearBukkitCache();
    }

    private static void resetAllTagsToUnbound(Object registry) throws Throwable {
        Class<?> registryClass = registry.getClass();

        Field allTagsField = null;
        for (Class<?> c = registryClass; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType().getName().contains("TagSet")
                        || f.getType().getSimpleName().contains("TagSet")) {
                    allTagsField = f;
                    break;
                }
            }
            if (allTagsField != null) break;
        }
        if (allTagsField == null) {

            throw new IllegalStateException("找不到 allTags 字段");
        }
        allTagsField.setAccessible(true);

        Class<?> tagSetClass = allTagsField.getType();
        Object unboundTagSet = null;
        for (Method m : tagSetClass.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 0
                    && tagSetClass.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                unboundTagSet = m.invoke(null);
                break;
            }
        }
        if (unboundTagSet == null) {
            throw new IllegalStateException("找不到 TagSet 的 unbound 工厂方法");
        }

        allTagsField.set(registry, unboundTagSet);
    }

    private static void restoreIntrusiveHolders(Object registry) {
        try {
            for (Class<?> c = registry.getClass(); c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (Modifier.isFinal(f.getModifiers())) continue;
                    if (f.getType() != Map.class) continue;
                    f.setAccessible(true);
                    Object val = f.get(registry);
                    if (val == null) {

                        f.set(registry, new IdentityHashMap<>());
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {

        }
    }

    private static Object getEnchantmentRegistry() throws Throwable {
        Object server = Bukkit.getServer();

        Method getServer = server.getClass().getMethod("getServer");
        Object nmsServer = getServer.invoke(server);

        Method registryAccess = findMethod(nmsServer.getClass(), "registryAccess");
        Object access = registryAccess.invoke(nmsServer);

        Object enchantmentRegistryKey = getEnchantmentResourceKey();

        Class<?> registryInterface = Class.forName("net.minecraft.core.Registry");
        Object result = invokeLookup(access, enchantmentRegistryKey, registryInterface);
        if (result == null) {
            throw new IllegalStateException("registryAccess 未返回可写 Registry");
        }
        return result;
    }

    private static Object getEnchantmentResourceKey() throws Throwable {
        Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
        Field f = registries.getField("ENCHANTMENT");
        return f.get(null);
    }

    private static Object invokeLookup(Object access, Object key, Class<?> registryInterface) throws Throwable {

        java.util.List<Method> candidates = new java.util.ArrayList<>();
        for (Method m : access.getClass().getMethods()) {
            String n = m.getName();
            if ((n.equals("lookupOrThrow") || n.equals("registryOrThrow"))
                    && m.getParameterCount() == 1) {
                candidates.add(m);
            }
        }

        for (Method m : candidates) {
            if (registryInterface.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                return m.invoke(access, key);
            }
        }

        Method fallback = null;
        for (Method m : candidates) {
            m.setAccessible(true);
            try {
                Object r = m.invoke(access, key);
                if (registryInterface.isInstance(r)) return r;
            } catch (Throwable ignored) {
            }
            if (fallback == null) fallback = m;
        }
        if (fallback != null) return fallback.invoke(access, key);
        throw new IllegalStateException("找不到 registryAccess 的 lookup 方法");
    }

    private static Field findBooleanField(Class<?> clazz) {

        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("frozen");
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }

        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == boolean.class
                        && !java.lang.reflect.Modifier.isFinal(f.getModifiers())
                        && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        throw new IllegalStateException("找不到 frozen 字段");
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }

        try {
            Method m = clazz.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Class<?> resourceIdClass() throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.resources.Identifier");
        } catch (ClassNotFoundException e) {
            return Class.forName("net.minecraft.resources.ResourceLocation");
        }
    }

    private static Object makeResourceId(String namespace, String path) throws Throwable {
        Class<?> idClass = resourceIdClass();
        Method factory = idClass.getMethod("fromNamespaceAndPath", String.class, String.class);
        return factory.invoke(null, namespace, path);
    }

    private static Object buildEnchantment(Object resourceId, Spec spec) throws Throwable {
        Class<?> enchClass = Class.forName("net.minecraft.world.item.enchantment.Enchantment");
        Class<?> costClass = Class.forName("net.minecraft.world.item.enchantment.Enchantment$Cost");
        Class<?> holderSetClass = Class.forName("net.minecraft.core.HolderSet");
        Class<?> slotGroupClass = Class.forName("net.minecraft.world.entity.EquipmentSlotGroup");

        Object emptyHolderSet = holderSetClass.getMethod("empty").invoke(null);

        Method dynamicCost = enchClass.getMethod("dynamicCost", int.class, int.class);
        Object minCost = dynamicCost.invoke(null, spec.minCostBase(), spec.minCostPerLevel());
        Object maxCost = dynamicCost.invoke(null, spec.maxCostBase(), spec.maxCostPerLevel());

        Object mainhand = slotGroupClass.getField("MAINHAND").get(null);
        Object slotsArray = Array.newInstance(slotGroupClass, 1);
        Array.set(slotsArray, 0, mainhand);

        Method definition = findDefinitionMethod(enchClass, holderSetClass, costClass, slotGroupClass);
        Object def = definition.invoke(null, emptyHolderSet, spec.weight(), spec.maxLevel(), minCost, maxCost,
                spec.anvilCost(), slotsArray);

        Class<?> defClass = def.getClass();
        Method enchantmentMethod = findEnchantmentBuilderMethod(enchClass, defClass);
        Object builder = enchantmentMethod.invoke(null, def);

        Method build = findBuildMethod(builder.getClass(), resourceId.getClass());
        return build.invoke(builder, resourceId);
    }

    private static Method findDefinitionMethod(Class<?> enchClass, Class<?> holderSetClass,
                                               Class<?> costClass, Class<?> slotGroupClass) {
        for (Method m : enchClass.getMethods()) {
            if (!m.getName().equals("definition")) continue;
            Class<?>[] p = m.getParameterTypes();

            if (p.length == 7 && p[0] == holderSetClass && p[1] == int.class && p[2] == int.class
                    && p[3] == costClass && p[4] == costClass && p[5] == int.class && p[6].isArray()) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("找不到 Enchantment.definition(7参) 方法");
    }

    private static Method findEnchantmentBuilderMethod(Class<?> enchClass, Class<?> defClass) {
        for (Method m : enchClass.getMethods()) {
            if (m.getName().equals("enchantment") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(defClass)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("找不到 Enchantment.enchantment(definition) 方法");
    }

    private static Method findBuildMethod(Class<?> builderClass, Class<?> idClass) {
        for (Method m : builderClass.getMethods()) {
            if (m.getName().equals("build") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(idClass)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("找不到 Builder.build(id) 方法");
    }

    private static Object makeEnchantmentResourceKey(Object resourceId) throws Throwable {
        Object enchantmentRegistryKey = getEnchantmentResourceKey();
        Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");

        for (Method m : resourceKeyClass.getMethods()) {
            if (m.getName().equals("create") && m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == resourceKeyClass
                    && m.getParameterTypes()[1].isAssignableFrom(resourceId.getClass())) {
                m.setAccessible(true);
                return m.invoke(null, enchantmentRegistryKey, resourceId);
            }
        }
        throw new IllegalStateException("找不到 ResourceKey.create(key, id) 方法");
    }

    private static Object registerMapping(Object registry, Object resourceKey, Object enchantment) throws Throwable {
        Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
        Class<?> regInfoClass = Class.forName("net.minecraft.core.RegistrationInfo");
        Object builtIn = regInfoClass.getField("BUILT_IN").get(null);

        for (Class<?> c = registry.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("register")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == resourceKeyClass && p[2] == regInfoClass) {
                    m.setAccessible(true);
                    return m.invoke(registry, resourceKey, enchantment, builtIn);
                }
            }
        }
        throw new IllegalStateException("找不到 MappedRegistry.register(key, value, info) 方法");
    }

    @SuppressWarnings("deprecation")
    private static void clearBukkitCache() {
        try {
            Object bukkitRegistry = org.bukkit.Registry.ENCHANTMENT;

            Method delegate = findMethod(bukkitRegistry.getClass(), "delegate");
            Object craftRegistry = (delegate != null) ? delegate.invoke(bukkitRegistry) : bukkitRegistry;
            if (craftRegistry == null) return;

            Field cacheField = findFieldByType(craftRegistry.getClass(), Map.class);
            if (cacheField != null) {
                cacheField.setAccessible(true);
                Object cache = cacheField.get(craftRegistry);
                if (cache instanceof Map<?, ?> map) {
                    map.clear();
                }
            }
        } catch (Throwable ignored) {

        }
    }

    private static Field findFieldByType(Class<?> clazz, Class<?> type) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    return f;
                }
            }
        }
        return null;
    }

    private record Spec(
            String namespace,
            String key,
            int weight,
            int maxLevel,
            int minCostBase,
            int minCostPerLevel,
            int maxCostBase,
            int maxCostPerLevel,
            int anvilCost
    ) {
    }
}
