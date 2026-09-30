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
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class EndermanGristleTeleportFunction<CTX extends Context>
        extends AbstractConditionalFunction<CTX> {

    private static final double DEFAULT_DAMAGE = 0.3;
    private static final int MAX_ATTEMPTS = 16;
    private static final double XZ_RANGE = 8.0;
    private static final int Y_OFFSET_BASE = 9;
    private static final int Y_OFFSET_RANGE = 24;

    private final double damage;

    private EndermanGristleTeleportFunction(List<Condition<CTX>> predicates, double damage) {
        super(predicates);
        this.damage = damage;
    }

    @Override
    protected void runInternal(CTX ctx) {
        ctx.getOptionalParameter(DirectContextParameters.PLAYER).ifPresent(cePlayer -> {
            if (!(cePlayer.platformPlayer() instanceof Player player)) return;

            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }

            Location origin = player.getLocation().clone();
            World world = origin.getWorld();
            ThreadLocalRandom rng = ThreadLocalRandom.current();

            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight() - 1;

            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                double dx = (rng.nextDouble() - 0.5) * XZ_RANGE;
                double dz = (rng.nextDouble() - 0.5) * XZ_RANGE;
                double dy = rng.nextInt(Y_OFFSET_RANGE) + Y_OFFSET_BASE;

                double targetX = origin.getX() + dx;
                double targetY = Math.clamp(origin.getY() + dy, minY, maxY);
                double targetZ = origin.getZ() + dz;

                Location dest = findSafeLanding(world, targetX, targetY, targetZ);
                if (dest == null) continue;

                player.teleport(dest);

                Location postLoc = player.getLocation();
                if (!postLoc.getBlock().isPassable()
                        || !postLoc.clone().add(0, 1, 0).getBlock().isPassable()
                        || player.isInWaterOrBubbleColumn()
                        || player.isInLava()) {
                    player.teleport(origin);
                    continue;
                }

                applyDamage(player);
                world.playSound(dest, Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                player.setFallDistance(0);
                return;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void applyDamage(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        double maxHp = player.getMaxHealth();
        double currentHp = player.getHealth();
        double multiplier = (currentHp < maxHp * 0.3) ? 1.5 : this.damage;
        double damageAmount = currentHp * multiplier;
        player.damage(damageAmount, DamageSource.builder(DamageType.FALL).build());
    }

    private static Location findSafeLanding(World world, double x, double startY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int limit = world.getMinHeight();

        for (int y = Math.min((int) startY, world.getMaxHeight() - 1); y > limit; y--) {
            if (world.getBlockAt(bx, y, bz).getType().isSolid()
                    && world.getBlockAt(bx, y + 1, bz).isPassable()
                    && world.getBlockAt(bx, y + 2, bz).isPassable()) {
                return new Location(world, x, y + 1.2, z);
            }
        }
        return null;
    }

    public static <CTX extends Context> FunctionFactory<CTX, EndermanGristleTeleportFunction<CTX>> factory(
            java.util.function.Function<ConfigSection, Condition<CTX>> conditionFactory) {
        return new Factory<>(conditionFactory);
    }

    private static class Factory<CTX extends Context>
            extends AbstractFactory<CTX, EndermanGristleTeleportFunction<CTX>> {

        public Factory(java.util.function.Function<ConfigSection, Condition<CTX>> factory) {
            super(factory);
        }

        @Override
        public EndermanGristleTeleportFunction<CTX> create(ConfigSection section) {
            return new EndermanGristleTeleportFunction<>(
                    getPredicates(section),
                    section.getDouble("damage", DEFAULT_DAMAGE)
            );
        }
    }
}
