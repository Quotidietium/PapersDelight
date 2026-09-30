package dev.tako.papersdelight.mechanic.function;

import dev.tako.papersdelight.effect.TimedEffectManager;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.AbstractConditionalFunction;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public final class RemoveEffectFunction<CTX extends Context>
        extends AbstractConditionalFunction<CTX> {

    private final String effectKey;

    private RemoveEffectFunction(List<Condition<CTX>> predicates, String effectKey) {
        super(predicates);
        this.effectKey = effectKey;
    }

    @Override
    protected void runInternal(CTX ctx) {
        ctx.getOptionalParameter(DirectContextParameters.PLAYER).ifPresent(cePlayer -> {
            if (!(cePlayer.platformPlayer() instanceof Player player)) return;

            TimedEffectManager manager = TimedEffectManager.byQualifiedId(effectKey);
            if (manager != null) {
                manager.removeEffect(player);
                return;
            }

            PotionEffectType type = Registry.EFFECT.get(
                    org.bukkit.NamespacedKey.fromString(effectKey));
            if (type != null) {
                cePlayer.removePotionEffect(Key.of(effectKey));
            }
        });
    }

    public static <CTX extends Context> FunctionFactory<CTX, RemoveEffectFunction<CTX>> factory(
            java.util.function.Function<ConfigSection, Condition<CTX>> conditionFactory) {
        return new Factory<>(conditionFactory);
    }

    private static class Factory<CTX extends Context>
            extends AbstractFactory<CTX, RemoveEffectFunction<CTX>> {

        public Factory(java.util.function.Function<ConfigSection, Condition<CTX>> factory) {
            super(factory);
        }

        @Override
        public RemoveEffectFunction<CTX> create(ConfigSection section) {
            return new RemoveEffectFunction<>(
                    getPredicates(section),
                    section.getNonNullString("effect")
            );
        }
    }
}
