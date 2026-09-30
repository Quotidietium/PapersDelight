package dev.tako.papersdelight.mechanic.basket;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BasketBlockBehavior extends BukkitBlockBehavior {
    public static final BlockBehaviorFactory<BasketBlockBehavior> FACTORY = new Factory();

    private static final Set<String> REGISTERED_BLOCK_IDS = ConcurrentHashMap.newKeySet();

    public final boolean redstoneLock;

    private static int collectInterval = 8;

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:basket"), FACTORY);
    }

    private BasketBlockBehavior(BlockDefinition blockDefinition, boolean redstoneLock) {
        super(blockDefinition);
        this.redstoneLock = redstoneLock;
    }

    public static boolean isBasket(Block block) {
        String id = CraftEngineUtil.getCustomBlockId(block);
        return id != null && REGISTERED_BLOCK_IDS.contains(id);
    }

    public static Set<String> getRegisteredBlockIds() {
        return REGISTERED_BLOCK_IDS;
    }

    public static int getCollectInterval() {
        return collectInterval;
    }

    public static String getFacing(Block block) {
        String facing = CraftEngineUtil.getCustomBlockProperty(block, "facing");
        return (facing != null && !facing.isEmpty()) ? facing.toLowerCase() : "up";
    }

    public static boolean isEnabled(Block block) {
        String enabled = CraftEngineUtil.getCustomBlockProperty(block, "enabled");
        if (enabled != null) return "true".equalsIgnoreCase(enabled);
        return !block.isBlockPowered();
    }

    @Override
    public void neighborChanged(Object thisBlock, Object[] args) {
        if (!redstoneLock) return;

        World world = NMSHelper.bukkitWorldOf(args[1]);
        if (world == null) return;

        BlockPos cePos = LocationUtils.fromBlockPos(args[2]);
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());
        BasketManager.updateRedstoneState(block);
    }

    private static class Factory implements BlockBehaviorFactory<BasketBlockBehavior> {
        private static final String[] REDSTONE_LOCK = {"redstone_lock", "redstone-lock"};
        private static final String[] COLLECT_INTERVAL = {"collect_interval", "collect-interval"};

        @Override
        public BasketBlockBehavior create(BlockDefinition block, ConfigSection section) {
            REGISTERED_BLOCK_IDS.add(block.id().toString());
            int interval = section.getInt(COLLECT_INTERVAL, 1);
            if (interval > 0) collectInterval = interval;
            return new BasketBlockBehavior(
                    block,
                    section.getBoolean(REDSTONE_LOCK, true)
            );
        }
    }
}
