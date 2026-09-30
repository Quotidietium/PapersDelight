package dev.tako.papersdelight.mechanic.function;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.Condition;
import net.momirealms.craftengine.core.plugin.context.Context;
import net.momirealms.craftengine.core.plugin.context.function.AbstractConditionalFunction;
import net.momirealms.craftengine.core.plugin.context.function.FunctionFactory;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class ChorusTeleportFunction<CTX extends Context>
        extends AbstractConditionalFunction<CTX> {

    private static final double DEFAULT_DIAMETER = 16.0;
    private static final int MAX_ATTEMPTS = 16;

    private final double diameter;

    private ChorusTeleportFunction(List<Condition<CTX>> predicates, double diameter) {
        super(predicates);
        this.diameter = diameter;
    }

    @Override
    protected void runInternal(CTX ctx) {
        ctx.getOptionalParameter(DirectContextParameters.PLAYER).ifPresent(cePlayer -> {
            if (!(cePlayer.platformPlayer() instanceof Player player)) return;

            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }

            Location origin = player.getLocation();
            World world = origin.getWorld();
            ThreadLocalRandom rng = ThreadLocalRandom.current();

            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight() - 1;

            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                double dx = (rng.nextDouble() - 0.5) * diameter;
                double dy = (rng.nextDouble() - 0.5) * diameter;
                double dz = (rng.nextDouble() - 0.5) * diameter;

                int targetX = (int) Math.floor(origin.getX() + dx);
                int targetZ = (int) Math.floor(origin.getZ() + dz);
                int searchY = (int) Math.clamp(origin.getY() + dy, minY, maxY);

                Location dest = findSafeLanding(world, targetX, targetZ, searchY);
                if (dest != null) {
                    player.teleport(dest);
                    player.setFallDistance(0);
                    world.playSound(dest, Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    return;
                }
            }
        });
    }

    private Location findSafeLanding(World world, int x, int z, int startY) {
        int limit = Math.max(startY - (int) Math.ceil(this.diameter), world.getMinHeight());
        for (int y = Math.min(startY, world.getMaxHeight() - 1); y > limit; y--) {
            if (!world.getBlockAt(x, y, z).isPassable()
                    && world.getBlockAt(x, y + 1, z).isPassable()
                    && world.getBlockAt(x, y + 2, z).isPassable()) {
                return new Location(world, x + 0.5, y + 1.2, z + 0.5);
            }
        }
        return null;
    }

    public static <CTX extends Context> FunctionFactory<CTX, ChorusTeleportFunction<CTX>> factory(
            java.util.function.Function<ConfigSection, Condition<CTX>> conditionFactory) {
        return new Factory<>(conditionFactory);
    }

    private static class Factory<CTX extends Context>
            extends AbstractFactory<CTX, ChorusTeleportFunction<CTX>> {

        public Factory(java.util.function.Function<ConfigSection, Condition<CTX>> factory) {
            super(factory);
        }

        @Override
        public ChorusTeleportFunction<CTX> create(ConfigSection section) {
            return new ChorusTeleportFunction<>(
                    getPredicates(section),
                    section.getDouble("diameter", DEFAULT_DIAMETER)
            );
        }
    }
}
