package dev.tako.papersdelight.mechanic.villager;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import dev.tako.papersdelight.bridge.NMSHelper;
import dev.tako.papersdelight.bridge.api.VillagerCropRules;
import dev.tako.papersdelight.mechanic.farm.AdvancedCropBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.DoubleCropBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.RopedCropBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.type.DoubleBlockHalf;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VillagerHarvestManager implements Listener, VillagerCropRules {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    final JavaPlugin plugin;
    private volatile VillagerConfig config;

    private volatile Map<String, List<String>> replantIndex;

    private final Map<java.util.UUID, PlantingMemo> plantingMemos = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile int plantingMemoTick = -1;

    private record PlantingMemo(int tick, Object soilState, Planting planting) {
    }

    public VillagerHarvestManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = VillagerConfig.load();
    }

    public void start() {
        refreshLoadedFarmers();
    }

    public void reload() {
        this.config = VillagerConfig.load();
        this.replantIndex = null;
        refreshLoadedFarmers();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Villager villager && VillagerSupport.isFarmer(villager)) {
            scheduleInstall(villager);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (var entity : event.getEntities()) {
            if (entity instanceof Villager villager && VillagerSupport.isFarmer(villager)) {
                scheduleInstall(villager);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        scheduleInstall(event.getEntity());
    }

    private void refreshLoadedFarmers() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                SCHEDULER.getRegionScheduler().runTask(plugin, world.getBlockAt(chunk.getX() << 4, 64, chunk.getZ() << 4).getLocation(), () -> {
                    if (!chunk.isLoaded()) return;
                    for (var entity : chunk.getEntities()) {
                        if (entity instanceof Villager villager && VillagerSupport.isFarmer(villager)) {
                            scheduleInstall(villager);
                        }
                    }
                });
            }
        }
    }

    private void scheduleInstall(Villager villager) {
        SCHEDULER.getEntityScheduler().runTaskLater(plugin, villager, st -> {
            if (!villager.isValid() || villager.isDead()) return;
            VillagerConfig current = config;
            NMSHelper.installVillagerCropHarvestBehavior(
                    villager,
                    current.harvestEnabled() && VillagerSupport.isFarmer(villager) ? this : null
            );
        }, 1L);
    }

    @Override
    public boolean isHarvestable(Block block) {
        VillagerConfig current = config;
        if (!current.harvestEnabled()) return false;

        ImmutableBlockState state = CraftEngineUtil.getCustomBlockState(block);
        if (state == null) return false;

        RopedCropBlockBehavior roped = state.behavior().getFirst(RopedCropBlockBehavior.class);
        if (roped != null) {
            if (!roped.canHarvestByVillagers) return false;

            if ("true".equals(CraftEngineUtil.getCustomBlockProperty(block, "ropelogged"))) return false;
            return roped.isMaxAge(state);
        }

        DoubleCropBlockBehavior doubled = state.behavior().getFirst(DoubleCropBlockBehavior.class);
        if (doubled != null) {
            if (!doubled.canHarvestByVillagers) return false;

            return state.get(doubled.halfProperty) == DoubleBlockHalf.UPPER
                    && state.get(doubled.ageProperty) >= doubled.upperMaxAge;
        }

        AdvancedCropBlockBehavior advanced = state.behavior().getFirst(AdvancedCropBlockBehavior.class);
        return advanced != null && advanced.canHarvestByVillagers && advanced.isMaxAge(state);
    }

    @Override
    public boolean tryHarvest(Block block, Villager villager) {
        return isHarvestable(block) && CraftEngineBlocks.remove(block, null, false, true, true);
    }

    @Override
    public boolean canPlant(Block block, Villager villager) {
        if (block == null || !block.getType().isAir()) return false;
        return plantingFor(villager, block.getRelative(0, -1, 0)) != null;
    }

    @Override
    public boolean tryPlant(Block block, Villager villager) {
        VillagerConfig current = config;
        if (!current.harvestEnabled() || block == null || !block.getType().isAir()) return false;

        Planting planting = plantingFor(villager, block.getRelative(0, -1, 0));
        if (planting == null) return false;

        BlockDefinition definition = CraftEngineBlocks.byId(Key.of(planting.cropId()));
        if (definition == null) return false;
        BlockData targetData = CraftEngineBlocks.getBukkitBlockData(definition.defaultState());
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(villager, block, targetData);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        ItemStack currentSeed = villager.getInventory().getItem(planting.slot());
        if (!VillagerSupport.matchesItemId(currentSeed, planting.seedId())) return false;
        if (!CraftEngineUtil.placeCustomBlock(block, planting.cropId())) return false;

        int remaining = currentSeed.getAmount() - 1;
        if (remaining <= 0) villager.getInventory().setItem(planting.slot(), null);
        else currentSeed.setAmount(remaining);
        return true;
    }

    private static boolean isSupportedSoil(String cropId, Object soilState) {
        if (soilState == null) return false;
        BlockDefinition definition = CraftEngineBlocks.byId(Key.of(cropId));
        if (definition == null) return false;
        ImmutableBlockState state;
        try {
            state = definition.defaultState();
        } catch (Throwable ignored) {
            return false;
        }
        if (state == null) return false;

        RopedCropBlockBehavior roped = state.behavior().getFirst(RopedCropBlockBehavior.class);
        if (roped != null) return roped.isSupportedSoilState(soilState);
        DoubleCropBlockBehavior doubled = state.behavior().getFirst(DoubleCropBlockBehavior.class);
        if (doubled != null) return doubled.isSupportedSoilState(soilState);
        AdvancedCropBlockBehavior advanced = state.behavior().getFirst(AdvancedCropBlockBehavior.class);
        return advanced != null && advanced.isSupportedSoilState(soilState);
    }

    private Planting plantingFor(Villager villager, Block soil) {
        int tick = Bukkit.getCurrentTick();
        if (tick != plantingMemoTick) {
            plantingMemos.clear();
            plantingMemoTick = tick;
        }
        Object soilState = soil == null ? null : BlockStateUtils.getBlockState(soil);
        java.util.UUID id = villager.getUniqueId();
        PlantingMemo memo = plantingMemos.get(id);
        if (memo != null && memo.tick() == tick && memo.soilState() == soilState) return memo.planting();

        Planting planting = findPlanting(villager.getInventory(), replantIndex(), soilState);
        plantingMemos.put(id, new PlantingMemo(tick, soilState, planting));
        return planting;
    }

    private Map<String, List<String>> replantIndex() {
        Map<String, List<String>> cached = replantIndex;
        if (cached != null) return cached;

        Map<String, List<String>> built = new HashMap<>();
        for (var entry : CraftEngineBlocks.loadedBlocks().entrySet()) {
            BlockDefinition definition = entry.getValue();
            ImmutableBlockState state;
            try {
                state = definition.defaultState();
            } catch (Throwable ignored) {
                continue;
            }
            if (state == null || !canReplant(state)) continue;

            Key seedKey = state.settings() == null ? null : state.settings().itemId();
            if (seedKey == null) continue;

            built.computeIfAbsent(seedKey.toString(), k -> new ArrayList<>())
                    .add(entry.getKey().toString());
        }

        Map<String, List<String>> immutable = new HashMap<>();
        for (var e : built.entrySet()) {
            immutable.put(e.getKey(), List.copyOf(e.getValue()));
        }
        Map<String, List<String>> result = Map.copyOf(immutable);
        replantIndex = result;
        return result;
    }

    private static boolean canReplant(ImmutableBlockState state) {
        RopedCropBlockBehavior roped = state.behavior().getFirst(RopedCropBlockBehavior.class);
        if (roped != null) return roped.canReplantByVillagers;
        DoubleCropBlockBehavior doubled = state.behavior().getFirst(DoubleCropBlockBehavior.class);
        if (doubled != null) return doubled.canReplantByVillagers;
        AdvancedCropBlockBehavior advanced = state.behavior().getFirst(AdvancedCropBlockBehavior.class);
        return advanced != null && advanced.canReplantByVillagers;
    }

    private static Planting findPlanting(Inventory inventory, Map<String, List<String>> replantMap, Object soilState) {
        if (replantMap.isEmpty() || soilState == null) return null;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            String seedId = VillagerSupport.resolveSeedId(stack);
            if (seedId == null) continue;
            List<String> crops = replantMap.get(seedId);
            if (crops == null) continue;
            for (String cropId : crops) {
                if (isSupportedSoil(cropId, soilState)) {
                    return new Planting(slot, seedId, cropId);
                }
            }
        }
        return null;
    }

    private record Planting(int slot, String seedId, String cropId) {}
}
