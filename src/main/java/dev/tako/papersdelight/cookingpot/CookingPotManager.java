package dev.tako.papersdelight.cookingpot;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.config.CookingPotConfig;
import dev.tako.papersdelight.common.ExplosionSettleFlow;
import dev.tako.papersdelight.common.TickBatch;
import dev.tako.papersdelight.heat.HeatSourceService;
import dev.tako.papersdelight.api.protection.ProtectionGate;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.gui.MenuManager;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.bukkit.util.ExplosionUtils;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.VersionHelper;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.util.ItemMetaUtil;
import dev.tako.papersdelight.util.MealLoreUtil;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import dev.tako.papersdelight.util.ParticleVisibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class CookingPotManager implements Listener {

    private static final String FALLBACK_POT_ID = "farmersdelight:cooking_pot";

    private final Map<String, String> containerFallbackCache = new ConcurrentHashMap<>();

    private static final int WAITING_OUTPUT_CAPACITY = 64;

    /** 侧面漏斗扫描方向：静态数组，processHoppers 每 8 个补偿 tick 扫一遍，免每次分配 List */
    private static final BlockFace[] SIDE_HOPPER_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    static volatile CookingPotManager instance;

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private final JavaPlugin plugin;
    private final RecipeManager recipeManager;

    private volatile CookingPotConfig config;
    final Set<Location> trackedLocations = ConcurrentHashMap.newKeySet();
    private final CookingPotRecipeCache recipeCache = new CookingPotRecipeCache();
    private final Set<Location> particlePots = ConcurrentHashMap.newKeySet();
    private final Map<String, ItemStack> heatIcons = new ConcurrentHashMap<>(2);
    private final Map<Integer, ItemStack> progressIcons = new ConcurrentHashMap<>(256);

    private final Map<Long, java.util.concurrent.atomic.AtomicInteger> chunkParticleCount = new ConcurrentHashMap<>();
    private final Map<Location, CookingSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerSessions = new ConcurrentHashMap<>();
    private final Map<Location, PendingUnload> pendingUnloads = new ConcurrentHashMap<>();

    private final Object sessionStateLock = new Object();
    private final AtomicLong pendingGeneration = new AtomicLong();
    private final Set<Location> recentlyPlaced = ConcurrentHashMap.newKeySet();

    private final CookingPotDropFlow<Event, Block> pendingExplosions = new CookingPotDropFlow<>();
    private final CookingPotIdentity<Block, Object> potIdentity = new CookingPotIdentity<>(
            block -> {
                try {
                    var state = CraftEngineBlocks.getCustomBlockState(block);
                    return state == null ? null : state.behavior();
                } catch (Throwable ignored) {
                    return null;
                }
            },
            behavior -> behavior instanceof CookingPotBlockBehavior,
            block -> getController(block) != null);

    private final NamespacedKey carriedMealKey;
    private final NamespacedKey carriedContainerKey;
    private long sessionGeneration;

    public CookingPotManager(JavaPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        instance = this;
        this.config = CookingPotConfig.load();
        this.carriedMealKey = new NamespacedKey(plugin, "cooking_pot_meal");
        this.carriedContainerKey = new NamespacedKey(plugin, "cooking_pot_container");
    }

    public void shutdown() {
        particlePots.clear();
        chunkParticleCount.clear();
        trackedLocations.clear();
        recipeCache.clear();
        activeSessions.clear();
        playerSessions.clear();
        recentlyPlaced.clear();
    }

    public void reload() {
        particlePots.clear();
        chunkParticleCount.clear();
        heatIcons.clear();
        progressIcons.clear();
        this.config = CookingPotConfig.load();
    }

    public void markPlaced(Location location) {
        Location key = blockKey(location);
        recentlyPlaced.add(key);
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, key, 2L, () -> recentlyPlaced.remove(key));
    }

    public void activatePot(Block block) {
        trackedLocations.add(blockKey(block));
    }

    void initPotLater(Block block) {
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, block.getLocation().toBlockLocation(), 1L, () -> updateAutomaticSupport(block));
    }

    public boolean isRecentlyPlaced(Location location) {
        return recentlyPlaced.contains(blockKey(location));
    }

    public boolean hasPersistedData(Block block) {
        return trackedLocations.contains(blockKey(block)) || getController(block) != null;
    }

    CookingPotBlockEntityController getController(Block block) {
        try {
            CEWorld ceWorld = CraftEngineUtil.getLoadedWorld(block.getWorld());
            if (ceWorld == null) return null;
            BlockPos cePos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(cePos);
            return getController(be);
        } catch (Exception e) {
            return null;
        }
    }

    private CookingPotBlockEntityController getController(BlockEntity be) {
        if (be == null) return null;
        try {
            ControllerRef ref = new ControllerRef();
            be.controller.let(CookingPotBlockEntityController.class, ref::set);
            return ref.controller;
        } catch (Exception e) {
            return null;
        }
    }

    private static final class ControllerRef {
        CookingPotBlockEntityController controller;
        void set(CookingPotBlockEntityController c) { this.controller = c; }
    }

    public CookingPotData openSession(Block block, Player player) {
        CookingPotBlockEntityController ctrl = getController(block);
        CookingPotData data = ctrl != null ? ctrl.toData() : new CookingPotData();
        Location key = blockKey(block);
        UUID playerId = player.getUniqueId();

        Location previous = playerSessions.put(playerId, key);
        if (previous != null && !previous.equals(key)) {
            CookingSession old = activeSessions.get(previous);
            if (old != null && old.player.getUniqueId().equals(playerId)) handoffSession(previous, old);
        }
        CookingSession replacement = new CookingSession(block, data, player,
                ++sessionGeneration, player.getOpenInventory().getTopInventory());
        CookingSession replaced;
        synchronized (sessionStateLock) {
            replaced = activeSessions.put(key, replacement);
        }
        if (replaced != null && replaced.player != null && !replaced.player.getUniqueId().equals(playerId)) {
            playerSessions.remove(replaced.player.getUniqueId(), key);

            Player replacedPlayer = replaced.player;
            if (replacedPlayer.isOnline() && replacedPlayer.isValid()) {
                Runnable resetReplacedMenu = () -> {
                    if (replacedPlayer.getOpenInventory().getTopInventory() != replaced.menu) return;
                    replaced.menu.setItem(CookingPotLayout.FINAL_OUTPUT, null);
                    replaced.menu.setItem(CookingPotLayout.WAITING_OUTPUT, null);
                    replacedPlayer.closeInventory();
                };
                if (!plugin.isEnabled()) {
                    try { resetReplacedMenu.run(); } catch (Throwable ignored) { }
                } else {
                    SCHEDULER.getEntityScheduler().runTask(plugin, replacedPlayer, resetReplacedMenu);
                }
            }
        }
        trackedLocations.add(key);
        return data;
    }

    private void handoffSession(Location key, CookingSession session) {
        if (session.closeRequested) return;
        session.closeRequested = true;
        Player owner = session.player;
        Runnable handoff = () -> {
            boolean ownsMenu = owner.getOpenInventory().getTopInventory() == session.menu;

            CookingPotMenuCloseFlow.EditableSnapshot snapshot =
                    CookingPotMenuCloseFlow.EditableSnapshot.read(session.menu);
            if (ownsMenu) owner.closeInventory();
            AtomicBoolean retiredCalled = new AtomicBoolean();
            Runnable retired = () -> {
                if (retiredCalled.compareAndSet(false, true)) stageRetiredPending(key, session, snapshot);
            };
            if (key == null || key.getWorld() == null) {
                retired.run();
                return;
            }
            ScheduledTask scheduled = Bukkit.getRegionScheduler().run(plugin, key, st -> {
                if (!isCurrentSession(key, session)) return;
                applyEditableSnapshot(session, snapshot);
                releaseSession(key, session);
            });
            if (scheduled == null) retired.run();
        };
        if (owner == null || !owner.isValid()) {
            conservativeRelease(key, session);
            return;
        }
        if (!plugin.isEnabled()) {
            try { handoff.run(); } catch (Throwable ignored) { }
            conservativeRelease(key, session);
            return;
        }
        SCHEDULER.getEntityScheduler().runTask(plugin, owner, handoff);
    }

    public void stopCooking(Block block) {
        Location key = blockKey(block);
        CookingSession session = activeSessions.remove(key);
        if (session == null) return;
        playerSessions.remove(session.player.getUniqueId(), key);

        CookingPotBlockEntityController ctrl = getController(block);
        if (ctrl != null) ctrl.fromData(session.data);
    }

    public void stopCookingIfOwner(Block block, Player player) {
        Location key = blockKey(block);
        CookingSession session = activeSessions.get(key);
        if (session == null) return;
        if (!session.player.getUniqueId().equals(player.getUniqueId())) return;
        activeSessions.remove(key);
        playerSessions.remove(player.getUniqueId(), key);
        CookingPotBlockEntityController ctrl = getController(block);
        if (ctrl != null) ctrl.fromData(session.data);
    }

    public boolean isSessionOwner(Block block, Player player) {
        CookingSession session = activeSessions.get(blockKey(block));
        return session != null && session.player.getUniqueId().equals(player.getUniqueId());
    }

    public CookingPotData getSessionData(Block block) {
        CookingSession session = activeSessions.get(blockKey(block));
        return session == null ? null : session.data;
    }

    public boolean isPlayerCooking(Player player) {
        CookingSession session = findSession(player);
        return session != null && session.data.isCooking;
    }

    public void syncFromInventory(Block block, Inventory inventory) {
        CookingPotBlockEntityController ctrl = getController(block);
        if (ctrl == null) return;
        CookingPotData data = ctrl.toData();
        CookingSession session = activeSessions.get(blockKey(block));
        if (syncEditableSlots(session, data, inventory)) ctrl.fromData(data);
    }

    public boolean startCooking(Player player, Inventory inventory) {
        CookingSession session = findSession(player);
        if (session == null) return false;
        syncEditableSlots(session, session.data, inventory);
        return true;
    }

    public boolean cancelCooking(Player player, Inventory inventory) {
        return false;
    }

    public boolean takeFinalOutputToCursor(Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
        CookingSession session = findSession(player);
        if (session == null || isEmpty(session.data.finalOutput)) return false;
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.isEmpty()) return false;
        session.invalidate();
        ItemStack output = session.data.finalOutput.clone();
        session.data.finalOutput = null;
        awardStoredExperience(player, session.data);
        CookingPotBlockEntityController ctrl = getController(session.block);
        if (ctrl != null) ctrl.fromData(session.data);
        refreshInventory(event.getView().getTopInventory(), session.data, session, isHeated(session.block));
        event.getView().setCursor(output);
        recordCook(player, output);
        return true;
    }

    private static void recordCook(Player player, ItemStack output) {
        dev.tako.papersdelight.stats.StatsManager stats =
                dev.tako.papersdelight.stats.StatsManager.getInstance();
        if (stats == null || isEmpty(output)) return;
        stats.record(player, dev.tako.papersdelight.stats.StatsManager.COOKING_POT_COOK,
                CraftEngineUtil.getItemIdentifier(output), output.getAmount());
    }

    public boolean tryServeHeldContainer(Player player, Block block, ItemStack held) {
        if (isEmpty(held)) return false;
        CookingPotBlockEntityController ctrl = getController(block);
        if (ctrl == null) return false;
        CookingPotData data = ctrl.toData();
        if (isEmpty(data.waitingOutput) || data.recipeContainer == null
                || !CraftEngineUtil.isItem(held, data.recipeContainer)) return false;
        CookingSession session = activeSessions.get(blockKey(block));
        if (session != null) session.invalidate();

        ItemStack serving = data.waitingOutput.clone();
        serving.setAmount(1);
        shrink(data.waitingOutput, 1);
        if (isEmpty(data.waitingOutput)) {
            data.waitingOutput = null;
            data.recipeContainer = null;
        }
        if (player.getGameMode() != GameMode.CREATIVE) shrink(held, 1);
        recordCook(player, serving);
        giveOrDrop(player, serving);
        ctrl.fromData(data);
        refreshOpenSession(block, data);
        playSound(block, new ConfigManager.SoundConfig("farmersdelight:block.food_container.take", 1.0f, 1.0f, 1.0f));
        return true;
    }

    public boolean toggleSupport(Block block) {
        String support = CraftEngineUtil.getCustomBlockProperty(block, "support");
        if (support == null) return false;
        String next = "2".equals(support) ? (isTraySource(block) ? "1" : "0") : "2";
        boolean changed = CraftEngineUtil.setCustomBlockProperty(block, "support", next);
        if (changed) playSound(block, Sound.BLOCK_LANTERN_PLACE, 0.7f, 1.0f);
        return changed;
    }

    public void populateInventory(Inventory inventory, CookingPotData data) {
        refreshIngredientSlots(inventory, data);
        if (isEmpty(data.waitingOutput)) {
            data.renderedWaitingOutput = null;
            data.renderedWaitingSource = null;
            data.renderedWaitingContainer = null;
        }
        writeSlot(inventory, CookingPotLayout.WAITING_OUTPUT,
                isEmpty(data.waitingOutput) ? null : renderedWaitingOutput(data));
        refreshSlot(inventory, CookingPotLayout.UTENSIL, data.utensil);
        refreshSlot(inventory, CookingPotLayout.FINAL_OUTPUT, data.finalOutput);
        updateProgressIndicator(inventory, data);
    }

    private ItemStack renderedWaitingOutput(CookingPotData data) {
        if (data.renderedWaitingOutput != null
                && itemsEqual(data.renderedWaitingSource, data.waitingOutput)
                && java.util.Objects.equals(data.renderedWaitingContainer, data.recipeContainer)) {
            return data.renderedWaitingOutput;
        }
        ItemStack waiting = data.waitingOutput.clone();
        ItemMeta meta = waiting.getItemMeta();
        if (data.recipeContainer != null && !data.recipeContainer.isBlank()) {
            meta.lore(List.of(Component.translatable(
                            "container.farmersdelight.cooking_pot.served_on",
                            translAtable(data.recipeContainer))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        } else {
            meta.lore(null);
        }
        waiting.setItemMeta(meta);
        MealLoreUtil.overrideMaxStackSize(waiting, WAITING_OUTPUT_CAPACITY);
        data.renderedWaitingOutput = waiting;
        data.renderedWaitingSource = data.waitingOutput.clone();
        data.renderedWaitingContainer = data.recipeContainer;
        return waiting;
    }

    static boolean hasProgress(int cookTime, int cookTimeTotal) {
        return cookTime > 0 && cookTimeTotal > 0;
    }

    public void updateHeatIndicator(Inventory inventory, Block block) {
        updateHeatIndicator(inventory, isHeated(block));
    }

    private void updateHeatIndicator(Inventory inventory, boolean heated) {
        writeSlot(inventory, CookingPotLayout.STATUS,
                heatIcons.computeIfAbsent(heated ? "heated" : "unheated", this::buildHeatIcon));
    }

    private ItemStack buildHeatIcon(String state) {
        boolean heated = "heated".equals(state);
        return ConfigManager.buildGuiItem(
                "cooking_pot.buttons.heat_indicator." + state,
                null,
                heated ? "<!i><white><lang:container.farmersdelight.cooking_pot.heated>" : "<!i><white><lang:container.farmersdelight.cooking_pot.not_heated>",
                List.of());
    }

    public void updateProgressIndicator(Inventory inventory, CookingPotData data) {
        if (!hasProgress(data.cookTime, data.cookTimeTotal)) {
            writeSlot(inventory, CookingPotLayout.PROGRESS[0],
                    progressIcons.computeIfAbsent(-1, k -> ConfigManager.buildGuiItem(
                            "cooking_pot.buttons.border", null, "<white> </white>", List.of())));
            return;
        }

        int total = Math.max(data.cookTimeTotal, 1);
        int pct = Math.min(100, data.cookTime * 100 / total);
        int stage = Math.min(21, pct * 22 / 100);
        data.progressStage = stage;
        data.progress = pct;

        boolean cooking = data.isCooking;
        writeSlot(inventory, CookingPotLayout.PROGRESS[0],
                progressIcons.computeIfAbsent((cooking ? 128 : 0) + pct, k -> buildProgressIcon(cooking, pct)));
    }

    private static ItemStack buildProgressIcon(boolean cooking, int pct) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        ItemMetaUtil.setDisplayName(meta, "<!i><white>" + pct + "%");
        List<String> loreTemplates = ConfigManager.getList(cooking ? "pot_progress_cook" : "pot_progress_cool");
        if (!loreTemplates.isEmpty()) {
            ItemMetaUtil.setLore(meta, loreTemplates);
        }
        ItemMetaUtil.applyCustomModelData(meta, 325001 + Math.min(21, pct * 22 / 100));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!isCookingPot(block)) return;
        markPlaced(block.getLocation());
        Location key = blockKey(block);
        trackedLocations.add(key);
        updateAutomaticSupport(block);

        SCHEDULER.getRegionScheduler().runTaskLater(plugin, key, 1L, () -> {
            CookingPotBlockEntityController ctrl = getController(block);
            if (ctrl == null) return;
            CookingPotData data = ctrl.toData();
            if (processHoppers(block, data)) ctrl.fromData(data);
        });

        CookingPotBlockEntityController ctrl = getController(block);
        if (ctrl == null) return;
        ItemMeta meta = event.getItemInHand().getItemMeta();
        if (meta != null) {
            byte[] mealBytes = meta.getPersistentDataContainer().get(carriedMealKey, PersistentDataType.BYTE_ARRAY);
            if (mealBytes != null && mealBytes.length > 0) {
                try { ctrl.waitingOutput(ItemStack.deserializeBytes(mealBytes)); } catch (Exception ignored) {}
            }
            String container = meta.getPersistentDataContainer().get(carriedContainerKey, PersistentDataType.STRING);
            if (container != null && !container.isEmpty()) ctrl.recipeContainer(container);
        }
    }

    InteractionResult handlePotInteract(org.bukkit.entity.Player player, Block block) {
        if (!ProtectionGate.canInteract(player, block.getLocation())) {
            return InteractionResult.FAIL;
        }

        org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();

        if (player.isSneaking()) {
            if (held == null || held.isEmpty()) {
                toggleSupport(block);
                player.swingMainHand();
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            return InteractionResult.PASS;
        }

        if (held != null && tryServeHeldContainer(player, block, held)) {
            player.swingMainHand();
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (isRecentlyPlaced(block.getLocation())) return InteractionResult.FAIL;
        String placedPotId = CookingPotDropId.resolve(
                CraftEngineUtil.getCustomBlockId(block), FALLBACK_POT_ID);
        if (CraftEngineUtil.isItem(held, placedPotId) && !hasPersistedData(block)) {
            markPlaced(block.getLocation());
            return InteractionResult.FAIL;
        }

        player.swingMainHand();
        MenuManager.getInstance().openMenu(player, "cooking_pot", inv ->
                closeFromOwner(player, block, inv));

        CookingPotData data = openSession(block, player);

        org.bukkit.inventory.Inventory topInv = player.getOpenInventory().getTopInventory();
        populateInventory(topInv, data);
        updateProgressIndicator(topInv, data);
        updateHeatIndicator(topInv, block);
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    private void closeFromOwner(Player owner, Block block, Inventory closedInventory) {
        Location key = blockKey(block);
        CookingSession session = activeSessions.get(key);
        if (session == null || !session.player.getUniqueId().equals(owner.getUniqueId())
                || session.menu != closedInventory) return;
        session.closeRequested = true;
        CookingPotMenuCloseFlow.Scheduler scheduler = new CookingPotMenuCloseFlow.Scheduler() {
            public void entity(Runnable task) {
                if (owner == null || !owner.isValid()) return;
                if (!plugin.isEnabled()) {
                    try { task.run(); } catch (Throwable ignored) { }
                    return;
                }
                SCHEDULER.getEntityScheduler().runTask(plugin, owner, task);
            }
            public void region(Runnable task) { SCHEDULER.getRegionScheduler().runTask(plugin, key, task); }
            public void entity(Runnable task, Runnable retired) {
                try {
                    if (owner == null || !owner.isValid()) { retired.run(); return; }
                    if (!plugin.isEnabled()) {
                        try { task.run(); } catch (Throwable ignored) { retired.run(); }
                        return;
                    }
                    SCHEDULER.getEntityScheduler().runTask(plugin, owner, st -> task.run());
                } catch (Throwable failure) { retired.run(); }
            }
            public void region(Runnable task, Runnable retired) {
                try {
                    if (key == null || key.getWorld() == null) { retired.run(); return; }
                    ScheduledTask scheduled = Bukkit.getRegionScheduler().run(plugin, key, st -> task.run());
                } catch (Throwable failure) { retired.run(); }
            }
        };
        AtomicBoolean retiredCalled = new AtomicBoolean();
        CookingPotMenuCloseFlow.captureAndPersist(scheduler,
                () -> isCurrentSession(key, session), closedInventory,
                snapshot -> applyEditableSnapshot(session, snapshot),
                () -> releaseSession(key, session),
                () -> conservativeRelease(key, session),
                snapshot -> {
                    if (retiredCalled.compareAndSet(false, true)) {
                        stageRetiredPending(key, session, snapshot);
                    }
                });
    }

    private void stageRetiredPending(Location key, CookingSession session,
                                     CookingPotMenuCloseFlow.EditableSnapshot snapshot) {
        registerPending(key, session, snapshot, false);
    }

    private PendingUnload registerPending(Location key, CookingSession session,
                                          CookingPotMenuCloseFlow.EditableSnapshot snapshot, boolean refresh) {
        synchronized (sessionStateLock) {
            if (!isCurrentSession(key, session)) return null;
            PendingUnload existing = pendingUnloads.get(key);
            if (existing != null && existing.session == session && (!refresh || existing.snapshot == snapshot)) {
                return existing;
            }
            long token = pendingGeneration.incrementAndGet();
            session.unloadGeneration = token;
            PendingUnload pending = new PendingUnload(session, snapshot, token);
            pendingUnloads.put(key, pending);
            return pending;
        }
    }

    private boolean isCurrentSession(Location key, CookingSession expected) {
        return activeSessions.get(key) == expected && expected.generation > 0;
    }

    private boolean isCurrentSessionVersion(Location key, CookingSession expected, long version) {
        return isCurrentSession(key, expected) && expected.version() == version;
    }

    private void applyEditableSnapshot(CookingSession session, CookingPotMenuCloseFlow.EditableSnapshot snapshot) {
        for (int i = 0; i < session.data.ingredients.length; i++)
            session.data.ingredients[i] = snapshot.ingredients()[i] == null ? null : snapshot.ingredients()[i].clone();
        session.data.utensil = snapshot.utensil() == null ? null : snapshot.utensil().clone();
        CookingPotBlockEntityController ctrl = getController(session.block);
        if (ctrl != null) ctrl.fromData(session.data);
    }

    private void releaseSession(Location key, CookingSession session) {
        if (activeSessions.remove(key, session)) playerSessions.remove(session.player.getUniqueId(), key);
    }

    private void submitCloseSnapshot(Location key, CookingSession session,
                                     CookingPotMenuCloseFlow.EditableSnapshot snapshot) {
        PendingUnload pending = registerPending(key, session, snapshot, true);
        if (pending == null) return;
        if (key == null || key.getWorld() == null) {
            pendingUnloads.replace(key, pending, pending);
            return;
        }
        ScheduledTask scheduled = Bukkit.getRegionScheduler().run(plugin, key, st -> {
            if (!isCurrentSession(key, session) || pendingUnloads.get(key) != pending
                    || session.unloadGeneration != pending.token) return;
            applyEditableSnapshot(session, pending.snapshot);
            pendingUnloads.remove(key, pending);
            releaseSession(key, session);
        });
        if (scheduled == null) pendingUnloads.replace(key, pending, pending);
    }

    private void retryPending(Location key, CookingSession session) {
        PendingUnload pending = pendingUnloads.get(key);
        if (pending != null && pending.session == session) {
            submitPendingUnload(key, pending);
        }
    }

    private void submitPendingUnload(Location key, PendingUnload pending) {
        CookingSession session = pending.session;
        if (key == null || key.getWorld() == null) {
            pendingUnloads.replace(key, pending, pending);
            return;
        }
        ScheduledTask scheduled = Bukkit.getRegionScheduler().run(plugin, key, st -> {
            if (!isCurrentSession(key, session) || pendingUnloads.get(key) != pending
                    || session.unloadGeneration != pending.token) return;
            applyEditableSnapshot(session, pending.snapshot);
            pendingUnloads.remove(key, pending);
            releaseSession(key, session);
        });
        if (scheduled == null) pendingUnloads.replace(key, pending, pending);
    }

    private void conservativeRelease(Location key, CookingSession session) {
        if (key == null || key.getWorld() == null) {
            session.closeRequested = false;
            return;
        }
        SCHEDULER.getRegionScheduler().runTask(plugin, key, () -> {
            if (!isCurrentSession(key, session)) return;
            CookingPotBlockEntityController ctrl = getController(session.block);
            if (ctrl != null) ctrl.fromData(session.data);
            releaseSession(key, session);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCookingPotInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Location key = playerSessions.get(player.getUniqueId());
        if (key == null) return;
        CookingSession session = activeSessions.get(key);
        if (session != null && player.getOpenInventory().getTopInventory() == session.menu) session.invalidate();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCookingPotInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Location key = playerSessions.get(player.getUniqueId());
        if (key == null) return;
        CookingSession session = activeSessions.get(key);
        if (session != null && player.getOpenInventory().getTopInventory() == session.menu) session.invalidate();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCookingPotInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Location key = playerSessions.get(player.getUniqueId());
        if (key == null) return;
        CookingSession session = activeSessions.get(key);
        if (session != null && event.getInventory() == session.menu) session.invalidate();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (!isCookingPotIdentity(block)) return;
        Location key = blockKey(block);
        CookingSession session = activeSessions.get(key);
        if (session != null) {
            event.setCancelled(true);
            if (!session.closeRequested) {
                session.closeRequested = true;
                Player owner = session.player;
                Runnable closeMenuAndSubmit = () -> {
                    if (!isCurrentSession(key, session)) return;
                    try {
                        CookingPotMenuCloseFlow.EditableSnapshot snapshot =
                                CookingPotMenuCloseFlow.EditableSnapshot.read(session.menu);
                        boolean ownsMenu = owner.getOpenInventory().getTopInventory() == session.menu;
                        if (ownsMenu) owner.closeInventory();
                        submitCloseSnapshot(key, session, snapshot);
                    } catch (Throwable unreadable) {
                        session.closeRequested = false;
                        retryPending(key, session);
                    }
                };
                Runnable retired = () -> {
                    session.closeRequested = false;
                    retryPending(key, session);
                };
                if (owner == null || !owner.isValid()) { retired.run(); return; }
                if (!plugin.isEnabled()) {
                    try { closeMenuAndSubmit.run(); } catch (Throwable ignored) { retired.run(); }
                    return;
                }
                SCHEDULER.getEntityScheduler().runTask(plugin, owner, st -> closeMenuAndSubmit.run());
            }
            return;
        }
        CookingPotBlockEntityController ctrl = getController(block);
        CookingPotData data = ctrl != null ? ctrl.toData() : new CookingPotData();
        if (dropStatefulPot(block, data, true, event.isDropItems())) event.setDropItems(false);
        trackedLocations.remove(key);
        forgetParticles(key);
        recipeCache.remove(key);
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        stageExplosion(event, event.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        stageExplosion(event, event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void settleBlockExplosion(BlockExplodeEvent event) {
        settleExplosion(event, event.isCancelled(), explosionRadius(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void settleEntityExplosion(EntityExplodeEvent event) {
        settleExplosion(event, event.isCancelled(), explosionRadius(event));
    }

    private void stageExplosion(Event event, List<Block> blocks) {
        for (Iterator<Block> it = blocks.iterator(); it.hasNext();) {
            Block block = it.next();

            if (!isCookingPotIdentity(block)) continue;
            it.remove();
            pendingExplosions.stage(event, block);
        }
    }

    private void settleExplosion(Event event, boolean cancelled, float radius) {
        pendingExplosions.settle(event, cancelled,
                this::isCurrentCookingPot,
                block -> activeSessions.containsKey(blockKey(block)),
                block -> ExplosionSettleFlow.survives(radius, ThreadLocalRandom.current().nextFloat()),
                (block, contents, body) -> {

                    CookingPotBlockEntityController ctrl = isCookingPotIdentity(block) ? getController(block) : null;
                    if (ctrl != null) dropStatefulPot(block, ctrl.toData(), contents, body);
                },
                block -> {
                    Location key = blockKey(block);
                    trackedLocations.remove(key);
                    forgetParticles(key);
                    recipeCache.remove(key);
                    CraftEngineBlocks.remove(block, false);
                });
    }

    private float explosionRadius(BlockExplodeEvent event) {
        return ExplosionSettleFlow.radius(VersionHelper.isOrAbove1_21,
                VersionHelper.isOrAbove1_21 && ExplosionUtils.isDroppingItems(event), event.getYield(),
                () -> ExplosionUtils.getRadius(event.getYield(), event.getExplosionResult()));
    }

    private float explosionRadius(EntityExplodeEvent event) {
        return ExplosionSettleFlow.radius(VersionHelper.isOrAbove1_21,
                VersionHelper.isOrAbove1_21 && ExplosionUtils.isDroppingItems(event), event.getYield(),
                () -> ExplosionUtils.getRadius(event.getYield(), event.getExplosionResult()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, event.getWorld(), event.getChunk().getX(), event.getChunk().getZ(), 1L, () -> retryPendingUnloads(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        World world = event.getWorld();

        for (Iterator<Location> it = trackedLocations.iterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (loc.getWorld() == world && (loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                CookingSession session = activeSessions.get(loc);
                if (session != null) {
                    persistSessionBeforeUnload(session);
                } else {
                    it.remove();
                    forgetParticles(loc);
                    recipeCache.remove(loc);
                }
            }
        }
    }

    private void persistSessionBeforeUnload(CookingSession session) {
        Location key = blockKey(session.block);

        if (Bukkit.getServer() == null) {
            Inventory open = openCookingPotInventory(session.player);
            if (open != null) syncEditableSlots(session, session.data, open);
            CookingPotBlockEntityController ctrl = getController(session.block);
            if (ctrl != null) ctrl.fromData(session.data);
            releaseSession(key, session);
            return;
        }
        Inventory identity = session.menu;
        Runnable persistAndSubmit = () -> {
            if (activeSessions.get(key) != session) return;
            CookingPotMenuCloseFlow.EditableSnapshot snapshot =
                    CookingPotMenuCloseFlow.EditableSnapshot.read(identity);
            PendingUnload pending = registerPending(key, session, snapshot, true);
            if (pending == null) return;
            if (key == null || key.getWorld() == null) {
                pendingUnloads.replace(key, pending, pending);
                return;
            }
            SCHEDULER.getRegionScheduler().runTask(plugin, key, () -> {
                if (activeSessions.get(key) != session || pendingUnloads.get(key) != pending
                        || session.unloadGeneration != pending.token) return;
                applyEditableSnapshot(session, pending.snapshot);
                pendingUnloads.remove(key, pending);
                releaseSession(key, session);
                trackedLocations.remove(key);
                forgetParticles(key);
            });
        };
        Player owner = session.player;
        if (owner == null || !owner.isValid()) {
            conservativeRelease(key, session);
            return;
        }
        if (!plugin.isEnabled()) {
            try { persistAndSubmit.run(); } catch (Throwable ignored) { }
            conservativeRelease(key, session);
            return;
        }
        SCHEDULER.getEntityScheduler().runTask(plugin, owner, persistAndSubmit);
    }

    private void retryPendingUnloads(World world, int cx, int cz) {
        pendingUnloads.forEach((key, pending) -> {
            if (key.getWorld() != world || (key.getBlockX() >> 4) != cx || (key.getBlockZ() >> 4) != cz) return;
            CookingSession session = pending.session;
            if (activeSessions.get(key) != session) { pendingUnloads.remove(key, pending); return; }
            SCHEDULER.getRegionScheduler().runTask(plugin, key, () -> {
                if (activeSessions.get(key) != session || pendingUnloads.get(key) != pending
                        || session.unloadGeneration != pending.token) return;
                applyEditableSnapshot(session, pending.snapshot);
                pendingUnloads.remove(key, pending);
                releaseSession(key, session);
                trackedLocations.remove(key);
                forgetParticles(key);
            });
        });
    }

    void registerPot(CookingPotBlockEntityController controller) {
        Location location = locationOf(controller);
        if (location != null) trackedLocations.add(location);
    }

    void forgetPot(CookingPotBlockEntityController controller) {
        Location location = locationOf(controller);
        if (location == null) return;
        recipeCache.remove(location);
        if (particlePots.remove(location)) adjustChunkParticleCount(location, -1);
        World world = location.getWorld();
        if (world != null && location.getBlock().getType().isAir()) trackedLocations.remove(location);
    }

    private static Location locationOf(CookingPotBlockEntityController controller) {
        CEWorld ceWorld = controller.blockEntity().world();
        if (ceWorld == null) return null;
        World world = Bukkit.getWorld(ceWorld.name());
        if (world == null) return null;
        BlockPos pos = controller.blockEntity().pos();
        return new Location(world, pos.x(), pos.y(), pos.z()).toBlockLocation();
    }

    private static final int HEAT_CACHE_TICKS = 10;

    void potTick(CookingPotBlockEntityController ctrl, CEWorld ceWorld, BlockPos cePos) {
        if (!plugin.isEnabled()) return;
        World world = Bukkit.getWorld(ceWorld.name());
        if (world == null) return;
        Location location = new Location(world, cePos.x(), cePos.y(), cePos.z()).toBlockLocation();
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());
        if (block.getType().isAir()) {
            trackedLocations.remove(location);
            forgetParticles(location);
            return;
        }

        int now = Bukkit.getCurrentTick();
        int elapsed = TickBatch.due(ctrl.lastPassTick, now, TickBatch.interval());
        if (elapsed == 0) return;
        ctrl.lastPassTick = now;

        if (ctrl.heatTicks() <= 0) {
            ctrl.heated(isHeated(block));
            ctrl.heatTicks(HEAT_CACHE_TICKS);
        }
        boolean heatRefreshed = ctrl.heatTicks() == HEAT_CACHE_TICKS;
        ctrl.heatTicks(ctrl.heatTicks() - 1);
        boolean heated = ctrl.heated();
        CookingPotConfig cfg = config;
        if (heated) {
            if (particlePots.add(location)) adjustChunkParticleCount(location, 1);
            ctrl.particleTicks += elapsed;
            if (ctrl.particleTicks >= cfg.particleIntervalTicks) {
                ctrl.particleTicks = 0;
                if (ParticleVisibility.hasNearbyViewer(block, cfg.particleViewDistance)
                        && !dev.tako.papersdelight.util.ParticleThrottle.shouldSkip(
                                countNearbyParticleTasks(location),
                                cfg.particleThrottle.threshold, cfg.particleThrottle.maxRate)) {
                    animationTick(block, ctrl.hasWaitingOutput(), cfg);
                }
            }
        } else {
            forgetParticles(location);
        }

        CookingSession session = activeSessions.get(location);
        boolean idle = session == null && !ctrl.hasAnyIngredient() && !ctrl.isCooking() && !ctrl.hasWaitingOutput();
        if (idle && block.getRelative(BlockFace.UP).getType() != Material.HOPPER) return;
        // support 是纯视觉方块属性（托盘支撑形态），仅玩家 toggleSupport 与本维护写入器读写，
        // 无玩法逻辑消费——挂到热源刷新同窗（每 HEAT_CACHE_TICKS 个补偿 tick 一次），
        // 免去每 tick 的 CE 属性读取 + isTraySource 热源定义扫描；变更延迟 ≤ 该窗口。
        if (heatRefreshed) updateAutomaticSupport(block);

        if (session == null) {
            CookingPotData data = ctrl.toData();
            boolean changed = false;
            for (int i = 0; i < elapsed; i++) {
                boolean idlePass = !hasInput(data) && !data.isCooking && isEmpty(data.waitingOutput);
                if (idlePass && block.getRelative(BlockFace.UP).getType() != Material.HOPPER) continue;
                if ((++ctrl.hopperTicks % 8) == 0) changed |= processHoppers(block, data);
                if (idlePass) continue;
                changed |= tickPot(location, block, data, null, heated);
            }
            if (changed) ctrl.fromData(data);
            return;
        }

        CookingPotData data = session.data;
        long version = session.version();
        // R8 空闲快路径快照（区域线程读块、数据字段读与会话分支既有模式一致）：
        // 数据全空（无输入/未烹饪/无待取出/无成品）且六个相邻位无漏斗（侧向漏斗空闲时仍可投餐具、
        // 下方漏斗可取成品，不能跳过）——此后若玩家也未编辑且热指示未变，本 tick 无事可做。
        final boolean idleNoHopper = !hasInput(data) && !data.isCooking
                && isEmpty(data.waitingOutput) && isEmpty(data.finalOutput)
                && !hasHopperNeighbor(block);
        Runnable hopperTick = () -> {
            if (!isCurrentSessionVersion(location, session, version)) return;
            boolean inputChanged = syncOwnerGuiEditableSlots(session, data);
            // 空闲快路径：跳过 regionStep 与 refresh 两次调度提交（3→1 次/tick）；
            // 与无会话空闲分支的 hopperTicks 不递增语义同型
            if (!inputChanged && idleNoHopper && heated == session.lastRefreshedHeated) return;
            Runnable regionStep = () -> {
                if (!isCurrentSessionVersion(location, session, version)) return;
                boolean stateChanged = inputChanged;
                for (int i = 0; i < elapsed; i++) {
                    if ((++ctrl.hopperTicks % 8) == 0) stateChanged |= processHoppers(block, data);
                    stateChanged |= tickPot(location, block, data, session, heated);
                }
                if (stateChanged) ctrl.fromData(data);
                // R8 无变化不刷新：GUI 展示的全部维度（食材/餐具=输入与漏斗、进度/待取/成品=tickPot、
                // 热图标=heated 对比 lastRefreshedHeated）本轮均未变化时，省去第 3 次调度提交
                if (!inputChanged && !stateChanged && heated == session.lastRefreshedHeated) return;
                Runnable refresh = () -> {
                    if (isCurrentSessionVersion(location, session, version)
                            && session.player.getOpenInventory().getTopInventory() == session.menu)
                        refreshInventory(session.menu, data, session, heated);
                };
                if (session.player == null || !session.player.isValid()) return;
                if (!plugin.isEnabled()) {
                    try { refresh.run(); } catch (Throwable ignored) { }
                    return;
                }
                SCHEDULER.getEntityScheduler().runTask(plugin, session.player, refresh);
            };
            if (location == null || location.getWorld() == null) return;
            SCHEDULER.getRegionScheduler().runTask(plugin, location, regionStep);
        };
        if (session.player == null || !session.player.isValid()) return;
        if (!plugin.isEnabled()) {
            try { hopperTick.run(); } catch (Throwable ignored) { }
            return;
        }
        SCHEDULER.getEntityScheduler().runTask(plugin, session.player, hopperTick);
    }

    private boolean tickPot(Location location, Block block, CookingPotData data, CookingSession session, boolean heated) {
        CookingRecipe recipe;
        if (!hasInput(data)) {
            recipe = null;
        } else {
            long epoch = recipeManager.snapshotEpoch();
            CookingPotRecipeCache.CacheResult cached = recipeCache.get(location, data.ingredients, epoch);
            if (cached instanceof CookingPotRecipeCache.CacheResult.Hit hit) {
                recipe = hit.recipe();
            } else {
                recipe = recipeManager.findMatch(data.ingredients);
                recipeCache.put(location, data.ingredients, epoch, recipe);
            }
        }
        boolean canCook = recipe != null && canStoreMeal(data, recipe);
        boolean changed = false;

        if (heated && canCook) {
            data.isCooking = true;
            data.recipeResult = recipe.result;
            data.recipeResultCount = recipe.resultCount;
            data.recipeContainer = resolveContainer(recipe);
            data.cookTimeTotal = Math.max(1, recipe.cookingTime);
            data.cookDurationTicks = data.cookTimeTotal;
            data.cookTime++;
            changed = true;
            if (data.cookTime >= data.cookTimeTotal) {
                finishCooking(block, data, recipe, session);
            }
        } else {
            if (data.isCooking) changed = true;
            data.isCooking = false;
            if (data.cookTime > 0) {
                data.cookTime = Math.max(0, data.cookTime - 2);
                changed = true;
            }
        }

        int nextProgress = data.cookTimeTotal <= 0 ? 0
                : Math.min(100, data.cookTime * 100 / data.cookTimeTotal);
        if (data.progress != nextProgress) {
            data.progress = nextProgress;
            changed = true;
        }
        changed |= moveMealToOutput(data);
        return changed;
    }

    void updateAutomaticSupport(Block block) {
        String support = CraftEngineUtil.getCustomBlockProperty(block, "support");
        if (support == null || "2".equals(support)) return;
        String expected = isTraySource(block) ? "1" : "0";
        if (!expected.equals(support)) CraftEngineUtil.setCustomBlockProperty(block, "support", expected);
    }

    private boolean processHoppers(Block block, CookingPotData data) {
        boolean changed = false;

        Block above = block.getRelative(BlockFace.UP);
        if (above.getType() == Material.HOPPER && above.getState() instanceof Container container) {
            changed |= moveOneIntoIngredients(container.getInventory(), data);
        }
        for (BlockFace face : SIDE_HOPPER_FACES) {
            Block side = block.getRelative(face);
            if (side.getType() != Material.HOPPER || !(side.getState() instanceof Container container)
                    || !(side.getBlockData() instanceof Directional directional)) continue;
            if (!side.getRelative(directional.getFacing()).equals(block)) continue;
            changed |= moveOneIntoContainer(container.getInventory(), data);
        }

        Block below = block.getRelative(BlockFace.DOWN);
        if (!isEmpty(data.finalOutput) && below.getType() == Material.HOPPER
                && below.getState() instanceof Container container) {
            ItemStack one = data.finalOutput.clone();
            one.setAmount(1);
            Map<Integer, ItemStack> leftovers = container.getInventory().addItem(one);
            if (leftovers.isEmpty()) {
                shrink(data.finalOutput, 1);
                if (isEmpty(data.finalOutput)) data.finalOutput = null;
                changed = true;
            }
        }
        return changed;
    }

    /** R8：六个相邻位是否存在漏斗（保守判定，不查朝向）。仅空闲快路径使用，区域线程调用。 */
    private static boolean hasHopperNeighbor(Block block) {
        if (block.getRelative(BlockFace.UP).getType() == Material.HOPPER) return true;
        if (block.getRelative(BlockFace.DOWN).getType() == Material.HOPPER) return true;
        for (BlockFace face : SIDE_HOPPER_FACES) {
            if (block.getRelative(face).getType() == Material.HOPPER) return true;
        }
        return false;
    }

    private boolean moveOneIntoIngredients(Inventory source, CookingPotData data) {
        for (int sourceSlot = 0; sourceSlot < source.getSize(); sourceSlot++) {
            ItemStack sourceStack = source.getItem(sourceSlot);
            if (isEmpty(sourceStack)) continue;
            for (int targetSlot = 0; targetSlot < data.ingredients.length; targetSlot++) {
                ItemStack target = data.ingredients[targetSlot];
                if (!isEmpty(target) && (!target.isSimilar(sourceStack) || target.getAmount() >= target.getMaxStackSize())) continue;
                if (isEmpty(target)) {
                    data.ingredients[targetSlot] = sourceStack.clone();
                    data.ingredients[targetSlot].setAmount(1);
                } else {
                    target.setAmount(target.getAmount() + 1);
                }
                shrink(sourceStack, 1);
                if (isEmpty(sourceStack)) source.setItem(sourceSlot, null);
                return true;
            }
        }
        return false;
    }

    private boolean moveOneIntoContainer(Inventory source, CookingPotData data) {
        for (int sourceSlot = 0; sourceSlot < source.getSize(); sourceSlot++) {
            ItemStack sourceStack = source.getItem(sourceSlot);
            if (isEmpty(sourceStack)) continue;
            if (!isEmpty(data.utensil)
                    && (!data.utensil.isSimilar(sourceStack) || data.utensil.getAmount() >= data.utensil.getMaxStackSize())) continue;
            if (isEmpty(data.utensil)) {
                data.utensil = sourceStack.clone();
                data.utensil.setAmount(1);
            } else {
                data.utensil.setAmount(data.utensil.getAmount() + 1);
            }
            shrink(sourceStack, 1);
            if (isEmpty(sourceStack)) source.setItem(sourceSlot, null);
            return true;
        }
        return false;
    }

    private String resolveContainer(CookingRecipe recipe) {
        if (recipe == null) return null;
        if (recipe.container != null && !recipe.container.isBlank()) return recipe.container;
        if (recipe.result == null || recipe.result.isEmpty()) return null;

        String cached = containerFallbackCache.computeIfAbsent(recipe.result, id -> {
            String remainder = CraftEngineUtil.getCraftRemainderId(id);
            return remainder == null ? "" : remainder;
        });
        return cached.isEmpty() ? null : cached;
    }

    private void finishCooking(Block block, CookingPotData data, CookingRecipe recipe, CookingSession session) {
        ItemStack result = CraftEngineUtil.createItem(recipe.result, recipe.resultCount);
        if (result == null || result.isEmpty()) return;

        if (!isEmpty(data.waitingOutput)) {
            if (data.waitingOutput.getAmount() + result.getAmount() > WAITING_OUTPUT_CAPACITY) return;
        }

        if (isEmpty(data.waitingOutput)) data.waitingOutput = result;
        else data.waitingOutput.setAmount(data.waitingOutput.getAmount() + result.getAmount());
        data.recipeContainer = resolveContainer(recipe);
        data.storedExperience += recipe.experience;
        consumeIngredients(block, data);
        data.cookTime = 0;
        data.progress = 0;
        if (session != null) session.skipNextIngredientRead = true;
    }

    private void consumeIngredients(Block block, CookingPotData data) {
        for (int i = 0; i < data.ingredients.length; i++) {
            ItemStack ingredient = data.ingredients[i];
            if (isEmpty(ingredient)) continue;
            ItemStack remainder = ingredientRemainder(ingredient);
            shrink(ingredient, 1);
            if (isEmpty(ingredient)) data.ingredients[i] = null;
            if (remainder != null) ejectRemainder(block, remainder);
        }
    }

    private ItemStack ingredientRemainder(ItemStack input) {
        Material remainder = switch (input.getType()) {
            case WATER_BUCKET, LAVA_BUCKET, MILK_BUCKET, POWDER_SNOW_BUCKET, AXOLOTL_BUCKET,
                 COD_BUCKET, PUFFERFISH_BUCKET, SALMON_BUCKET, TROPICAL_FISH_BUCKET -> Material.BUCKET;
            case MUSHROOM_STEW, RABBIT_STEW, BEETROOT_SOUP, SUSPICIOUS_STEW -> Material.BOWL;
            case POTION, SPLASH_POTION, LINGERING_POTION, EXPERIENCE_BOTTLE, HONEY_BOTTLE -> Material.GLASS_BOTTLE;
            default -> null;
        };
        if (remainder != null) return new ItemStack(remainder);
        String id = CraftEngineUtil.getCustomItemId(input);
        if ("farmersdelight:milk_bottle".equals(id)) return new ItemStack(Material.GLASS_BOTTLE);
        if ("farmersdelight:tomato_sauce".equals(id)) return new ItemStack(Material.BOWL);
        return null;
    }

    private void ejectRemainder(Block block, ItemStack remainder) {
        String facing = Optional.ofNullable(CraftEngineUtil.getCustomBlockProperty(block, "facing")).orElse("north");
        Vector velocity = switch (facing) {
            case "east" -> new Vector(0, 0.25, -0.08);
            case "south" -> new Vector(0.08, 0.25, 0);
            case "west" -> new Vector(0, 0.25, 0.08);
            default -> new Vector(-0.08, 0.25, 0);
        };
        block.getWorld().dropItem(block.getLocation().add(0.5, 0.7, 0.5), remainder).setVelocity(velocity);
    }

    private boolean moveMealToOutput(CookingPotData data) {
        if (isEmpty(data.waitingOutput)) return false;
        if (!isEmpty(data.finalOutput) && !data.finalOutput.isSimilar(data.waitingOutput)) return false;
        int outputCount = isEmpty(data.finalOutput) ? 0 : data.finalOutput.getAmount();

        int room = Math.max(0, data.waitingOutput.getMaxStackSize() - outputCount);
        if (room <= 0) return false;

        int amount;
        if (data.recipeContainer == null || data.recipeContainer.isBlank()) {
            amount = Math.min(room, data.waitingOutput.getAmount());
        } else {
            if (isEmpty(data.utensil) || !CraftEngineUtil.isItem(data.utensil, data.recipeContainer)) return false;
            amount = Math.min(room, Math.min(data.waitingOutput.getAmount(), data.utensil.getAmount()));
            shrink(data.utensil, amount);
            if (isEmpty(data.utensil)) data.utensil = null;
        }
        if (amount <= 0) return false;
        if (isEmpty(data.finalOutput)) {
            data.finalOutput = data.waitingOutput.clone();
            data.finalOutput.setAmount(amount);
        } else {
            data.finalOutput.setAmount(data.finalOutput.getAmount() + amount);
        }
        shrink(data.waitingOutput, amount);
        if (isEmpty(data.waitingOutput)) {
            data.waitingOutput = null;
            data.recipeContainer = null;
        }
        return true;
    }

    private boolean canStoreMeal(CookingPotData data, CookingRecipe recipe) {
        // 原型缓存：逐 tick 判定复用共享只读产物实例，免走 CraftEngine 构建管线
        ItemStack result = recipeManager.resultPrototype(recipe);
        if (result == null || result.isEmpty()) return false;
        if (isEmpty(data.waitingOutput)) return true;
        if (!data.waitingOutput.isSimilar(result)) return false;
        return data.waitingOutput.getAmount() + result.getAmount() <= WAITING_OUTPUT_CAPACITY;
    }

    private boolean syncOwnerGuiEditableSlots(CookingSession session, CookingPotData data) {
        Inventory open = session.player.getOpenInventory().getTopInventory();
        if (open != session.menu) return false;
        return syncEditableSlots(session, data, open);
    }

    private boolean syncEditableSlots(CookingSession session, CookingPotData data, Inventory inventory) {
        boolean changed = false;
        if (session != null && session.skipNextIngredientRead) {
            refreshIngredientSlots(inventory, data);
            session.skipNextIngredientRead = false;
        } else {
            for (int i = 0; i < CookingPotLayout.INGREDIENTS.length; i++) {
                // 先比后克隆（R8）：稳态（玩家未编辑）每槽每 tick 省 1 次 take 克隆；
                // 不等才 take（克隆）写入数据模型，与库存镜像解耦的语义不变
                ItemStack next = inventory.getItem(CookingPotLayout.INGREDIENTS[i]);
                if (!itemsEqual(data.ingredients[i], next)) {
                    data.ingredients[i] = take(next);
                    changed = true;
                }
            }
        }
        ItemStack container = inventory.getItem(CookingPotLayout.UTENSIL);
        if (!itemsEqual(data.utensil, container)) {
            data.utensil = take(container);
            changed = true;
        }
        return changed;
    }

    private void refreshInventory(Inventory inventory, CookingPotData data, CookingSession session, boolean heated) {
        refreshIngredientSlots(inventory, data, session);
        populateInventory(inventory, data);
        updateHeatIndicator(inventory, heated);
        if (session != null) session.lastRefreshedHeated = heated;
    }

    private void refreshOpenSession(Block block, CookingPotData data) {
        CookingSession session = activeSessions.get(blockKey(block));
        if (session == null) return;
        Inventory inventory = openCookingPotInventory(session.player);
        if (inventory != null) refreshInventory(inventory, data, session, isHeated(block));
    }

    private static void refreshIngredientSlots(Inventory inventory, CookingPotData data) {
        for (int i = 0; i < CookingPotLayout.INGREDIENTS.length; i++) {
            refreshSlot(inventory, CookingPotLayout.INGREDIENTS[i], data.ingredients[i]);
        }
    }

    private static void refreshIngredientSlots(Inventory inventory, CookingPotData data, CookingSession session) {
        refreshIngredientSlots(inventory, data);
        if (session != null && session.data == data) session.skipNextIngredientRead = false;
    }

    private static void refreshSlot(Inventory inventory, int slot, ItemStack item) {
        // writeSlot 内部先比较、不等才写且写时克隆——外层预克隆在稳态（槽位未变）每 tick 每槽被丢弃一次
        writeSlot(inventory, slot, isEmpty(item) ? null : item);
    }

    private static void writeSlot(Inventory inventory, int slot, ItemStack item) {
        if (!itemsEqual(inventory.getItem(slot), item)) inventory.setItem(slot, item == null ? null : item.clone());
    }

    private String itemTranslationKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var def = CraftEngineItems.byItemStack(stack);
        if (def != null) return def.translationKey();
        return stack.getType().translationKey();
    }

    private Component translAtable(String itemId) {
        ItemStack item = CraftEngineUtil.createItem(itemId, 1);
        String key = itemTranslationKey(item);
        if (key != null) {
            return Component.translatable(key)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return Component.text(itemId)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    private void animationTick(Block block, boolean hasMeal, CookingPotConfig cfg) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextFloat() < 0.2f) {
            block.getWorld().spawnParticle(Particle.BUBBLE_POP,
                    block.getX() + 0.5 + random.nextDouble(-0.3, 0.3), block.getY() + 0.7,
                    block.getZ() + 0.5 + random.nextDouble(-0.3, 0.3), 1, 0, 0, 0, 0, null, false);
        }
        if (random.nextFloat() < 0.05f) {
            block.getWorld().spawnParticle(Particle.WHITE_SMOKE,
                    block.getX() + 0.5 + random.nextDouble(-0.2, 0.2), block.getY() + 0.55,
                    block.getZ() + 0.5 + random.nextDouble(-0.2, 0.2), 1, 0, 0.01, 0, 0, null, false);
        }
        if (random.nextInt(10) == 0 && !shouldThrottleSound(block, cfg)) {
            playSound(block, hasMeal
                    ? "farmersdelight:block.cooking_pot.boil_soup"
                    : "farmersdelight:block.cooking_pot.boil", 0.5f, random.nextFloat(0.9f, 1.1f));
        }
    }

    private boolean shouldThrottleSound(Block block, CookingPotConfig cfg) {
        int nearby = countNearbyParticleTasks(block.getLocation().toBlockLocation());
        return dev.tako.papersdelight.util.ParticleThrottle.shouldSkip(
                nearby, cfg.soundThrottle.threshold, cfg.soundThrottle.maxRate);
    }

    private void playSound(Block block, ConfigManager.SoundConfig cfg) {
        if (block == null || cfg == null) return;
        Location loc = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        if (loc.getWorld() == null) return;
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

    private void playSound(Block block, Sound sound, float volume, float pitch) {
        if (block == null || sound == null) return;
        Location loc = block.getLocation();
        if (loc.getWorld() == null) return;
        Runnable soundTask = () -> {
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
            world.playSound(loc, sound, volume, pitch);
        };
        if (!SCHEDULER.isFolia()) {
            if (Bukkit.isPrimaryThread()) { soundTask.run(); return; }
            SCHEDULER.getGlobalRegionScheduler().runTask(plugin, soundTask);
            return;
        }
        SCHEDULER.getRegionScheduler().runTask(plugin, loc, soundTask);
    }

    private void playSound(Block block, String sound, float volume, float pitch) {
        if (block == null || sound == null || sound.isBlank()) return;
        Location loc = block.getLocation();
        if (loc.getWorld() == null) return;
        Runnable soundTask = () -> {
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
            world.playSound(loc, sound, volume, pitch);
        };
        if (!SCHEDULER.isFolia()) {
            if (Bukkit.isPrimaryThread()) { soundTask.run(); return; }
            SCHEDULER.getGlobalRegionScheduler().runTask(plugin, soundTask);
            return;
        }
        SCHEDULER.getRegionScheduler().runTask(plugin, loc, soundTask);
    }

    private void forgetParticles(Location loc) {
        if (particlePots.remove(loc)) adjustChunkParticleCount(loc, -1);
    }

    private void adjustChunkParticleCount(Location loc, int delta) {
        long key = chunkKey(loc);
        java.util.concurrent.atomic.AtomicInteger count = chunkParticleCount.get(key);
        if (delta > 0) {
            if (count == null) count = chunkParticleCount.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicInteger());
            count.incrementAndGet();
            return;
        }
        if (count != null && count.addAndGet(-1) <= 0) chunkParticleCount.remove(key);
    }

    private int countNearbyParticleTasks(Location loc) {
        java.util.concurrent.atomic.AtomicInteger count = chunkParticleCount.get(chunkKey(loc));
        return count != null ? count.get() : 0;
    }

    private static long chunkKey(Location loc) {
        int worldMix = loc.getWorld() != null
                ? (int) (loc.getWorld().getUID().getLeastSignificantBits() >>> 48) : 0;
        return ((long) ((loc.getBlockX() >> 4) ^ worldMix)) << 32 | ((long) (loc.getBlockZ() >> 4)) & 0xFFFFFFFFL;
    }

    public CookingPotData load(Block block) {
        CookingPotBlockEntityController ctrl = getController(block);
        return ctrl != null ? ctrl.toData() : new CookingPotData();
    }

    public void save(Block block, CookingPotData data) {
        CookingPotBlockEntityController ctrl = getController(block);
        if (ctrl != null) ctrl.fromData(data);
        trackedLocations.add(blockKey(block));
    }

    public void remove(Block block) {
        Location key = blockKey(block);
        trackedLocations.remove(key);
        recipeCache.remove(key);
    }

    public boolean isHeated(Block block) {
        return HeatSourceService.isHeated(block);
    }

    public boolean isTraySource(Block block) {
        List<ConfigManager.HeatSourceDef> heatSources = ConfigManager.getHeatSources();
        Block below = block.getRelative(BlockFace.DOWN);
        for (ConfigManager.HeatSourceDef definition : heatSources) {
            if (definition.tray() && !definition.conductor() && matchesBlockDef(below, definition)) return true;
        }

        for (ConfigManager.HeatSourceDef definition : heatSources) {
            if (!definition.conductor() || !matchesBlockDef(below, definition)) continue;
            Block twoBelow = block.getRelative(BlockFace.DOWN, 2);
            for (ConfigManager.HeatSourceDef heat : heatSources) {
                if (heat.tray() && !heat.conductor() && matchesBlockDef(twoBelow, heat)) return true;
            }
            break;
        }
        return false;
    }

    private boolean isMatchingHeatSource(Block block, ConfigManager.HeatSourceDef definition) {
        return HeatSourceService.matchesBlockDef(block, definition) && HeatSourceService.checkLit(block);
    }

    private boolean matchesBlockDef(Block block, ConfigManager.HeatSourceDef definition) {
        return HeatSourceService.matchesBlockDef(block, definition);
    }

    private boolean isCookingPot(Block block) {
        return getController(block) != null;
    }

    private boolean isCookingPotIdentity(Block block) {
        return potIdentity.isPotBehavior(block);
    }

    private boolean isCurrentCookingPot(Block block) {
        return potIdentity.isCurrentPot(block);
    }

    private boolean dropStatefulPot(Block block, CookingPotData data, boolean dropContents, boolean dropBlock) {
        Location drop = block.getLocation().add(.5, .5, .5);
        if (dropContents) {
            for (ItemStack ingredient : data.ingredients) dropIfPresent(block.getWorld(), drop, ingredient);
            dropIfPresent(block.getWorld(), drop, data.utensil);
            dropIfPresent(block.getWorld(), drop, data.finalOutput);
            spawnStoredExperience(block.getWorld(), drop, data);
        } else {
            data.storedExperience = 0;
        }
        if (!dropBlock) return false;
        String dropId = CookingPotDropId.resolve(CraftEngineUtil.getCustomBlockId(block), FALLBACK_POT_ID);
        ItemStack pot = CraftEngineUtil.createItem(dropId, 1);
        if (pot == null && dropId != null && !dropId.equals(FALLBACK_POT_ID)) {
            plugin.getLogger().warning(ConfigManager.getOr("cooking_pot_drop_item_missing", "厨锅方块 %block_id% 没有同 ID 的 CE 物品，掉落回退到 %fallback_id%；请为该方块提供同 ID 物品定义。")
                    .replace("%block_id%", dropId).replace("%fallback_id%", FALLBACK_POT_ID));
            pot = CraftEngineUtil.createItem(FALLBACK_POT_ID, 1);
        }
        if (pot == null) return false;
        if (!isEmpty(data.waitingOutput)) MealLoreUtil.applyMealLore(pot, data.waitingOutput);
        ItemMeta meta = pot.getItemMeta();
        if (meta != null) {
            if (!isEmpty(data.waitingOutput)) {
                meta.getPersistentDataContainer().set(carriedMealKey, PersistentDataType.BYTE_ARRAY, data.waitingOutput.serializeAsBytes());
                if (data.recipeContainer != null) meta.getPersistentDataContainer().set(carriedContainerKey, PersistentDataType.STRING, data.recipeContainer);
            }
            pot.setItemMeta(meta);
        }
        block.getWorld().dropItemNaturally(drop, pot);
        return true;
    }

    private static boolean hasInput(CookingPotData data) {
        for (ItemStack ingredient : data.ingredients) if (!isEmpty(ingredient)) return true;
        return false;
    }

    private void awardStoredExperience(Player player, CookingPotData data) {
        spawnStoredExperience(player.getWorld(), player.getLocation(), data);
    }

    private void spawnStoredExperience(World world, Location location, CookingPotData data) {
        int experience = (int) Math.floor(data.storedExperience);
        float fraction = data.storedExperience - experience;
        if (fraction > 0 && Math.random() < fraction) experience++;
        data.storedExperience = 0;
        if (experience <= 0) return;
        int value = experience;
        world.spawn(location, ExperienceOrb.class, orb -> orb.setExperience(value));
    }

    private CookingSession findSession(Player player) {
        if (player == null) return null;
        Location key = playerSessions.get(player.getUniqueId());
        if (key == null) return null;
        CookingSession session = activeSessions.get(key);
        return session != null && session.player.getUniqueId().equals(player.getUniqueId()) ? session : null;
    }

    public Location findSessionLocation(Player player) {
        if (player == null) return null;
        CookingSession session = findSession(player);
        return session != null ? blockKey(session.block) : null;
    }

    private Inventory openCookingPotInventory(Player player) {
        if (player == null || !player.isOnline()) return null;
        Inventory top = player.getOpenInventory().getTopInventory();
        int size = top.getSize();
        return (size == CookingPotLayout.SIZE || size == CookingPotLayout.EXPANDED_SIZE) ? top : null;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static void dropIfPresent(World world, Location location, ItemStack item) {
        if (!isEmpty(item)) world.dropItemNaturally(location, item);
    }

    private static ItemStack take(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static void shrink(ItemStack item, int amount) {
        if (item != null) item.setAmount(Math.max(0, item.getAmount() - amount));
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static boolean itemsEqual(ItemStack first, ItemStack second) {
        if (isEmpty(first)) return isEmpty(second);
        return !isEmpty(second) && first.getAmount() == second.getAmount() && first.isSimilar(second);
    }

    static Location blockKey(Block block) {
        return blockKey(block.getLocation());
    }

    static Location blockKey(Location location) {
        return location.toBlockLocation();
    }

    private static final class PendingUnload {
        final CookingSession session;
        final CookingPotMenuCloseFlow.EditableSnapshot snapshot;
        final long token;
        PendingUnload(CookingSession session, CookingPotMenuCloseFlow.EditableSnapshot snapshot, long token) {
            this.session = session;
            this.snapshot = snapshot;
            this.token = token;
        }
    }

    private static final class CookingSession {
        final Block block;
        final CookingPotData data;
        final Player player;
        final long generation;
        final Inventory menu;
        boolean closeRequested;
        long unloadGeneration;
        volatile long operationVersion;
        boolean skipNextIngredientRead;
        /** R8 空闲快路径：上次 refreshInventory 使用的热态——未变化时跳过整次刷新调度 */
        boolean lastRefreshedHeated;

        long version() {
            return operationVersion;
        }

        void invalidate() {
            operationVersion++;
        }

        CookingSession(Block block, CookingPotData data, Player player, long generation, Inventory menu) {
            this.block = block;
            this.data = data;
            this.player = player;
            this.generation = generation;
            this.menu = menu;
        }
    }
    }
