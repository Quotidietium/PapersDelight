package dev.tako.papersdelight.jug;

import net.momirealms.craftengine.core.util.ReflectionUtils;
import dev.tako.papersdelight.util.ReflectionHandles;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.api.menu.Menu;
import dev.tako.papersdelight.api.menu.MenuEventHandler;
import dev.tako.papersdelight.gui.MenuManager;
import dev.tako.papersdelight.api.menu.SimpleMenuModule;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

public final class JugRuntimeInstaller {
    private static final String DECODER = "dev.tako.papersdelight.jug.JugRecipeDecoderImpl";
    private static final String MANAGER = "dev.tako.papersdelight.jug.JugManager";
    private static final java.util.Map<Class<?>, MethodHandle> MENU_FACTORIES = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile Throwable lastMenuFailure;

    private JugRuntimeInstaller() {
    }

    public static ClassLoader runtimeClassLoader() {
        return JugRuntimeInstaller.class.getClassLoader();
    }

    public static boolean installDecoderEarly(Plugin plugin) {
        if (!JugSupport.isPresentAndVisible(plugin, runtimeClassLoader())) {
            JugRecipeDecoderBridge.install(null);
            return false;
        }
        try {
            Object decoder = Class.forName(DECODER, true, runtimeClassLoader())
                    .getDeclaredConstructor().newInstance();
            JugRecipeDecoderBridge.install((JugRecipeDecoderBridge.JugRecipeDecoder) decoder);
            return true;
        } catch (Throwable failure) {
            JugRecipeDecoderBridge.install(null);
            plugin.getLogger().warning("Unable to install Jug recipe decoder: " + failure.getMessage());
            return false;
        }
    }

    public static Object installRuntime(JavaPlugin plugin, RecipeManager recipes) {
        if (!JugSupport.isAvailable(plugin, runtimeClassLoader())) return null;
        Object manager = null;
        try {
            Object created = Class.forName(MANAGER, true, runtimeClassLoader())
                    .getDeclaredConstructor(JavaPlugin.class, RecipeManager.class).newInstance(plugin, recipes);
            manager = created;
            if (!completeRuntimeInstall(created,
                    () -> plugin.getServer().getPluginManager().registerEvents((Listener) created, plugin),
                    JugRuntimeInstaller::shutdownRuntime)) {
                return null;
            }
            return created;
        } catch (Throwable failure) {

            shutdownRuntime(manager);
            plugin.getLogger().warning("Unable to install Jug runtime bridge: " + failure.getMessage());
            return null;
        }
    }

    public static boolean registerMenu(MenuManager menuManager, Object manager) {
        if (menuManager == null || manager == null || !JugGate.available()) return false;
        try {
            ClassLoader loader = manager.getClass().getClassLoader();
            Class<?> menuClass = Class.forName("dev.tako.papersdelight.gui.module.jug.JugMenu", true, loader);
            Class<?> handlerClass = Class.forName("dev.tako.papersdelight.gui.module.jug.JugEventHandler", true, loader);
            Class<?> managerClass = Class.forName(MANAGER, true, loader);
            Object handler = handlerClass.getConstructor(managerClass).newInstance(manager);
            SimpleMenuModule module = new SimpleMenuModule.Builder()
                    .id("jug")
                    .menu(() -> {
                        try {
                            return invokeMenuFactory(menuClass);
                        } catch (ReflectiveOperationException failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .handler((MenuEventHandler) handler)
                    .build();
            menuManager.registerModule(module);
            return true;
        } catch (Throwable failure) {
            lastMenuFailure = failure;
            return false;
        }
    }

    static Menu invokeMenuFactory(Class<?> menuClass) throws ReflectiveOperationException {
        MethodHandle factory = MENU_FACTORIES.computeIfAbsent(menuClass, JugRuntimeInstaller::findMenuFactory);
        try {
            return (Menu) factory.invokeExact();
        } catch (ReflectiveOperationException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new ReflectiveOperationException(failure);
        }
    }

    private static MethodHandle findMenuFactory(Class<?> menuClass) {
        for (Method method : menuClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 0
                    && Menu.class.isAssignableFrom(method.getReturnType())) {
                try {
                    return ReflectionUtils.LOOKUP.unreflect(method).asType(MethodType.methodType(Menu.class));
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
        throw new IllegalStateException(menuClass.getName() + " has no public static Menu factory");
    }

    public static Throwable lastMenuRegistrationFailure() {
        return lastMenuFailure;
    }

    static boolean completeRuntimeInstall(Object manager, Runnable registerEvents, Consumer<Object> shutdown) {
        try {
            JugGate.install((JugGate.Bridge) manager);
            registerEvents.run();
            return true;
        } catch (Throwable failure) {
            shutdown.accept(manager);
            return false;
        }
    }

    public static void shutdownRuntime(Object manager) {
        JugGate.uninstall();
        if (manager == null) return;
        ReflectionHandles.callNoArg(manager, "shutdown");
    }

    public static void uninstallDecoder() {
        JugRecipeDecoderBridge.install(null);
    }
}
