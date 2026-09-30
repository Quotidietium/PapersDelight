package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.util.WorldLookup;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

public final class JugGate {

    public interface Bridge {
        InteractionResult interact(org.bukkit.entity.Player player, Block block);

        @Nullable
        BlockEntityController createController(Object blockEntity);

        int analogSignal(Block block);

        void onPlaced(Block block);
    }

    private static volatile Bridge bridge;

    private JugGate() {
    }

    public static void install(@Nullable Bridge implementation) {
        bridge = implementation;
    }

    public static void uninstall() {
        bridge = null;
    }

    @Nullable
    public static Bridge bridge() {
        return bridge;
    }

    public static boolean available() {
        return bridge != null;
    }

    public static InteractionResult interact(@Nullable org.bukkit.entity.Player player, @Nullable Block block) {
        Bridge current = bridge;
        return current == null || player == null || block == null ? InteractionResult.PASS : current.interact(player, block);
    }

    public static BlockEntityController createController(@Nullable Object blockEntity) {
        Bridge current = bridge;
        BlockEntityController controller = current == null || blockEntity == null
                ? null
                : current.createController(blockEntity);
        return controller != null ? controller : inactiveController(blockEntity);
    }

    private static BlockEntityController inactiveController(@Nullable Object blockEntity) {
        return new JugInactiveBlockEntityController(
                blockEntity instanceof net.momirealms.craftengine.core.block.entity.BlockEntity entity ? entity : null);
    }

    public static int analogSignal(@Nullable Block block) {
        Bridge current = bridge;
        return current == null || block == null ? 0 : current.analogSignal(block);
    }

    public static void onPlace(@Nullable Block block) {
        Bridge current = bridge;
        if (current != null && block != null) current.onPlaced(block);
    }

    @Nullable
    public static Block blockFromPlaceArgs(@Nullable Object[] args) {
        return blockFromArgs(args, 0, 1);
    }

    @Nullable
    public static Block blockFromSignalArgs(@Nullable Object[] args) {
        return blockFromArgs(args, 1, 2);
    }

    @Nullable
    private static Block blockFromArgs(@Nullable Object[] args, int levelIndex, int positionIndex) {
        if (args == null || args.length <= positionIndex) return null;
        World world = worldOf(args[levelIndex]);
        if (world == null) return null;
        try {
            BlockPos position = LocationUtils.fromBlockPos(args[positionIndex]);
            return world.getBlockAt(position.x(), position.y(), position.z());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static World worldOf(@Nullable Object level) {
        return WorldLookup.worldOf(level);
    }
}
