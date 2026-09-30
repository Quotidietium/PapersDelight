package dev.tako.papersdelight.damage;

import dev.tako.papersdelight.api.damage.DamageTypeDefinition;
import dev.tako.papersdelight.api.damage.DamageTypeHandle;
import dev.tako.papersdelight.api.damage.DamageTypeRegistrationState;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class DamageTypes {

    private static final @NotNull Map<Key, DamageTypeHandle> HANDLES = new ConcurrentHashMap<>();
    private static final @NotNull Object LOCK = new Object();
    private static final @NotNull Logger LOGGER = Logger.getLogger("PapersDelight-DamageTypes");

    static KeyPresence keyPresence = DamageTypes::isKeyInRuntimeRegistry;

    static DamageDispatcher damageDispatcher = DamageTypes::dispatchDamage;

    static BooleanSupplier capabilityCheck = DamageTypeSupport::isRegistryEventCapabilityPresent;

    static AdapterInvoker adapterInvoker = DamageTypes::invokeAdapter;

    static Supplier<String> adapterClassNameResolver = DamageTypeSupport::currentAdapterClassName;


    public static final @NotNull Key GENERIC_KEY = Key.key("minecraft", "generic");

    private DamageTypes() {
        throw new UnsupportedOperationException("DamageTypes is a utility class");
    }

    private static void invokeAdapter(BootstrapContext context, DamageTypeDefinition definition) throws Throwable {
        Class<?> adapterClass = Class.forName(adapterClassNameResolver.get());
        Method registerMethod = adapterClass.getMethod("register", BootstrapContext.class, DamageTypeDefinition.class);
        MethodHandle register = MethodHandles.lookup().unreflect(registerMethod);
        register.invoke(context, definition);
    }

    private static boolean isKeyInRuntimeRegistry(Key key) {
        try {
            return Registry.DAMAGE_TYPE.get(new NamespacedKey(key.namespace(), key.value())) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static DamageType resolveType(Key key) {
        try {
            DamageType type = Registry.DAMAGE_TYPE.get(new NamespacedKey(key.namespace(), key.value()));
            return type != null ? type : DamageType.GENERIC;
        } catch (Throwable t) {
            return DamageType.GENERIC;
        }
    }

    private static void dispatchDamage(LivingEntity victim, double amount, Key effectiveKey, Entity causer) {
        DamageType type = resolveType(effectiveKey);
        DamageSource.Builder builder = DamageSource.builder(type);
        if (causer != null) {
            builder.withCausingEntity(causer);
        }
        DamageSource source = builder.build();
        victim.damage(amount, source);
    }


    @NotNull
    public static DamageTypeHandle register(@NotNull BootstrapContext context,
                                            @NotNull DamageTypeDefinition definition) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(definition, "definition");

        synchronized (LOCK) {
            Key key = definition.key();
            DamageTypeHandle existing = HANDLES.get(key);
            if (existing != null) {
                if (existing.getDefinition().equals(definition)) {
                    return existing;
                }

                LOGGER.severe("检测到伤害类型键冲突，已强制降级为 FALLBACK。key=" + key
                        + ", existing=" + existing.getDefinition()
                        + ", incoming=" + definition);
                existing.downgrade();
                return new DamageTypeHandle(definition, DamageTypeRegistrationState.FALLBACK);
            }

            boolean capabilityPresent;
            try {
                capabilityPresent = capabilityCheck.getAsBoolean();
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "伤害类型注册能力检测异常，回退解析: " + key, t);
                capabilityPresent = false;
            }

            if (!capabilityPresent) {
                LOGGER.info("伤害类型注册能力缺失（1.21-1.21.3），回退解析: " + key);
                DamageTypeHandle handle = new DamageTypeHandle(definition, DamageTypeRegistrationState.FALLBACK);
                HANDLES.put(key, handle);
                return handle;
            }

            try {
                adapterInvoker.invoke(context, definition);
                DamageTypeHandle handle = new DamageTypeHandle(definition, DamageTypeRegistrationState.REGISTERED);
                HANDLES.put(key, handle);
                return handle;
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "伤害类型注册失败，回退: " + key, t);
                DamageTypeHandle handle = new DamageTypeHandle(definition, DamageTypeRegistrationState.FALLBACK);
                HANDLES.put(key, handle);
                return handle;
            }
        }
    }

    static DamageTypeHandle peek(Key key) {
        return HANDLES.get(key);
    }


    @NotNull
    public static Key effectiveKey(@NotNull Key customKey, @NotNull Key fallback) {
        Objects.requireNonNull(customKey, "customKey");
        Objects.requireNonNull(fallback, "fallback");
        if (keyPresence.isPresent(customKey)) {
            return customKey;
        }
        if (keyPresence.isPresent(fallback)) {
            return fallback;
        }
        return GENERIC_KEY;
    }


    @NotNull
    public static Key effectiveKey(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return effectiveKey(handle.getKey(), handle.getFallback());
    }


    @NotNull
    public static DamageType resolve(@NotNull Key customKey, @NotNull Key fallback) {
        return resolveType(effectiveKey(customKey, fallback));
    }


    @NotNull
    public static DamageType resolve(@NotNull DamageTypeHandle handle) {
        Objects.requireNonNull(handle, "handle");
        return resolve(handle.getKey(), handle.getFallback());
    }


    public static void damage(@NotNull LivingEntity victim, double amount, @NotNull DamageTypeHandle handle) {
        damage(victim, amount, handle, null);
    }


    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull DamageTypeHandle handle,
                              @Nullable Entity causer) {
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(handle, "handle");
        Key effectiveKey = effectiveKey(handle);
        damageDispatcher.dispatch(victim, amount, effectiveKey, causer);
    }


    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull Key customKey,
                              @NotNull Key fallback) {
        damage(victim, amount, customKey, fallback, null);
    }


    public static void damage(@NotNull LivingEntity victim,
                              double amount,
                              @NotNull Key customKey,
                              @NotNull Key fallback,
                              @Nullable Entity causer) {
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(customKey, "customKey");
        Objects.requireNonNull(fallback, "fallback");
        Key effectiveKey = effectiveKey(customKey, fallback);
        damageDispatcher.dispatch(victim, amount, effectiveKey, causer);
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            HANDLES.clear();
            capabilityCheck = DamageTypeSupport::isRegistryEventCapabilityPresent;
            adapterInvoker = DamageTypes::invokeAdapter;
            adapterClassNameResolver = DamageTypeSupport::currentAdapterClassName;
            keyPresence = DamageTypes::isKeyInRuntimeRegistry;
            damageDispatcher = DamageTypes::dispatchDamage;
        }
    }
}

@FunctionalInterface
interface KeyPresence {

    boolean isPresent(Key key);
}

@FunctionalInterface
interface DamageDispatcher {

    void dispatch(LivingEntity victim, double amount, Key effectiveKey, Entity causer);
}

@FunctionalInterface
interface AdapterInvoker {

    void invoke(BootstrapContext context, DamageTypeDefinition definition) throws Throwable;
}
