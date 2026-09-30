package dev.tako.papersdelight.mechanic.skillet;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import dev.tako.papersdelight.util.ItemMetaUtil;
import dev.tako.papersdelight.common.TickBatch;
import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.config.ConfigManager.SoundConfig;
import dev.tako.papersdelight.heat.HeatSourceService;
import dev.tako.papersdelight.config.SkilletConfig;
import dev.tako.papersdelight.api.protection.ProtectionGate;
import dev.tako.papersdelight.recipe.CampfireRecipeUtil;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.support.HandheldSkilletSupport;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import dev.tako.papersdelight.util.ParticleVisibility;
import dev.tako.papersdelight.util.TextUtil;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class SkilletManager implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private final JavaPlugin plugin;

    static volatile SkilletManager instance;

    final Set<Location> knownSkillets = ConcurrentHashMap.newKeySet();

    private final Map<Location, List<ItemDisplay>> displayEntities = new ConcurrentHashMap<>();

    private final Set<Location> particleSkillets = ConcurrentHashMap.newKeySet();
    private final Set<Location> restored = ConcurrentHashMap.newKeySet();
    private final Map<UUID, HandheldSession> handheldSessions = new ConcurrentHashMap<>();
    private final NamespacedKey handheldIngredientKey;
    private final NamespacedKey handheldSkilletOriginalDamageKey;
    private final NamespacedKey handheldSkilletOriginalItemModelKey;
    private final ItemModelGenerator handheldIngredientModels;

    private volatile boolean handheldCookingSupported;

    private boolean initialScanDone;

    private volatile SkilletConfig config;

    public SkilletManager(JavaPlugin plugin, ItemModelGenerator handheldIngredientModels) {
        this.plugin = plugin;
        this.handheldIngredientModels = handheldIngredientModels;
        this.handheldIngredientKey = new NamespacedKey(plugin, "handheld_skillet_ingredient");
        this.handheldSkilletOriginalDamageKey = new NamespacedKey(plugin, "handheld_skillet_original_damage");
        this.handheldSkilletOriginalItemModelKey = new NamespacedKey(plugin, "handheld_skillet_original_item_model");
    }

    public void load() {

        config = SkilletConfig.load();
        handheldCookingSupported = dev.tako.papersdelight.support.FeatureSupport.handheldSkillet()
                && HandheldSkilletSupport.isSupported(Bukkit.getMinecraftVersion());
        instance = this;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isValid()) continue;
            Runnable restore = () -> {
                restoreHandheldSkilletProgress(player);
                refundEscrowedIngredient(player);
            };
            if (!plugin.isEnabled()) {
                try { restore.run(); } catch (Throwable ignored) { }
                continue;
            }
            SCHEDULER.getEntityScheduler().runTask(plugin, player, restore);
        }
        initialScanDone = false;
    }

    public void stopAll() {
        cancelAllHandheldSessions();
        particleSkillets.clear();
        for (List<ItemDisplay> list : displayEntities.values()) {
            for (ItemDisplay d : list) {
                if (d == null || !d.isValid()) continue;
                if (!plugin.isEnabled()) {
                    try { d.remove(); } catch (Throwable ignored) { }
                    continue;
                }
                SCHEDULER.getEntityScheduler().runTask(plugin, d, d::remove);
            }
        }
        displayEntities.clear();
        knownSkillets.clear();
        restored.clear();
    }

    public void discoverAllSkillets() {
        List<Chunk> all = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) all.addAll(List.of(w.getLoadedChunks()));
        if (all.isEmpty()) { initialScanDone = true; return; }
        scanBatch(all, 0);
    }

    private void scanBatch(List<Chunk> chunks, int start) {
        int end = Math.min(start + 4, chunks.size());
        for (int i = start; i < end; i++) {
            Chunk c = chunks.get(i);
            SCHEDULER.getRegionScheduler().runTask(plugin, c.getWorld(), c.getX(), c.getZ(), () -> scanChunk(c.getWorld(), c.getX(), c.getZ()));
        }
        if (end < chunks.size()) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(plugin, 2L, () -> scanBatch(chunks, end));
        } else {
            initialScanDone = true;
        }
    }

    boolean isHandheldCookingSupported() {
        return handheldCookingSupported;
    }

    boolean tryStartHandheldCooking(Player player, EquipmentSlot skilletHand) {
        if (!handheldCookingSupported || player == null || skilletHand == null) return false;
        if (handheldSessions.containsKey(player.getUniqueId())) return true;
        if (player.getPersistentDataContainer().has(handheldIngredientKey, PersistentDataType.BYTE_ARRAY)) {
            refundEscrowedIngredient(player);
            return false;
        }

        ItemStack skillet = player.getInventory().getItem(skilletHand);
        if (!CraftEngineUtil.isItem(skillet, "farmersdelight:skillet")) return false;
        if (!isPlayerNearHeatSource(player)) return false;

        EquipmentSlot ingredientHand = skilletHand == EquipmentSlot.HAND
                ? EquipmentSlot.OFF_HAND
                : EquipmentSlot.HAND;
        ItemStack source = player.getInventory().getItem(ingredientHand);
        CampfireRecipe recipe = CampfireRecipeUtil.findRecipe(source);
        if (recipe == null) {
            player.sendActionBar(TextUtil.parse(player,
                    "<!i><lang:item.farmersdelight.skillet.how_to_cook>"));
            return false;
        }
        if (player.isUnderWater()) {
            player.sendActionBar(TextUtil.parse(player,
                    "<!i><lang:item.farmersdelight.skillet.underwater>"));
            return false;
        }

        ItemStack ingredient = source.clone();
        ingredient.setAmount(1);
        setEscrowedIngredient(player, ingredient);
        ItemStack remainder = source.clone();
        remainder.setAmount(source.getAmount() - 1);
        player.getInventory().setItem(ingredientHand, remainder.isEmpty() ? null : remainder);

        int fireAspectLevel = skillet.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        int cookingTicks = SkilletCookingTime.calculate(recipe.getCookingTime(), fireAspectLevel);
        NamespacedKey ingredientModel = handheldIngredientModels.itemDefinitionFor(ingredient);
        HandheldSession session = new HandheldSession(
                skilletHand, ingredientHand,
                recipe.getResult().clone(),
                new HandheldSkilletProgress(cookingTicks),
                ingredientModel,
                handheldIngredientModels.flippedItemDefinitionFor(ingredient)
        );
        beginHandheldSkilletProgress(skillet, session, ingredientModel);
        handheldSessions.put(player.getUniqueId(), session);
        Runnable retired = () -> retireHandheldCooking(player.getUniqueId(), session);
        if (player == null || !player.isValid() || !plugin.isEnabled()) {
            retired.run();
            return true;
        }
        session.task = player.getScheduler().runAtFixedRate(
                plugin,
                st -> tickHandheldCooking(player),
                retired,
                1L,
                1L
        );

        return true;
    }

    private void tickHandheldCooking(Player player) {
        HandheldSession session = handheldSessions.get(player.getUniqueId());
        if (session == null) return;

        ItemStack held = player.getInventory().getItem(session.skilletHand);
        boolean activeUsing = player.hasActiveItem() && player.getActiveItemHand() == session.skilletHand;
        boolean withinActiveUseGrace = session.activeUseGraceTicks > 0;
        if (withinActiveUseGrace) session.activeUseGraceTicks--;
        boolean stillUsing = player.isOnline()
                && !player.isDead()
                && (activeUsing || session.activeUseGraceTicks > 0 || withinActiveUseGrace)
                && CraftEngineUtil.isItem(held, "farmersdelight:skillet");

        Entity grounded = player;
        if (session.flip.updateLanding(grounded.isOnGround())) {
            playSound(player.getLocation(), config.soundAddFood);
            player.clearActiveItem();
            session.activeUseGraceTicks = 2;
            flipHandheldIngredientModel(held, session);
        }
        HandheldSkilletProgress.Result result = session.progress.tick(stillUsing);
        int stage = HandheldSkilletProgressBar.stageFor(
                session.progress.elapsedTicks(), session.progress.totalTicks());
        if (stage != session.lastDisplayedStage) {
            showHandheldSkilletProgress(held, session, stage);
        }
        if (result == HandheldSkilletProgress.Result.CONTINUE) {
            if (session.flip.consumeSizzleBurst() || ThreadLocalRandom.current().nextInt(50) == 0) {
                playSound(player.getLocation(), config.soundSizzle);
            }
            return;
        }
        if (result == HandheldSkilletProgress.Result.CANCEL) {
            cancelHandheldCooking(player);
            return;
        }

        if (!handheldSessions.remove(player.getUniqueId(), session)) return;
        session.cancelTask();
        restoreHandheldSkilletProgress(player);
        player.clearActiveItem();
        giveOrDrop(player, session.result);
        dev.tako.papersdelight.stats.StatsManager stats =
                dev.tako.papersdelight.stats.StatsManager.getInstance();
        if (stats != null) {
            stats.record(player.getUniqueId(), dev.tako.papersdelight.stats.StatsManager.SKILLET_COOK,
                    CraftEngineUtil.getItemIdentifier(session.result), session.result.getAmount());
        }
    }

    private void cancelHandheldCooking(Player player) {
        HandheldSession session = handheldSessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cancelTask();
        restoreHandheldSkilletProgress(player);
        refundEscrowedIngredient(player, session.ingredientHand);
    }

    private void retireHandheldCooking(UUID playerId, HandheldSession session) {
        handheldSessions.remove(playerId, session);
    }

    private void cancelAllHandheldSessions() {
        for (HandheldSession session : handheldSessions.values()) {
            session.cancelTask();
        }
        handheldSessions.clear();
    }

    private void beginHandheldSkilletProgress(ItemStack stack, HandheldSession session, NamespacedKey ingredientModel) {
        if (!CraftEngineUtil.isItem(stack, "farmersdelight:skillet")) return;
        restoreHandheldSkilletProgress(stack);
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasMaxDamage()) return;
        if (damageable.getMaxDamage() <= 0) return;

        var data = meta.getPersistentDataContainer();
        data.set(handheldSkilletOriginalDamageKey, PersistentDataType.INTEGER, damageable.getDamage());
        NamespacedKey originalItemModel = getHandheldSkilletItemModel(meta);
        if (handheldCookingSupported && ingredientModel != null && setHandheldSkilletItemModel(meta, ingredientModel)) {
            data.set(handheldSkilletOriginalItemModelKey, PersistentDataType.STRING,
                    originalItemModel == null ? "" : originalItemModel.asString());
        }
        stack.setItemMeta(meta);
        showHandheldSkilletProgress(stack, session, 0);
    }

    private void showHandheldSkilletProgress(ItemStack stack, HandheldSession session, int stage) {
        if (!CraftEngineUtil.isItem(stack, "farmersdelight:skillet")) return;
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasMaxDamage()) return;
        int maxDamage = damageable.getMaxDamage();
        if (maxDamage <= 0) return;

        var data = meta.getPersistentDataContainer();
        if (!data.has(handheldSkilletOriginalDamageKey, PersistentDataType.INTEGER)) return;
        damageable.setDamage(HandheldSkilletProgressBar.damageFor(maxDamage, stage));
        stack.setItemMeta(meta);
        session.lastDisplayedStage = stage;
    }

    private void flipHandheldIngredientModel(ItemStack stack, HandheldSession session) {
        if (!CraftEngineUtil.isItem(stack, "farmersdelight:skillet")) return;
        NamespacedKey nextModel = session.flipped ? session.defaultIngredientModel : session.flippedIngredientModel;
        if (nextModel == null || session.defaultIngredientModel.equals(session.flippedIngredientModel)) return;
        ItemMeta meta = stack.getItemMeta();
        if (setHandheldSkilletItemModel(meta, nextModel)) {
            stack.setItemMeta(meta);
            session.flipped = !session.flipped;
        }
    }

    private boolean restoreHandheldSkilletProgress(ItemStack stack) {
        if (!CraftEngineUtil.isItem(stack, "farmersdelight:skillet")) return false;
        ItemMeta meta = stack.getItemMeta();
        var data = meta.getPersistentDataContainer();
        boolean restored = false;

        String originalItemModel = data.get(handheldSkilletOriginalItemModelKey, PersistentDataType.STRING);
        if (originalItemModel != null) {
            NamespacedKey itemModel = originalItemModel.isEmpty() ? null : NamespacedKey.fromString(originalItemModel);
            setHandheldSkilletItemModel(meta, itemModel);
            data.remove(handheldSkilletOriginalItemModelKey);
            restored = true;
        }

        if (meta instanceof Damageable damageable) {
            Integer originalDamage = data.get(handheldSkilletOriginalDamageKey, PersistentDataType.INTEGER);
            if (originalDamage != null) {
                damageable.setDamage(originalDamage);
                data.remove(handheldSkilletOriginalDamageKey);
                restored = true;
            }
        }
        if (restored) stack.setItemMeta(meta);
        return restored;
    }

    private void restoreHandheldSkilletProgress(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && !stack.isEmpty()) restoreHandheldSkilletProgress(stack);
        }
    }

    private static NamespacedKey getHandheldSkilletItemModel(ItemMeta meta) {
        return ItemMetaUtil.getItemModel(meta);
    }

    private static boolean setHandheldSkilletItemModel(ItemMeta meta, NamespacedKey itemModel) {
        return ItemMetaUtil.setItemModel(meta, itemModel);
    }

    private void setEscrowedIngredient(Player player, ItemStack ingredient) {
        player.getPersistentDataContainer().set(
                handheldIngredientKey,
                PersistentDataType.BYTE_ARRAY,
                ingredient.serializeAsBytes()
        );
    }

    private ItemStack getEscrowedIngredient(Player player) {
        byte[] encoded = player.getPersistentDataContainer().get(
                handheldIngredientKey,
                PersistentDataType.BYTE_ARRAY
        );
        if (encoded == null) return null;
        try {
            ItemStack ingredient = ItemStack.deserializeBytes(encoded);
            return ingredient.isEmpty() ? null : ingredient;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(ConfigManager.getOr(
                    "handheld_skillet_ingredient_read_fail", "无法读取玩家 %player% 的手持煎锅托管原料: %error%")
                    .replace("%player%", player.getUniqueId().toString())
                    .replace("%error%", ConfigManager.describeError(exception)));
            clearEscrowedIngredient(player);
            return null;
        }
    }

    private void clearEscrowedIngredient(Player player) {
        player.getPersistentDataContainer().remove(handheldIngredientKey);
    }

    private void refundEscrowedIngredient(Player player) {
        refundEscrowedIngredient(player, null);
    }

    private void refundEscrowedIngredient(Player player, EquipmentSlot preferredHand) {
        ItemStack ingredient = getEscrowedIngredient(player);
        if (ingredient == null) return;
        if (preferredHand != null) {
            ItemStack current = player.getInventory().getItem(preferredHand);
            if (current == null || current.isEmpty()) {
                player.getInventory().setItem(preferredHand, ingredient);
                clearEscrowedIngredient(player);
                return;
            }
            if (current.isSimilar(ingredient) && current.getAmount() + ingredient.getAmount() <= current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + ingredient.getAmount());
                player.getInventory().setItem(preferredHand, current);
                clearEscrowedIngredient(player);
                return;
            }
        }
        giveOrDrop(player, ingredient);
        clearEscrowedIngredient(player);
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        for (ItemStack overflow : player.getInventory().addItem(stack.clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    private static boolean isPlayerNearHeatSource(Player player) {
        if (player.getFireTicks() > 0) return true;
        Block origin = player.getLocation().getBlock();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (HeatSourceService.isActiveHeatSource(origin.getRelative(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHandheldSkilletConsume(PlayerItemConsumeEvent event) {
        if (CraftEngineUtil.isItem(event.getItem(), "farmersdelight:skillet")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHandheldSkilletJoin(PlayerJoinEvent event) {
        restoreHandheldSkilletProgress(event.getPlayer());
        refundEscrowedIngredient(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHandheldSkilletDrop(PlayerDropItemEvent event) {
        restoreHandheldSkilletProgress(event.getItemDrop().getItemStack());
        cancelHandheldCooking(event.getPlayer());
    }

    @EventHandler
    public void onHandheldSkilletQuit(PlayerQuitEvent event) {
        cancelHandheldCooking(event.getPlayer());
    }

    @EventHandler
    public void onHandheldSkilletJump(PlayerJumpEvent event) {
        HandheldSession session = handheldSessions.get(event.getPlayer().getUniqueId());
        if (session != null) session.flip.onJump();
    }

    @EventHandler
    public void onHandheldSkilletDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        HandheldSession session = handheldSessions.remove(player.getUniqueId());
        if (session != null) session.cancelTask();

        if (event.getKeepInventory()) {
            restoreHandheldSkilletProgress(player);
        } else {
            for (ItemStack drop : event.getDrops()) {
                restoreHandheldSkilletProgress(drop);
            }
        }

        ItemStack ingredient = getEscrowedIngredient(player);
        if (ingredient == null) return;
        if (event.getKeepInventory()) {
            giveOrDrop(player, ingredient);
        } else {
            event.getDrops().add(ingredient);
        }
        clearEscrowedIngredient(player);
    }

    private static final class HandheldSession {
        private final EquipmentSlot skilletHand;
        private final EquipmentSlot ingredientHand;
        private final ItemStack result;
        private final HandheldSkilletProgress progress;
        private final NamespacedKey defaultIngredientModel;
        private final NamespacedKey flippedIngredientModel;
        private final HandheldSkilletFlipTracker flip = new HandheldSkilletFlipTracker();
        private int activeUseGraceTicks;
        private boolean flipped;
        private int lastDisplayedStage = -1;
        private ScheduledTask task;

        private HandheldSession(
                EquipmentSlot skilletHand, EquipmentSlot ingredientHand, ItemStack result, HandheldSkilletProgress progress,
                NamespacedKey defaultIngredientModel, NamespacedKey flippedIngredientModel) {
            this.skilletHand = skilletHand;
            this.ingredientHand = ingredientHand;
            this.result = result;
            this.progress = progress;
            this.defaultIngredientModel = defaultIngredientModel;
            this.flippedIngredientModel = flippedIngredientModel;
        }

        private void cancelTask() {
            if (task != null) task.cancel();
        }
    }

    private void ejectCooked(SkilletBlockEntityController ctrl, Block block, ItemStack result) {
        ejectToRightSide(block, result);
        java.util.UUID placerUuid = ctrl.getPlacerUuid();
        if (placerUuid == null) return;
        dev.tako.papersdelight.stats.StatsManager stats = dev.tako.papersdelight.stats.StatsManager.getInstance();
        if (stats == null) return;
        String itemId = dev.tako.papersdelight.ce.CraftEngineUtil.getItemIdentifier(result);
        stats.record(placerUuid, dev.tako.papersdelight.stats.StatsManager.SKILLET_COOK, itemId, 1L);
    }

    private static final int HEAT_CACHE_TICKS = 10;

    void tickSkillet(SkilletBlockEntityController ctrl, CEWorld ceWorld, BlockPos cePos) {
        if (!plugin.isEnabled()) return;
        Block block = bukkitBlock(ceWorld, cePos);
        if (block == null) return;
        if (ctrl.isEmpty()) {
            ctrl.heatTicks(0);
            if (displayEntities.isEmpty() && particleSkillets.isEmpty()) return;
            Location loc = block.getLocation().toBlockLocation();
            removeAllDisplayEntities(loc);
            stopParticleTask(loc);
            return;
        }
        Location loc = block.getLocation().toBlockLocation();
        int now = Bukkit.getCurrentTick();
        int elapsed = TickBatch.due(ctrl.lastPassTick, now, TickBatch.interval());
        if (elapsed == 0) return;
        ctrl.lastPassTick = now;

        if (ctrl.heatTicks() <= 0) {
            ctrl.heated(HeatSourceService.isHeated(block));
            ctrl.heatTicks(HEAT_CACHE_TICKS);
        }
        ctrl.heatTicks(ctrl.heatTicks() - 1);
        boolean heated = ctrl.heated();
        SkilletConfig cfg = config;
        if (heated) {
            particleSkillets.add(loc);
        } else {
            particleSkillets.remove(loc);
        }

        for (int i = 0; i < elapsed; i++) {
            if (heated && ++ctrl.particleTicks >= cfg.particleIntervalTicks) {
                ctrl.particleTicks = 0;
                if (ParticleVisibility.hasNearbyViewer(block, cfg.particleViewDistance)) particleTick(block);
            }
            ItemStack ticked = ctrl.serverTick(heated, SkilletBlockBehavior.isWaterlogged(block));
            if (ticked != null && !ticked.isEmpty()) ejectCooked(ctrl, block, ticked);
        }

        if (ctrl.hasStoredStack()) {
            updateDisplayInPlace(block, ctrl.getStoredStack());
        } else {
            removeAllDisplayEntities(loc);
            particleSkillets.remove(loc);
        }
    }

    void forgetSkillet(SkilletBlockEntityController ctrl) {
        Location loc = blockLocation(ctrl);
        if (loc == null) return;
        knownSkillets.remove(loc);
        restored.remove(loc);
        stopParticleTask(loc);
        removeAllDisplayEntities(loc);
    }

    private static Location blockLocation(SkilletBlockEntityController ctrl) {
        CEWorld ceWorld = ctrl.blockEntity().world();
        if (ceWorld == null) return null;
        World world = Bukkit.getWorld(ceWorld.name());
        if (world == null) return null;
        BlockPos pos = ctrl.blockEntity().pos();
        return new Location(world, pos.x(), pos.y(), pos.z()).toBlockLocation();
    }

    private static Block bukkitBlock(CEWorld ceWorld, BlockPos pos) {
        World world = Bukkit.getWorld(ceWorld.name());
        return world == null ? null : world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    private void ejectToRightSide(Block block, ItemStack item) {
        String facing = SkilletBlockBehavior.getFacing(block);
        float rightX = 0, rightZ = 0;
        switch (facing) {
            case "east"  -> rightZ = -1f;
            case "south" -> rightX =  1f;
            case "west"  -> rightZ =  1f;
            default      -> rightX = -1f;
        }
        Location dropLoc = block.getLocation().clone().add(0.5, 0.2, 0.5);
        org.bukkit.entity.Item entity = block.getWorld().dropItem(dropLoc, item);
        entity.setVelocity(new org.bukkit.util.Vector(rightX * 0.15, 0.1, rightZ * 0.15));
        entity.setPickupDelay(10);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getChunk().getWorld();
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, world, cx, cz, 1L, () -> {
            if (!world.isChunkLoaded(cx, cz)) return;
            scanChunk(world, cx, cz);
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        World world = event.getWorld();
        for (Iterator<Location> it = knownSkillets.iterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (loc.getWorld() == world && (loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                it.remove();
                restored.remove(loc);
                stopParticleTask(loc);
                removeAllDisplayEntities(loc);
            }
        }
    }

    private void scanChunk(World world, int chunkX, int chunkZ) {
        scanChunk(world, chunkX, chunkZ, 1);
    }

    private void scanChunk(World world, int chunkX, int chunkZ, int retries) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        CEChunk chunk = CraftEngineUtil.getLoadedChunk(world, chunkX, chunkZ);
        if (chunk == null) {
            if (retries > 0) {
                SCHEDULER.getRegionScheduler().runTaskLater(plugin, world, chunkX, chunkZ, 40L, () -> scanChunk(world, chunkX, chunkZ, retries - 1));
            }
            return;
        }

        for (BlockEntity entity : chunk.blockEntities()) {
            SkilletBlockEntityController ctrl = getController(entity);
            if (ctrl == null) continue;
            BlockPos pos = entity.pos();
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            Location blockLoc = block.getLocation().toBlockLocation();
            knownSkillets.add(blockLoc);
            updateAutomaticSupport(block);

            if (ctrl.hasStoredStack()) {
                if (restored.add(blockLoc)) {
                    spawnDisplayEntity(block, ctrl.getStoredStack());
                }
            } else {
                stopParticleTask(blockLoc);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (getController(block) == null) return;
        Player player = event.getPlayer();
        if (player != null && !player.isSneaking()) {
            event.setCancelled(true);
            return;
        }
        Location loc = block.getLocation().toBlockLocation();
        knownSkillets.add(loc);
        updateAutomaticSupport(block);

        ItemStack held = event.getItemInHand();
        if (held != null && !held.isEmpty()) {
            SkilletBlockEntityController ctrl = getController(block);
            if (ctrl != null) ctrl.setSkilletItem(held);
        }
    }

    InteractionResult handleSkilletInteract(net.momirealms.craftengine.core.entity.player.Player cePlayer,
                                            Block clicked, InteractionHand hand) {
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) cePlayer.platformPlayer();
        EquipmentSlot interactionSlot = SkilletHand.toEquipmentSlot(hand);
        ItemStack held = player.getInventory().getItem(interactionSlot);
        SkilletBlockEntityController ctrl = getController(clicked);
        if (ctrl == null) return InteractionResult.FAIL;

        if (!ProtectionGate.canInteract(player, clicked.getLocation())) {
            return InteractionResult.FAIL;
        }

        knownSkillets.add(clicked.getLocation().toBlockLocation());

        boolean waterlogged = SkilletBlockBehavior.isWaterlogged(clicked);

        if (held == null || held.getType() == Material.AIR) {
            ItemStack extracted = ctrl.removeItem();
            if (extracted.isEmpty()) return InteractionResult.PASS;
            if (player.getGameMode() != GameMode.CREATIVE)
                player.getInventory().setItem(interactionSlot, extracted);
            removeAllDisplayEntities(clicked.getLocation().toBlockLocation());
            stopParticleTask(clicked.getLocation().toBlockLocation());
            cePlayer.swingHand(hand);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (waterlogged) {
            player.sendActionBar(TextUtil.parse(player,
                    "<!i><lang:block.farmersdelight.skillet.underwater>"));
            return InteractionResult.FAIL;
        }

        if (!CampfireRecipeUtil.isIngredient(held)) {
            player.sendActionBar(TextUtil.parse(player,
                    "<!i><lang:block.farmersdelight.skillet.invalid_item>"));
            return InteractionResult.FAIL;
        }

        ItemStack remainder = ctrl.addItemToCook(held, player.getUniqueId());
        if (remainder.getAmount() == held.getAmount()) {
            player.sendActionBar(TextUtil.parse(player,
                    "<!i><lang:block.farmersdelight.skillet.invalid_item>"));
            return InteractionResult.FAIL;
        }

        if (player.getGameMode() != GameMode.CREATIVE)
            player.getInventory().setItem(interactionSlot, remainder.isEmpty() ? null : remainder);
        spawnDisplayEntity(clicked, ctrl.getStoredStack());
        cePlayer.swingHand(hand);

        SkilletConfig cfg = config;
        playSound(clicked, HeatSourceService.isHeated(clicked) ? cfg.soundAddFood : cfg.soundAddFoodCold);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        Location loc = block.getLocation().toBlockLocation();
        knownSkillets.remove(loc);
        restored.remove(loc);

        SkilletBlockEntityController ctrl = getController(block);
        if (ctrl == null) return;

        ItemStack food = ctrl.takeItem();
        if (!food.isEmpty()) {
            block.getWorld().dropItemNaturally(
                    block.getLocation().clone().add(0.5, 0.5, 0.5), food);
        }
        removeAllDisplayEntities(loc);
        stopParticleTask(loc);

        if (event.isDropItems()) {
            ItemStack skilletItem = ctrl.getSkilletAsItem();
            if (!skilletItem.isEmpty()) {
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(
                        block.getLocation().clone().add(0.5, 0.5, 0.5), skilletItem);
            }
        }
    }

    private boolean isMatchingHeatSource(Block block, ConfigManager.HeatSourceDef def) {
        return HeatSourceService.matchesBlockDef(block, def) && HeatSourceService.checkLit(block);
    }

    private boolean matchesBlockDef(Block block, ConfigManager.HeatSourceDef def) {
        return HeatSourceService.matchesBlockDef(block, def);
    }

    private boolean isTraySource(Block block) {
        List<ConfigManager.HeatSourceDef> heatSources = ConfigManager.getHeatSources();
        Block below = block.getRelative(BlockFace.DOWN);
        for (ConfigManager.HeatSourceDef def : heatSources) {
            if (def.tray() && !def.conductor() && matchesBlockDef(below, def)) return true;
        }

        for (ConfigManager.HeatSourceDef def : heatSources) {
            if (!def.conductor() || !matchesBlockDef(below, def)) continue;
            Block twoBelow = block.getRelative(BlockFace.DOWN, 2);
            for (ConfigManager.HeatSourceDef heat : heatSources) {
                if (heat.tray() && !heat.conductor() && matchesBlockDef(twoBelow, heat)) return true;
            }
            break;
        }
        return false;
    }

    void updateAutomaticSupport(Block block) {
        String expected = isTraySource(block) ? "true" : "false";
        String current = CraftEngineUtil.getCustomBlockProperty(block, "support");
        if (!expected.equals(current)) {
            CraftEngineUtil.setCustomBlockProperty(block, "support", expected);
        }
    }

    private static int getModelCount(ItemStack stack) {
        if (stack.getAmount() <= 1) return 1;
        return 1 + (int) Math.ceil(((float) stack.getAmount() / stack.getMaxStackSize()) * 4);
    }

    private void spawnDisplayEntity(Block block, ItemStack item) {
        if (item == null || item.isEmpty()) return;
        SkilletConfig cfg = config;
        Location blockLoc = block.getLocation().toBlockLocation();
        removeAllDisplayEntities(blockLoc);

        int modelCount = getModelCount(item);
        float yawRad = (float) Math.toRadians(getDisplayYaw(SkilletBlockBehavior.getFacing(block)));

        Location center = blockLoc.clone().add(0.5, cfg.displayTranslateY, 0.5);
        World world = center.getWorld();

        long seed = (long) item.getType().ordinal() << 32 | (blockLoc.hashCode() & 0xFFFFFFFFL);
        Random rng = new Random(seed);

        List<ItemDisplay> spawned = new ArrayList<>(modelCount);
        for (int i = 0; i < modelCount; i++) {
            final double xOff, yOff, zOff;
            if (modelCount > 1 && i > 0) {
                xOff = (rng.nextFloat() * 2.0 - 1.0) * cfg.stackXzOffset;
                yOff = cfg.stackYOffset * i;
                zOff = (rng.nextFloat() * 2.0 - 1.0) * cfg.stackXzOffset;
            } else {
                xOff = 0; yOff = 0; zOff = 0;
            }

            Location spawnLoc = center.clone().add(xOff, yOff, zOff);
            ItemStack visual = item.clone();
            visual.setAmount(1);
            ItemDisplay display = world.spawn(spawnLoc, ItemDisplay.class, d -> {
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new Quaternionf().rotateY(yawRad).rotateX((float) Math.toRadians(cfg.displayPitch)),
                        new Vector3f(cfg.displayScale),
                        new Quaternionf()
                ));
                d.setItemStack(visual);
                d.setPersistent(false);
                d.setVisibleByDefault(true);
            });
            spawned.add(display);
        }
        displayEntities.put(blockLoc, spawned);
    }

    private void updateDisplayInPlace(Block block, ItemStack item) {
        Location loc = block.getLocation().toBlockLocation();
        List<ItemDisplay> existing = displayEntities.get(loc);
        if (existing == null || existing.isEmpty() || existing.stream().noneMatch(ItemDisplay::isValid)) {
            spawnDisplayEntity(block, item);
            return;
        }
        int newCount = getModelCount(item);
        int oldCount = existing.size();

        if (newCount > oldCount) {
            spawnDisplayEntity(block, item);
            return;
        }

        for (int i = 0; i < oldCount; i++) {
            ItemDisplay d = existing.get(i);
            if (!d.isValid()) continue;
            ItemStack visual = item.clone();
            visual.setAmount(1);
            d.setItemStack(visual);
        }

        if (newCount < oldCount) {
            for (int i = newCount; i < oldCount; i++) {
                ItemDisplay d = existing.get(i);
                if (d.isValid()) d.remove();
            }
            displayEntities.put(loc, new ArrayList<>(existing.subList(0, newCount)));
        }
    }

    private void removeAllDisplayEntities(Location loc) {
        List<ItemDisplay> list = displayEntities.remove(loc.toBlockLocation());
        if (list != null) {
            for (ItemDisplay d : list) {
                if (d != null && d.isValid()) d.remove();
            }
        }
    }

    private void stopParticleTask(Location loc) {
        particleSkillets.remove(loc.toBlockLocation());
    }

    private void particleTick(Block block) {
        SkilletConfig cfg = config;
        if (!HeatSourceService.isHeated(block)) return;
        if (!ParticleVisibility.hasNearbyViewer(block, cfg.particleViewDistance)) return;
        SkilletBlockEntityController ctrl = getController(block);
        if (ctrl == null || !ctrl.hasStoredStack()) return;

        World world = block.getWorld();
        double cx = block.getX() + 0.5;
        double cy = block.getY() + 0.1;
        double cz = block.getZ() + 0.5;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (rng.nextFloat() < 0.2f) {
            double x = cx + rng.nextDouble() * 0.4 - 0.2;
            double z = cz + rng.nextDouble() * 0.4 - 0.2;
            double motionY = rng.nextBoolean() ? 0.015 : 0.005;
            world.spawnParticle(Particle.WHITE_SMOKE, x, cy, z, 0, 0, motionY, 0, 0.0, null, false);
        }

        int fa = ctrl.getFireAspectLevel();
        if (fa > 0 && rng.nextFloat() < fa * 0.05f) {
            double fx = block.getX() + cfg.fireAspectOriginX
                    + rng.nextDouble() * cfg.fireAspectXzSpread * 2 - cfg.fireAspectXzSpread;
            double fy = block.getY() + cfg.fireAspectOriginY;
            double fz = block.getZ() + cfg.fireAspectOriginZ
                    + rng.nextDouble() * cfg.fireAspectXzSpread * 2 - cfg.fireAspectXzSpread;
            world.spawnParticle(Particle.ENCHANTED_HIT, fx, fy, fz, 0,
                    (rng.nextFloat() - 0.5) * cfg.fireAspectVelocityXz * 2,
                    cfg.fireAspectVelocityYBase + rng.nextFloat() * cfg.fireAspectVelocityYExtra,
                    (rng.nextFloat() - 0.5) * cfg.fireAspectVelocityXz * 2,
                    0.0, null, false);
        }

        if (rng.nextFloat() < 0.1f && !shouldThrottleSound(block, cfg)) {
            Location soundLoc = new Location(world, block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
            Runnable sound = () -> playSound(block, cfg.soundSizzle);
            if (!SCHEDULER.isFolia()) {
                if (Bukkit.isPrimaryThread()) sound.run();
                else SCHEDULER.getGlobalRegionScheduler().runTask(plugin, sound);
            } else {
                SCHEDULER.getRegionScheduler().runTask(plugin, soundLoc, sound);
            }
        }
    }

    private SkilletBlockEntityController getController(Block block) {
        try {
            var ceWorld = CraftEngineUtil.getLoadedWorld(block.getWorld());
            if (ceWorld == null) return null;
            var cePos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(cePos);
            return getController(be);
        } catch (Exception e) {
            return null;
        }
    }

    private SkilletBlockEntityController getController(BlockEntity be) {
        if (be == null) return null;
        try {
            var ref = new ControllerRef();
            be.controller.let(SkilletBlockEntityController.class, ref::set);
            return ref.controller;
        } catch (Exception e) {
            return null;
        }
    }

    private static final class ControllerRef {
        SkilletBlockEntityController controller;
        void set(SkilletBlockEntityController c) { this.controller = c; }
    }

    static float getDisplayYaw(String facing) {
        return switch (facing) {
            case "east" -> 270f;
            case "south" -> 180f;
            case "west" -> 90f;
            default -> 0f;
        };
    }

    private boolean shouldThrottleSound(Block block, SkilletConfig cfg) {
        Location loc = block.getLocation().toBlockLocation();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        int count = 0;
        for (Location l : particleSkillets) {
            if ((l.getBlockX() >> 4) == cx && (l.getBlockZ() >> 4) == cz) count++;
        }
        return dev.tako.papersdelight.util.ParticleThrottle.shouldSkip(
                count, cfg.soundThrottle.threshold, cfg.soundThrottle.maxRate);
    }

    private void playSound(Block block, SoundConfig cfg) {
        if (block == null) return;
        playSound(new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5), cfg);
    }

    private void playSound(Location loc, SoundConfig cfg) {
        if (loc == null || loc.getWorld() == null || cfg == null) return;
        Runnable sound = () -> {
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
            ConfigManager.playSound(world, loc.getX(), loc.getY(), loc.getZ(), cfg);
        };
        if (!SCHEDULER.isFolia()) {
            if (Bukkit.isPrimaryThread()) { sound.run(); return; }
            SCHEDULER.getGlobalRegionScheduler().runTask(plugin, sound);
            return;
        }
        SCHEDULER.getRegionScheduler().runTask(plugin, loc, sound);
    }
}
