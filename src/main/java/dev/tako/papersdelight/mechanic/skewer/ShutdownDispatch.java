package dev.tako.papersdelight.mechanic.skewer;

import java.util.function.Consumer;

final class ShutdownDispatch {

    private ShutdownDispatch() {
    }

    static <T> boolean settle(boolean pluginEnabled,
                              T target,
                              Consumer<T> synchronous,
                              Consumer<T> dispatched) {
        if (!pluginEnabled) {
            try {
                synchronous.accept(target);
            } catch (Throwable ignored) {
            }
            return true;
        }
        dispatched.accept(target);
        return false;
    }
}
