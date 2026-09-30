package dev.tako.papersdelight.mechanic.villager;

import dev.tako.papersdelight.bridge.NMSHelper;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public final class VillagerPickupManager implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private static final Key PLANTABLE_SEEDS_TAG = Key.of("minecraft:villager_plantable_seeds");
    private static final Key VILLAGER_PICKS_UP_TAG = Key.of("minecraft:villager_picks_up");
    private static final Set<Material> VANILLA_VILLAGER_PICKUP_MATERIALS = Set.of(
            Material.BREAD,
            Material.WHEAT,
            Material.BEETROOT,
            Material.WHEAT_SEEDS,
            Material.POTATO,
            Material.CARROT,
            Material.BEETROOT_SEEDS,
            Material.TORCHFLOWER_SEEDS,
            Material.PITCHER_POD
    );

    private static final double HORIZONTAL_RANGE = 1.5;
    private static final double VERTICAL_RANGE = 1.0;

    final JavaPlugin plugin;

    private final Set<java.util.UUID> trackedItems = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, cn.chengzhimeow.ccscheduler.task.CCTask> itemTasks =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile VillagerConfig config;

    public VillagerPickupManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = VillagerConfig.load();
    }

    public void start() {
        stop();
        refreshLoadedItems();
    }

    public void stop() {
        itemTasks.values().forEach(cn.chengzhimeow.ccscheduler.task.CCTask::cancel);
        itemTasks.clear();
        trackedItems.clear();
    }

    public void reload() {
        this.config = VillagerConfig.load();
        start();
    }

    private void refreshLoadedItems() {
        if (!config.pickupEnabled()) return;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                SCHEDULER.getRegionScheduler().runTask(plugin,
                        world.getBlockAt(chunk.getX() << 4, 64, chunk.getZ() << 4).getLocation(),
                        () -> {
                            if (!chunk.isLoaded()) return;
                            for (Entity entity : chunk.getEntities()) {
                                if (entity instanceof Item item) trackItem(item);
                            }
                        });
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Item item) trackItem(item);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Item item) trackItem(item);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) trackItem(item);
        }
    }

    static boolean shouldTrackItem(boolean pickupEnabled, boolean valid, boolean tagged) {
        return pickupEnabled && valid && tagged;
    }

    private void trackItem(Item item) {
        if (!shouldTrackItem(config.pickupEnabled(), item.isValid(), hasVillagerPickupTag(item.getItemStack()))) return;
        java.util.UUID id = item.getUniqueId();
        if (!trackedItems.add(id)) return;
        long interval = config.pickupIntervalTicks();
        CCTask handle = SCHEDULER.getEntityScheduler().runTaskTimer(
                plugin,
                item,
                () -> scanNearbyVillagers(item),
                interval,
                interval
        );
        itemTasks.put(id, handle);
    }

    private void scanNearbyVillagers(Item item) {
        if (!shouldTrackItem(config.pickupEnabled(), item.isValid() && !item.isDead(),
                hasVillagerPickupTag(item.getItemStack()))) {
            untrackItem(item.getUniqueId());
            return;
        }
        for (Entity nearby : item.getNearbyEntities(HORIZONTAL_RANGE, VERTICAL_RANGE, HORIZONTAL_RANGE)) {
            if (!(nearby instanceof Villager villager) || !villager.isValid() || villager.isDead()) continue;
            if (item.getPickupDelay() > 0 || !Bukkit.isOwnedByCurrentRegion(villager)
                    || !Bukkit.isOwnedByCurrentRegion(item)) continue;
            if (NMSHelper.villagerPickUpItemEntity(villager, item)) {
                if (!item.isValid() || item.isDead()) untrackItem(item.getUniqueId());
                return;
            }
        }
    }

    private void untrackItem(java.util.UUID id) {
        trackedItems.remove(id);
        cn.chengzhimeow.ccscheduler.task.CCTask task = itemTasks.remove(id);
        if (task != null) task.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;

        ItemStack stack = event.getItem().getItemStack();
        if (!VANILLA_VILLAGER_PICKUP_MATERIALS.contains(stack.getType())) return;

        if (!CraftEngineItems.isCustomItem(stack)) return;

        if (!hasVillagerPickupTag(stack)) event.setCancelled(true);
    }

    private boolean hasVillagerPickupTag(ItemStack stack) {

        if (!CraftEngineItems.isCustomItem(stack)) return false;
        var definition = CraftEngineItems.byItemStack(stack);
        return definition != null
                && (definition.is(PLANTABLE_SEEDS_TAG) || definition.is(VILLAGER_PICKS_UP_TAG));
    }
}
