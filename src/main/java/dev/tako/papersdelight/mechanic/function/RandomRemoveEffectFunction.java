package dev.tako.papersdelight.mechanic.function;

import dev.tako.papersdelight.effect.TimedEffectManager;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.AbstractConditionalFunction;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class RandomRemoveEffectFunction<CTX extends Context>
        extends AbstractConditionalFunction<CTX> {

    private static final Random RANDOM = new Random();

    private final boolean harmfulOnly;

    private RandomRemoveEffectFunction(List<Condition<CTX>> predicates, boolean harmfulOnly) {
        super(predicates);
        this.harmfulOnly = harmfulOnly;
    }

    @Override
    protected void runInternal(CTX ctx) {
        ctx.getOptionalParameter(DirectContextParameters.PLAYER).ifPresent(cePlayer -> {
            if (!(cePlayer.platformPlayer() instanceof Player bukkitPlayer)) return;

            List<String> vanillaIds = new ArrayList<>();
            for (PotionEffect e : bukkitPlayer.getActivePotionEffects()) {
                if (harmfulOnly && e.getType().getCategory() != PotionEffectTypeCategory.HARMFUL) {
                    continue;
                }
                vanillaIds.add(e.getType().getKey().toString());
            }

            List<String> customIds = new ArrayList<>();
            Set<String> lowPriorityIds = new HashSet<>();
            for (TimedEffectManager manager : TimedEffectManager.registered()) {
                if (!manager.isActive(bukkitPlayer)) continue;
                if (harmfulOnly && !manager.isHarmful()) continue;
                customIds.add(manager.qualifiedId());
                if (manager.isLowPriority()) lowPriorityIds.add(manager.qualifiedId());
            }

            List<String> candidateIds =
                    applyLowPriority(mergeCandidates(vanillaIds, customIds), lowPriorityIds);
            if (candidateIds.isEmpty()) return;

            String chosen = candidateIds.get(RANDOM.nextInt(candidateIds.size()));
            TimedEffectManager manager = TimedEffectManager.byQualifiedId(chosen);
            if (manager != null) {
                manager.removeEffect(bukkitPlayer);
            } else {
                cePlayer.removePotionEffect(Key.of(chosen));
            }
        });
    }

    static List<String> applyLowPriority(List<String> candidateIds, Set<String> lowPriorityIds) {
        if (candidateIds.isEmpty() || lowPriorityIds.isEmpty()) return candidateIds;
        boolean hasNormal = candidateIds.stream().anyMatch(id -> !lowPriorityIds.contains(id));
        return hasNormal ? candidateIds : List.of();
    }

    static List<String> mergeCandidates(List<String> vanillaIds, List<String> customIds) {
        List<String> merged = new ArrayList<>(vanillaIds.size() + customIds.size());
        merged.addAll(vanillaIds);
        for (String id : customIds) {
            if (!merged.contains(id)) merged.add(id);
        }
        return merged;
    }

    public static <CTX extends Context> FunctionFactory<CTX, RandomRemoveEffectFunction<CTX>> factory(
            java.util.function.Function<ConfigSection, Condition<CTX>> conditionFactory) {
        return new Factory<>(conditionFactory);
    }

    private static class Factory<CTX extends Context>
            extends AbstractFactory<CTX, RandomRemoveEffectFunction<CTX>> {

        public Factory(java.util.function.Function<ConfigSection, Condition<CTX>> factory) {
            super(factory);
        }

        @Override
        public RandomRemoveEffectFunction<CTX> create(ConfigSection section) {
            return new RandomRemoveEffectFunction<>(
                    getPredicates(section),
                    section.getBoolean("harmful_only", false)
            );
        }
    }
}
