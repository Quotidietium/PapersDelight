package dev.tako.papersdelight.jug;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import dev.tako.papersdelight.util.TextUtil;

public final class JugFluidDisplay {
    private JugFluidDisplay() {}

    public static Component translatableFluidName(String key, Component fluidName) {
        return Component.translatable(key, fluidName)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component translatableFluidName(Component fluidName, int amount, int capacity) {
        Component level = Component.text(amount + "/" + capacity);
        return Component.translatable("container.farmersdelight.jug.fluid", fluidName, level)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component translatableFluidName(String fluidName, int amount, int capacity) {
        return translatableFluidName(TextUtil.parse(fluidName), amount, capacity);
    }

    public static Component preservingName(Component existing, String fluidName, int amount, int capacity) {
        if (fluidName == null || fluidName.isBlank()) return existing;
        return translatableFluidName(fluidName, amount, capacity);
    }

    public static Component emptyName() {
        return Component.translatable("container.farmersdelight.jug.empty")
                .decoration(TextDecoration.ITALIC, false);
    }
}
