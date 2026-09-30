package dev.tako.papersdelight.mechanic.function;

import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.condition.ConditionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;

import java.util.Optional;

public final class IsSneakingCondition<CTX extends Context> implements Condition<CTX> {

    private IsSneakingCondition() {
    }

    @Override
    public boolean test(CTX ctx) {
        Optional<Player> player = ctx.getOptionalParameter(DirectContextParameters.PLAYER);
        return player.map(Player::isSneaking).orElse(false);
    }

    public static <CTX extends Context> ConditionFactory<CTX, IsSneakingCondition<CTX>> factory() {
        return new Factory<>();
    }

    private static class Factory<CTX extends Context> implements ConditionFactory<CTX, IsSneakingCondition<CTX>> {

        @Override
        public IsSneakingCondition<CTX> create(ConfigSection section) {
            return new IsSneakingCondition<>();
        }
    }
}
