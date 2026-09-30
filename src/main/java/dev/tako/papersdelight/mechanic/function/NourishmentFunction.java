package dev.tako.papersdelight.mechanic.function;

import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.AbstractConditionalFunction;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.plugin.context.number.NumberProvider;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import dev.tako.papersdelight.mechanic.nourishment.NourishmentManager;
import org.bukkit.entity.Player;

import java.util.List;

public final class NourishmentFunction<CTX extends Context>
        extends AbstractConditionalFunction<CTX> {

    private final NumberProvider duration;

    private NourishmentFunction(List<Condition<CTX>> predicates, NumberProvider duration) {
        super(predicates);
        this.duration = duration;
    }

    @Override
    protected void runInternal(CTX ctx) {
        ctx.getOptionalParameter(DirectContextParameters.PLAYER).ifPresent(cePlayer -> {
            if (cePlayer.platformPlayer() instanceof Player player) {
                NourishmentManager mgr = NourishmentManager.getInstance();
                if (mgr != null) {
                    mgr.applyNourishment(player, this.duration.getInt(ctx));
                }
            }
        });
    }

    public static <CTX extends Context> FunctionFactory<CTX, NourishmentFunction<CTX>> factory(
            java.util.function.Function<ConfigSection, Condition<CTX>> conditionFactory) {
        return new Factory<>(conditionFactory);
    }

    private static class Factory<CTX extends Context>
            extends AbstractFactory<CTX, NourishmentFunction<CTX>> {

        public Factory(java.util.function.Function<ConfigSection, Condition<CTX>> factory) {
            super(factory);
        }

        @Override
        public NourishmentFunction<CTX> create(ConfigSection section) {
            return new NourishmentFunction<>(
                    getPredicates(section),
                    section.getNumber("duration", ConfigConstants.CONSTANT_TWENTY)
            );
        }
    }
}
