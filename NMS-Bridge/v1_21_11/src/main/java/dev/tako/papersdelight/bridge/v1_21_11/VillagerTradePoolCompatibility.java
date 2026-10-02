package dev.tako.papersdelight.bridge.v1_21_11;

import java.util.function.Predicate;

final class VillagerTradePoolCompatibility {

    private static final String MODERN_TRADES =
            "net.minecraft.world.entity.npc.villager.VillagerTrades";
    private static final String LEGACY_TRADES =
            "net.minecraft.world.entity.npc.VillagerTrades";

    private VillagerTradePoolCompatibility() {
    }

    static Injector select(Predicate<String> classPresent) {
        if (classPresent.test(MODERN_TRADES)) return Injector.MODERN;
        if (classPresent.test(LEGACY_TRADES)) return Injector.LEGACY;
        return Injector.UNSUPPORTED;
    }

    static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, VillagerTradePoolCompatibility.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    enum Injector {
        MODERN,
        LEGACY,
        UNSUPPORTED
    }
}
