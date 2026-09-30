package dev.tako.papersdelight.jug;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JugRuntimeInstallerContractTest {

    @Test
    void jugManagerExposesConstructorMatchingInstallerLookup() {
        Constructor<?>[] constructors = JugManager.class.getDeclaredConstructors();
        assertEquals(1, constructors.length, "JugManager 应只暴露一个构造器供反射安装");

        Class<?>[] parameters = constructors[0].getParameterTypes();
        assertEquals(2, parameters.length, "JugManager 构造器应接收插件与配方管理器两个参数");

        assertTrue(org.bukkit.plugin.Plugin.class.isAssignableFrom(parameters[0]),
                "JugManager 首个构造参数应为插件类型");

        assertDoesNotThrow(() -> JugManager.class.getDeclaredConstructor(parameters[0], parameters[1]),
                "按声明类型查找构造器必须成功");
    }

    @Test
    void installRuntimeAcceptsConstructorParameterType() throws ReflectiveOperationException {
        Class<?> pluginParameter = JugManager.class.getDeclaredConstructors()[0].getParameterTypes()[0];

        Method installRuntime = JugRuntimeInstaller.class
                .getDeclaredMethod("installRuntime", pluginParameter, dev.tako.papersdelight.recipe.RecipeManager.class);

        assertTrue(installRuntime.getParameterTypes()[0].isAssignableFrom(pluginParameter),
                "installRuntime 的插件参数类型必须能承载 JugManager 构造器所需类型");
    }
}
