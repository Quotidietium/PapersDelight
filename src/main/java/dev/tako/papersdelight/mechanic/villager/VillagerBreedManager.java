package dev.tako.papersdelight.mechanic.villager;

import dev.tako.papersdelight.bridge.NMSHelper;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class VillagerBreedManager {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private static final int FOOD_LEVEL_TARGET = 12;

    final JavaPlugin plugin;

    private final java.util.Set<java.util.UUID> pendingVillagers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile VillagerConfig config;
    private cn.chengzhimeow.ccscheduler.task.CCTask task;

    public VillagerBreedManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = VillagerConfig.load();
    }

    public void start() {
        stop();
        if (!config.breedEnabled()) return;

        long interval = config.breedIntervalTicks();
        this.task = SCHEDULER.getGlobalRegionScheduler().runTaskTimer(plugin, this::scanAllWorlds, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        this.config = VillagerConfig.load();
        start();
    }

    private void scanAllWorlds() {
        for (var it = VillagerSupport.tracked().iterator(); it.hasNext(); ) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(it.next());
            if (entity == null) {
                it.remove();
                continue;
            }
            if (!(entity instanceof Villager villager)) continue;
            if (villager.getWorld().getPlayers().isEmpty()) continue;
            if (!pendingVillagers.add(villager.getUniqueId())) continue;
            SCHEDULER.getEntityScheduler().runTask(plugin, villager, () -> {
                try {
                    feedIfHungry(villager);
                } finally {
                    pendingVillagers.remove(villager.getUniqueId());
                }
            });
        }
    }

    private void feedIfHungry(Villager villager) {
        if (!villager.isValid() || villager.isDead()) return;

        int foodLevel = NMSHelper.getVillagerFoodLevel(villager);

        if (foodLevel < 0) return;
        if (foodLevel >= FOOD_LEVEL_TARGET) return;

        Inventory inventory = villager.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;

            Integer points = matchFoodPoints(stack);
            if (points == null) continue;

            if (!NMSHelper.addVillagerFoodLevel(villager, points)) return;

            int remaining = stack.getAmount() - 1;
            if (remaining <= 0) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(remaining);
                inventory.setItem(slot, stack);
            }
            return;
        }
    }

    private static Integer matchFoodPoints(ItemStack stack) {
        if (!CraftEngineItems.isCustomItem(stack)) return null;
        var def = CraftEngineItems.byItemStack(stack);
        if (def == null) return null;
        int points = VillagerFoodPointSetting.foodPoint(def);
        return points > 0 ? points : null;
    }
}
