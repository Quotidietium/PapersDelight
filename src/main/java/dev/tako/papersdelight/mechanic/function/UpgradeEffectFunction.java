package dev.tako.papersdelight.mechanic.function;

import dev.tako.papersdelight.effect.TimedEffectManager;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.AbstractConditionalFunction;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.plugin.context.number.NumberProvider;
import net.momirealms.craftengine.core.plugin.context.number.ConstantNumberProvider;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public final class UpgradeEffectFunction<CTX extends Context>
        extends AbstractConditionalFunction<CTX> {

    private final String effectKey;
    private final NumberProvider increment;
    private final NumberProvider maxAmplifier;
    private final NumberProvider duration;

    private UpgradeEffectFunction(List<Condition<CTX>> predicates,
                                  String effectKey,
                                  NumberProvider increment,
                                  NumberProvider maxAmplifier,
                                  NumberProvider duration) {
        super(predicates);
        this.effectKey = effectKey;
        this.increment = increment;
        this.maxAmplifier = maxAmplifier;
        this.duration = duration;
    }

    @Override
    protected void runInternal(CTX ctx) {
        ctx.getOptionalParameter(DirectContextParameters.PLAYER).ifPresent(cePlayer -> {
            if (!(cePlayer.platformPlayer() instanceof Player player)) return;

            int inc = this.increment.getInt(ctx);
            int max = this.maxAmplifier.getInt(ctx);
            int cfgDuration = this.duration.getInt(ctx);

            TimedEffectManager manager = TimedEffectManager.byQualifiedId(effectKey);
            if (manager != null) {
                upgradeCustom(manager, player, inc, max, cfgDuration);
                return;
            }

            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.fromString(effectKey));
            if (type != null) {
                upgradeVanilla(player, type, inc, max, cfgDuration);
            }
        });
    }

    private static void upgradeCustom(TimedEffectManager manager, Player player,
                                      int increment, int maxAmplifier, int cfgDuration) {
        if (!manager.isActive(player)) return;
        int newAmplifier = UpgradeEffectMath.upgradedAmplifier(
                manager.getAmplifier(player), increment, maxAmplifier);
        int newDuration = UpgradeEffectMath.mergedDuration(
                manager.getRemainingTicks(player), cfgDuration);
        manager.applyEffect(player, newDuration, newAmplifier);
    }

    private static void upgradeVanilla(Player player, PotionEffectType type,
                                       int increment, int maxAmplifier, int cfgDuration) {
        PotionEffect current = player.getPotionEffect(type);
        if (current == null) return;
        int newAmplifier = UpgradeEffectMath.upgradedAmplifier(
                current.getAmplifier(), increment, maxAmplifier);
        int newDuration = UpgradeEffectMath.mergedDuration(
                current.getDuration(), cfgDuration);
        player.addPotionEffect(current.withAmplifier(newAmplifier).withDuration(newDuration));
    }

    public static <CTX extends Context> FunctionFactory<CTX, UpgradeEffectFunction<CTX>> factory(
            java.util.function.Function<ConfigSection, Condition<CTX>> conditionFactory) {
        return new Factory<>(conditionFactory);
    }

    private static final class Factory<CTX extends Context>
            extends AbstractFactory<CTX, UpgradeEffectFunction<CTX>> {

        public Factory(java.util.function.Function<ConfigSection, Condition<CTX>> factory) {
            super(factory);
        }

        @Override
        public UpgradeEffectFunction<CTX> create(ConfigSection section) {
            return new UpgradeEffectFunction<>(
                    getPredicates(section),
                    section.getNonNullString("effect"),
                    section.getNumber("increment", ConfigConstants.CONSTANT_ONE),
                    section.getNumber("max_amplifier", ConstantNumberProvider.constant(4)),
                    section.getNumber("duration", ConfigConstants.CONSTANT_ZERO)
            );
        }
    }
}
