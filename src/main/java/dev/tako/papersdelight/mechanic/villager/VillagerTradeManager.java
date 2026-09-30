package dev.tako.papersdelight.mechanic.villager;

import dev.tako.papersdelight.bridge.NMSHelper;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import dev.tako.papersdelight.bridge.api.VillagerTradePool;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class VillagerTradeManager implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private static final long PATROL_INTERVAL_TICKS = 200L;

    final JavaPlugin plugin;
    private volatile VillagerConfig config;

    public VillagerTradeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = VillagerConfig.load();
    }

    public void start() {
        installTradePool();
        refreshLoadedVillagers();
        startSharingFixPatrol();
    }

    @EventHandler
    public void onCraftEngineReload(net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent event) {
        installTradePool();
    }

    public void reload() {
        this.config = VillagerConfig.load();
        installTradePool();
        refreshLoadedVillagers();
    }

    private void startSharingFixPatrol() {
        SCHEDULER.getGlobalRegionScheduler().runTaskTimer(plugin, () -> {
            for (var it = VillagerSupport.tracked().iterator(); it.hasNext(); ) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(it.next());
                if (entity == null) {
                    it.remove();
                    continue;
                }
                if (!(entity instanceof Villager villager)) continue;
                SCHEDULER.getEntityScheduler().runTask(plugin, villager, () -> {
                    if (!villager.isValid() || villager.isDead()) return;
                    if (NMSHelper.villagerNeedsItemSharingFix(villager)) {
                        NMSHelper.installVillagerItemSharingFix(villager);
                    }
                });
            }
        }, PATROL_INTERVAL_TICKS, PATROL_INTERVAL_TICKS);
    }

    private void installTradePool() {

        if (!craftEngineItemsReady()) return;

        VillagerConfig current = config;
        List<VillagerTradePool.BuyEntry> buys = new ArrayList<>();
        List<VillagerTradePool.SellEntry> sells = new ArrayList<>();

        if (current.buyEnabled()) {
            for (VillagerConfig.BuyTrade trade : current.buyTrades()) {
                ItemStack ingredient = VillagerSupport.resolveItem(plugin, trade.itemId(), trade.amount());
                if (ingredient == null) continue;
                buys.add(new VillagerTradePool.BuyEntry(
                        trade.level(), ingredient, 1,
                        trade.maxUses(), trade.villagerXp(), (float) trade.priceMultiplier()));
            }
        }

        if (current.sellEnabled()) {
            for (VillagerConfig.SellTrade trade : current.sellTrades()) {
                ItemStack result = VillagerSupport.resolveItem(plugin, trade.itemId(), trade.amount());
                if (result == null) continue;
                sells.add(new VillagerTradePool.SellEntry(
                        result, trade.emeraldCost(),
                        trade.maxUses(), trade.villagerXp(), (float) trade.priceMultiplier()));
            }
        }

        try {
            NMSHelper.installVillagerTradePool(new VillagerTradePool(buys, sells));
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning(dev.tako.papersdelight.config.ConfigManager.getOr(
                    "villager_trade_pool_install_fail", "村民交易池注入失败，将保留原版交易表：%error%")
                    .replace("%error%", String.valueOf(e)));
        }
    }

    private boolean craftEngineItemsReady() {
        try {
            return !net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Villager villager) scheduleSharingFix(villager);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (var entity : event.getEntities()) {
            if (entity instanceof Villager villager) scheduleSharingFix(villager);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCareerChange(org.bukkit.event.entity.VillagerCareerChangeEvent event) {
        scheduleSharingFix(event.getEntity());
    }

    private void refreshLoadedVillagers() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                SCHEDULER.getRegionScheduler().runTask(
                        plugin,
                        world.getBlockAt(chunk.getX() << 4, 64, chunk.getZ() << 4).getLocation(),
                        () -> {
                            if (!chunk.isLoaded()) return;
                            for (var entity : chunk.getEntities()) {
                                if (entity instanceof Villager villager) scheduleSharingFix(villager);
                            }
                        }
                );
            }
        }
    }

    private void scheduleSharingFix(Villager villager) {
        VillagerSupport.track(villager.getUniqueId());
        SCHEDULER.getEntityScheduler().runTaskLater(plugin, villager, st -> {
            if (!villager.isValid() || villager.isDead()) return;
            NMSHelper.installVillagerItemSharingFix(villager);
        }, 1L);
    }
}
