package dev.tako.papersdelight.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class ParticleVisibility {

    private ParticleVisibility() {}

    public static void register(Plugin plugin) { }

    public static void clear() { }

    public static boolean hasNearbyViewer(Block block, double rangeBlocks) {
        if (block == null || rangeBlocks <= 0.0D) return true;

        World world = block.getWorld();
        if (world == null) return true;

        double bx = block.getX() + 0.5D;
        double by = block.getY() + 0.5D;
        double bz = block.getZ() + 0.5D;
        Location center = new Location(world, bx, by, bz);
        return !world.getNearbyPlayers(center, rangeBlocks).isEmpty();
    }
}
