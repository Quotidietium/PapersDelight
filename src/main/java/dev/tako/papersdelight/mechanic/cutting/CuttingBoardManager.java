package dev.tako.papersdelight.mechanic.cutting;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.config.ConfigManager.SoundConfig;
import dev.tako.papersdelight.api.protection.ProtectionGate;
import dev.tako.papersdelight.recipe.DefaultItemMatcherResolver;
import dev.tako.papersdelight.api.item.ItemMatcher;
import dev.tako.papersdelight.api.item.ItemResult;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.util.TextUtil;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CuttingBoardManager implements Listener {

    private static final String[] DEFAULT_RECIPES = {};

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private final JavaPlugin plugin;
    private final HopperScheduler hopperScheduler;
    private volatile CuttingRuntimeState runtimeState = CuttingRuntimeState.empty();

    private final Map<Location, List<ItemDisplay>> displayEntities = new ConcurrentHashMap<>();

    static CuttingBoardManager instance;

    private final Set<Location> trackedBoards = ConcurrentHashMap.newKeySet();

    private cn.chengzhimeow.ccscheduler.task.CCTask hopperTask;
    private volatile long hopperGeneration;
    private volatile int hopperTickRuns;

    private final Set<Location> recentlyPlaced = ConcurrentHashMap.newKeySet();

    public CuttingBoardManager(JavaPlugin plugin) {
        this(plugin, callback -> {
            if (plugin == null) return null;
            return SCHEDULER.getGlobalRegionScheduler().runTaskTimer(plugin, 8L, 8L, callback);
        });
    }

    CuttingBoardManager(JavaPlugin plugin, HopperScheduler hopperScheduler) {
        this.plugin = plugin;
        this.hopperScheduler = Objects.requireNonNull(hopperScheduler, "hopperScheduler");
    }

    public record RuntimeSettings(
            Map<String, SoundConfig> toolSounds,
            List<String> defaultTools,
            Map<String, ToolPosition> toolPositions,
            ToolPosition defaultToolPosition,
            SoundConfig soundPlace,
            SoundConfig soundTake,
            SoundConfig soundInsertTool,
            float fallbackVolume,
            float fallbackPitch,
            boolean hopperInteraction
    ) {
        public RuntimeSettings {
            toolSounds = Collections.unmodifiableMap(new LinkedHashMap<>(
                    toolSounds == null ? Map.of() : toolSounds));
            defaultTools = List.copyOf(defaultTools == null ? List.of() : defaultTools);
            toolPositions = Collections.unmodifiableMap(new LinkedHashMap<>(
                    toolPositions == null ? Map.of() : toolPositions));
            defaultToolPosition = defaultToolPosition == null
                    ? new ToolPosition(0.0, 0.23, 0.0, 0.7, 0.0, 90.0, 0.0)
                    : defaultToolPosition;
            soundPlace = soundPlace == null
                    ? new SoundConfig("farmersdelight:block.cutting_board.place_item", 1.0f, 0.8f)
                    : soundPlace;
            soundTake = soundTake == null
                    ? new SoundConfig("farmersdelight:block.cutting_board.remove_item", 0.25f, 0.5f)
                    : soundTake;
            soundInsertTool = soundInsertTool == null
                    ? new SoundConfig("farmersdelight:block.cutting_board.carve_tool", 1.0f, 0.8f)
                    : soundInsertTool;
        }

        static RuntimeSettings empty() {
            return new RuntimeSettings(Map.of(), List.of(), Map.of(), null,
                    null, null, null, 1.0f, 0.8f, false);
        }
    }

    @FunctionalInterface
    interface HopperScheduler {
        cn.chengzhimeow.ccscheduler.task.CCTask schedule(Runnable callback);
    }

    private record CuttingRuntimeState(
            List<CuttingRecipe> recipes,
            RuntimeSettings settings,
            ItemMatcher defaultToolMatcher
    ) {
        private CuttingRuntimeState {
            recipes = List.copyOf(recipes);
            settings = Objects.requireNonNull(settings, "settings");
            defaultToolMatcher = Objects.requireNonNull(defaultToolMatcher, "defaultToolMatcher");
        }

        static CuttingRuntimeState empty() {
            return new CuttingRuntimeState(List.of(), RuntimeSettings.empty(), ItemMatcher.empty());
        }
    }

    public static final class RuntimeSnapshot {
        private final CuttingRuntimeState state;
        private final boolean hopperTaskActive;
        private final CuttingBoardManager activeInstance;

        private RuntimeSnapshot(
                CuttingRuntimeState state,
                boolean hopperTaskActive,
                CuttingBoardManager activeInstance
        ) {
            this.state = Objects.requireNonNull(state, "state");
            this.hopperTaskActive = hopperTaskActive;
            this.activeInstance = activeInstance;
        }
    }

    public synchronized RuntimeSnapshot captureRuntimeState() {
        return new RuntimeSnapshot(runtimeState, hopperTask != null, instance);
    }

    public synchronized void restoreRuntimeState(RuntimeSnapshot snapshot) {
        RuntimeSnapshot target = Objects.requireNonNull(snapshot, "snapshot");
        cn.chengzhimeow.ccscheduler.task.CCTask currentTask = hopperTask;
        cn.chengzhimeow.ccscheduler.task.CCTask restoredTask = null;
        long restoredGeneration = hopperGeneration;

        if (target.hopperTaskActive && currentTask == null) {
            restoredGeneration = hopperGeneration + 1L;
            long callbackGeneration = restoredGeneration;
            restoredTask = Objects.requireNonNull(
                    hopperScheduler.schedule(() -> hopperTick(callbackGeneration)),
                    "hopper scheduler returned null task");
        }

        if (!target.hopperTaskActive && currentTask != null) {
            hopperGeneration++;
            hopperTask = null;
        } else if (restoredTask != null) {
            hopperGeneration = restoredGeneration;
            hopperTask = restoredTask;
        }
        runtimeState = target.state;
        instance = target.activeInstance;

        if (!target.hopperTaskActive && currentTask != null) {
            cancelHopperTaskBestEffort(currentTask);
        }
    }

    public RuntimeSettings reloadRuntimeSettings() {
        SoundConfig loadedPlace = ConfigManager.readSoundConfig("cutting_board.sounds.place_item",
                "farmersdelight:block.cutting_board.place_item", 1.0f, 0.8f);
        SoundConfig loadedTake = ConfigManager.readSoundConfig("cutting_board.sounds.remove_item",
                "farmersdelight:block.cutting_board.remove_item", 0.25f, 0.5f);
        SoundConfig loadedInsert = ConfigManager.readSoundConfig("cutting_board.sounds.carve_tool",
                "farmersdelight:block.cutting_board.carve_tool", 1.0f, 0.8f);
        Map<String, SoundConfig> loadedToolSounds = new LinkedHashMap<>();
        ConfigurationSection toolSec = ConfigManager.getConfig()
                .getConfigurationSection("cutting_board.tool_sounds");
        if (toolSec != null) {
            for (String toolKey : toolSec.getKeys(false)) {
                SoundConfig sound = ConfigManager.readSoundOrSimple(
                        ConfigManager.getConfig(), "cutting_board.tool_sounds." + toolKey);
                if (sound != null) {
                    loadedToolSounds.put(normalizeToolExpression(toolKey), sound);
                }
            }
        }
        float loadedPitch = (float) ConfigManager.getConfig()
                .getDouble("cutting_board.sounds.fallback.pitch", 0.8f);
        float loadedVolume = (float) ConfigManager.getConfig()
                .getDouble("cutting_board.sounds.fallback.volume", 1.0f);
        List<String> loadedDefaultTools = normalizeToolExpressions(
                ConfigManager.getStringOrStringList("cutting_board.default_tools"));
        ToolPositions positions = readToolPositions();
        RuntimeSettings settings = new RuntimeSettings(
                loadedToolSounds, loadedDefaultTools, positions.positions(), positions.defaultPosition(),
                loadedPlace, loadedTake, loadedInsert, loadedVolume, loadedPitch,
                ConfigManager.getBoolean("cutting_board.hopper_interaction", true));
        return settings;
    }

    public RuntimeSettings currentRuntimeSettings() {
        return runtimeState.settings();
    }

    public synchronized void publishRuntimeConfig(List<CuttingRecipe> loadedRecipes,
                                                   RuntimeSettings settings) {
        RuntimeSettings nextSettings = settings == null ? RuntimeSettings.empty() : settings;
        List<CuttingRecipe> suppliedRecipes = List.copyOf(loadedRecipes == null ? List.of() : loadedRecipes);
        List<CuttingRecipe> nextRecipes = suppliedRecipes.stream()
                .map(recipe -> withDefaultTools(recipe, nextSettings.defaultTools()))
                .toList();
        ItemMatcher nextDefaultToolMatcher = ItemMatcher.anyOf(nextSettings.defaultTools());
        validateRecipeTags(nextRecipes);
        CuttingRuntimeState nextState = new CuttingRuntimeState(
                nextRecipes, nextSettings, nextDefaultToolMatcher);

        CuttingRuntimeState previousState = runtimeState;
        boolean wasHopperEnabled = previousState.settings().hopperInteraction() && hopperTask != null;
        boolean enableHopper = nextSettings.hopperInteraction();
        cn.chengzhimeow.ccscheduler.task.CCTask previousTask = hopperTask;
        cn.chengzhimeow.ccscheduler.task.CCTask candidateTask = null;
        long candidateGeneration = hopperGeneration;

        if (enableHopper && !wasHopperEnabled) {
            candidateGeneration = hopperGeneration + 1L;
            long callbackGeneration = candidateGeneration;
            candidateTask = Objects.requireNonNull(
                    hopperScheduler.schedule(() -> hopperTick(callbackGeneration)),
                    "hopper scheduler returned null task");
        }

        if (!enableHopper && previousTask != null) {
            hopperGeneration++;
            hopperTask = null;
        } else if (candidateTask != null) {
            hopperGeneration = candidateGeneration;
            hopperTask = candidateTask;
        }
        runtimeState = nextState;
        instance = this;

        if (!enableHopper && previousTask != null) {
            cancelHopperTaskBestEffort(previousTask);
        }
    }

    private static CuttingRecipe withDefaultTools(CuttingRecipe recipe, List<String> defaultTools) {
        if (!recipe.tools().isEmpty()) return recipe;
        return new CuttingRecipe(
                recipe.input(), defaultTools, recipe.results(), recipe.sound(), recipe.source(),
                recipe.inputMatcher(), ItemMatcher.anyOf(defaultTools));
    }

    private void cancelHopperTaskBestEffort(cn.chengzhimeow.ccscheduler.task.CCTask task) {
        try {
            task.cancel();
        } catch (Throwable throwable) {
            if (plugin != null) {
                plugin.getLogger().warning(ConfigManager.getOr(
                        "cutting_cancel_old_hopper_task_failed", "取消旧砧板漏斗任务失败；旧 generation 已失效: %error%")
                        .replace("%error%", ConfigManager.describeError(throwable)));
            }
        }
    }

    private void validateRecipeTags(List<CuttingRecipe> candidateRecipes) {
        Map<String, Set<String>> undefinedTags = new LinkedHashMap<>();
        for (CuttingRecipe recipe : candidateRecipes) {
            if (recipe.input().startsWith("#")) {
                String tag = recipe.input().substring(1).toLowerCase(Locale.ROOT);
                if (!isBukkitTag(tag) && !isCeTag(tag)) {
                    undefinedTags.computeIfAbsent(recipe.input(), k -> new LinkedHashSet<>())
                            .add(recipe.source());
                }
            }
            for (String tool : recipe.tools()) {
                if (tool.startsWith("#")) {
                    String tag = tool.substring(1).toLowerCase(Locale.ROOT);
                    if (!isBukkitTag(tag) && !isCeTag(tag)) {
                        undefinedTags.computeIfAbsent(tool, k -> new LinkedHashSet<>())
                                .add(recipe.source());
                    }
                }
            }
        }
        if (!undefinedTags.isEmpty() && plugin != null) {
            StringBuilder sb = new StringBuilder(ConfigManager.getOr(
                    "cutting_undefined_tags_header", "砧板配方引用了未定义的 tag："));
            for (var entry : undefinedTags.entrySet()) {
                sb.append(ConfigManager.getOr(
                        "cutting_undefined_tags_entry", "\n  - %tag%（来源：%sources%）")
                        .replace("%tag%", entry.getKey())
                        .replace("%sources%", String.join(", ", entry.getValue())));
            }
            plugin.getLogger().warning(sb.toString());
        }
    }

    private static List<String> normalizeToolExpressions(List<String> expressions) {
        if (expressions == null || expressions.isEmpty()) return List.of();
        return expressions.stream().map(CuttingBoardManager::normalizeToolExpression).toList();
    }

    private static String normalizeToolExpression(String expression) {
        if (expression == null) return null;
        return "#minecraft:shears".equalsIgnoreCase(expression.trim())
                ? "minecraft:shears"
                : expression;
    }

    public int countRecipes() {
        return runtimeState.recipes().size();
    }

    public List<CuttingRecipe> getRecipes() {
        return runtimeState.recipes();
    }

    RuntimeSettings runtimeSettingsForTests() {
        return runtimeState.settings();
    }

    boolean hasHopperTaskForTests() {
        return hopperTask != null;
    }

    long hopperGenerationForTests() {
        return hopperGeneration;
    }

    int hopperTickRunsForTests() {
        return hopperTickRuns;
    }

    cn.chengzhimeow.ccscheduler.task.CCTask hopperTaskForTests() {
        return hopperTask;
    }

    private static boolean isBukkitTag(String tag) {
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(tag);
            if (key == null) return false;
            return org.bukkit.Bukkit.getTag(org.bukkit.Tag.REGISTRY_ITEMS, key, org.bukkit.Material.class) != null;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isCeTag(String tag) {
        try {
            var items = net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems();
            if (items == null || items.isEmpty()) return true;
            net.momirealms.craftengine.core.util.Key ceKey =
                    net.momirealms.craftengine.core.util.Key.of(tag);
            for (var key : items.keySet()) {
                var def = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(key);
                if (def != null && def.is(ceKey)) return true;
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    public void markPlaced(Location location) {
        Location key = location.clone();
        recentlyPlaced.add(key);
        trackedBoards.add(key);
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, key, 2L, () -> recentlyPlaced.remove(key));
    }

    public boolean isRecentlyPlaced(Location location) {
        return recentlyPlaced.contains(location);
    }

    public void preDestroyCleanup(Location loc) {
        Location key = loc.clone();

        removeDisplayEntity(key);
        trackedBoards.remove(key);

        World world = loc.getWorld();
        if (world == null) return;
        Block block = loc.getBlock();
        CuttingBoardBlockEntityController controller = getController(block);
        if (controller != null) {
            if (controller.hasTool()) {
                ItemStack tool = controller.tool();
                if (tool != null) {
                    world.dropItemNaturally(key.clone().add(0.5, 0.3, 0.5), tool);
                }
                controller.tool(null);
            }
            if (controller.hasItem()) {
                ItemStack item = controller.item();
                if (item != null) {
                    world.dropItemNaturally(key.clone().add(0.5, 0.3, 0.5), item);
                }
                controller.item(null);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onShiftToolInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        org.bukkit.entity.Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (getController(block) == null) return;

        InteractionResult result = handleBoardInteract(player, block);
        if (result == InteractionResult.SUCCESS_AND_CANCEL || result == InteractionResult.FAIL) {
            event.setCancelled(true);
        }
    }

    InteractionResult handleBoardInteract(org.bukkit.entity.Player player, Block block) {
        if (!ProtectionGate.canInteract(player, block.getLocation())) {
            return InteractionResult.FAIL;
        }

        CuttingRuntimeState state = runtimeState;
        ItemStack held = player.getInventory().getItemInMainHand();

        if (player.isSneaking()) {
            if (!isConfiguredTool(held, state)) return InteractionResult.PASS;
        }

        if (isRecentlyPlaced(block.getLocation())) return InteractionResult.FAIL;

        CuttingBoardBlockEntityController controller = getController(block);
        if (controller == null) return InteractionResult.FAIL;

        boolean shifting = player.isSneaking();
        boolean holdingTool = isConfiguredTool(held, state);
        boolean holdingItem = !isVoid(held) && !holdingTool;
        boolean emptyHand = isVoid(held);
        boolean hasBoardItem = controller.hasItem();
        boolean hasBoardTool = controller.hasTool();

        if (shifting && holdingTool) {

            if (hasBoardItem) {
                return InteractionResult.FAIL;
            }
            if (hasBoardTool) {
                return InteractionResult.FAIL;
            }
            insertTool(player, block, controller, held);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (hasBoardItem) {

            if (holdingTool) {
                tryCut(player, block, controller, held);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            if (holdingItem && held.isSimilar(controller.item())) {
                addToStack(player, block, controller, held);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            if (holdingItem) {
                return InteractionResult.FAIL;
            }

            if (hasBoardTool) { takeTool(player, block, controller); return InteractionResult.SUCCESS_AND_CANCEL; }
            if (hasBoardItem) { takeItem(player, block, controller); }
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        if (hasBoardTool) {
            if (emptyHand) {
                takeTool(player, block, controller);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }
            return InteractionResult.FAIL;
        }

        if (holdingTool) {
            placeToolOnBoard(player, block, controller, held);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (holdingItem) {
            placeItem(player, block, controller, held);
        }
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (getController(block) == null && !displayEntities.containsKey(loc)) return;

        preDestroyCleanup(loc);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getChunk().getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, world, chunkX, chunkZ, 1L, () -> {
            scanChunkForCuttingBoards(world, chunkX, chunkZ);
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        World world = event.getWorld();
        for (Iterator<Map.Entry<Location, List<ItemDisplay>>> it = displayEntities.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Location, List<ItemDisplay>> entry = it.next();
            Location loc = entry.getKey();
            if (loc.getWorld() == world && (loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                for (ItemDisplay d : entry.getValue()) {
                    if (d != null && d.isValid()) d.remove();
                }
                it.remove();
            }
        }
        trackedBoards.removeIf(loc ->
                loc.getWorld() == world && (loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz);
    }

    private void placeItem(Player player, Block block, CuttingBoardBlockEntityController controller, ItemStack held) {
        int handAmount = held.getAmount();
        controller.item(held.clone());
        triggerComparatorUpdate(block);
        int actuallyPlaced = controller.item().getAmount();
        held.setAmount(handAmount - actuallyPlaced);

        spawnDisplayEntity(block, controller.item());
        player.swingMainHand();
        playSound(block, runtimeState.settings().soundPlace());
    }

    private void addToStack(Player player, Block block, CuttingBoardBlockEntityController controller, ItemStack held) {
        ItemStack boardItem = controller.item();
        int limit = Math.min(64, boardItem.getMaxStackSize());
        int canAdd = limit - boardItem.getAmount();
        if (canAdd <= 0) {
            return;
        }
        int toAdd = Math.min(held.getAmount(), canAdd);
        boardItem.setAmount(boardItem.getAmount() + toAdd);
        held.setAmount(held.getAmount() - toAdd);
        controller.markUnsaved();
        triggerComparatorUpdate(block);

        updateDisplayInPlace(block, boardItem);
        player.swingMainHand();
        playSound(block, runtimeState.settings().soundPlace());
    }

    private void placeToolOnBoard(Player player, Block block, CuttingBoardBlockEntityController controller, ItemStack held) {

        controller.item(null); controller.tool(null);
        removeDisplayEntity(block.getLocation());

        ItemStack tool = held.clone();
        tool.setAmount(1);
        controller.tool(tool);
        controller.setToolInserted(false);
        triggerComparatorUpdate(block);

        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }

        spawnDisplayEntity(block, tool);

        player.swingMainHand();
        playSound(block, runtimeState.settings().soundPlace());
    }

    private void insertTool(Player player, Block block, CuttingBoardBlockEntityController controller, ItemStack held) {
        ItemStack tool = held.clone();
        tool.setAmount(1);
        controller.tool(tool);
        controller.setToolInserted(true);
        triggerComparatorUpdate(block);

        controller.item(null);
        removeDisplayEntity(block.getLocation());

        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }

        spawnToolDisplayEntity(block, tool);

        player.swingMainHand();
        playSound(block, runtimeState.settings().soundInsertTool());
    }
    private void takeItem(Player player, Block block, CuttingBoardBlockEntityController controller) {
        ItemStack stored = controller.item();
        controller.item(null);
        triggerComparatorUpdate(block);
        removeDisplayEntity(block.getLocation());

        if (stored != null) {
            player.getInventory().addItem(stored).forEach((i, leftover) ->
                    block.getWorld().dropItemNaturally(
                            block.getLocation().clone().add(0.5, 0.8, 0.5), leftover));
        }

        player.swingMainHand();
        playSound(block, runtimeState.settings().soundTake());
    }
    private void takeTool(Player player, Block block, CuttingBoardBlockEntityController controller) {
        ItemStack tool = controller.tool();
        controller.tool(null);
        triggerComparatorUpdate(block);
        removeDisplayEntity(block.getLocation());

        if (tool != null) {
            player.getInventory().addItem(tool).forEach((i, leftover) ->
                    player.getWorld().dropItemNaturally(
                            player.getLocation().add(0, 0.5, 0), leftover));
        }

        player.swingMainHand();
        playSound(player.getLocation().getBlock(), runtimeState.settings().soundTake());
    }

    private void tryCut(Player player, Block block,
                         CuttingBoardBlockEntityController controller, ItemStack tool) {
        CuttingRuntimeState state = runtimeState;
        RuntimeSettings settings = state.settings();
        ItemStack boardItem = controller.item();
        if (boardItem == null || boardItem.isEmpty()) return;

        CuttingRecipe match = findRecipe(boardItem, tool, state);
        if (match == null) {
            player.sendActionBar(TextUtil.parse(player,
                    "<!i><lang:block.farmersdelight.cutting_board.invalid_tool>"));
            return;
        }

        dev.tako.papersdelight.stats.StatsManager stats =
                dev.tako.papersdelight.stats.StatsManager.getInstance();
        if (stats != null) {
            stats.record(player, dev.tako.papersdelight.stats.StatsManager.CUTTING_BOARD_CUT,
                    CraftEngineUtil.getItemIdentifier(boardItem), 1L);
        }

        int remaining = boardItem.getAmount() - 1;
        if (remaining <= 0) {
            controller.item(null);
            removeDisplayEntity(block.getLocation());
            player.sendActionBar(net.kyori.adventure.text.Component.text(" "));
            triggerComparatorUpdate(block);
        } else {
            boardItem.setAmount(remaining);
            controller.markUnsaved();
            triggerComparatorUpdate(block);
            updateDisplayInPlace(block, boardItem);

            String remMsg = "<!i><lang:block.farmersdelight.cutting_board.remaining_items:%d>";
            player.sendActionBar(TextUtil.parse(player, remMsg.formatted(remaining)));
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            damageTool(tool);
        }

        String blockFacing = getFacing(block);
        float rightX = 0f, rightZ = 0f;
        switch (blockFacing) {
            case "east" ->  { rightZ = -1f; }
            case "south" -> { rightX = 1f; }
            case "west" ->  { rightZ = 1f; }
            default ->       { rightX = -1f; }
        }
        Location dropLoc = block.getLocation().clone().add(0.5, 0.2, 0.5);
        World world = block.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int fortuneLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
        double fortuneBonus = fortuneLevel * ConfigManager.getDouble("cutting_board.fortune_bonus", 0.1);

        for (ItemResult result : match.results()) {
            if (result.chance() < 1.0) {
                double effectiveChance = Math.min(1.0, result.chance() + fortuneBonus);

                int outputAmount = result.count();
                for (int roll = 0; roll < result.count(); roll++) {
                    if (rng.nextDouble() >= effectiveChance) outputAmount--;
                }
                if (outputAmount <= 0) continue;
                ItemStack output = CraftEngineUtil.createItem(result.item(), outputAmount);
                if (output != null) {
                    org.bukkit.entity.Item itemEntity = world.dropItem(dropLoc, output);
                    itemEntity.setVelocity(new org.bukkit.util.Vector(rightX * 0.15, 0.1, rightZ * 0.15));
                    itemEntity.setPickupDelay(10);
                }
            } else {
                ItemStack output = CraftEngineUtil.createItem(result.item(), result.count());
                if (output != null) {
                    org.bukkit.entity.Item itemEntity = world.dropItem(dropLoc, output);
                    itemEntity.setVelocity(new org.bukkit.util.Vector(rightX * 0.15, 0.1, rightZ * 0.15));
                    itemEntity.setPickupDelay(10);
                }
            }
        }

        player.swingMainHand();
        world.spawnParticle(Particle.ITEM, dropLoc, 5, 0.15, 0.05, 0.15, 0.04, boardItem);

        SoundConfig cutSound = match.sound();

        if (cutSound == null) {
            for (var entry : settings.toolSounds().entrySet()) {
                if (CraftEngineUtil.isItem(tool, entry.getKey())) {
                    cutSound = entry.getValue();
                    break;
                }
            }
        }

        if (cutSound == null) {
            if (isShears(tool)) {
                cutSound = settings.toolSounds().get("shears");
                if (cutSound == null) {
                    cutSound = new SoundConfig("minecraft:entity.sheep.shear");
                }
            } else if (state.defaultToolMatcher().matches(tool, DefaultItemMatcherResolver.INSTANCE)) {
                cutSound = settings.toolSounds().get("knife");
                if (cutSound == null) {
                    cutSound = new SoundConfig("farmersdelight:block.cutting_board.knife_cut");
                }
            }
        }

        if (cutSound == null) {
            Material mat = boardItem.getType();
            if (mat.isBlock()) {
                Sound breakSound = mat.createBlockData().getSoundGroup().getBreakSound();
                cutSound = new SoundConfig(breakSound.getKey().getNamespace()
                        + ":" + breakSound.getKey().getKey(), settings.fallbackVolume(), settings.fallbackPitch());
            } else {
                cutSound = new SoundConfig("minecraft:block.wood.break",
                        settings.fallbackVolume(), settings.fallbackPitch());
            }
        }

        ConfigManager.playSound(dropLoc.getWorld(), dropLoc.getX(), dropLoc.getY(), dropLoc.getZ(), cutSound);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        if (!ConfigManager.getBoolean("cutting_board.dispenser_cutting", true)) return;

        Block dispenser = event.getBlock();
        if (dispenser.getType() != Material.DISPENSER) return;

        org.bukkit.block.data.type.Dispenser data =
                (org.bukkit.block.data.type.Dispenser) dispenser.getBlockData();
        Block target = dispenser.getRelative(data.getFacing());

        CuttingBoardBlockEntityController controller = getController(target);
        if (controller == null) return;

        event.setCancelled(true);

        ItemStack boardItem = controller.item();
        if (boardItem == null || boardItem.isEmpty()) return;

        CuttingRuntimeState state = runtimeState;
        ItemStack tool = event.getItem();
        CuttingRecipe match = findRecipe(boardItem, tool, state);
        if (match == null) return;

        Block dispenserBlock = dispenser;
        SCHEDULER.getRegionScheduler().runTask(plugin, target.getLocation(), () ->
                executeDispenserCut(target, controller, dispenserBlock, tool, match, boardItem, state));
    }

    private void executeDispenserCut(Block block, CuttingBoardBlockEntityController controller,
                                     Block dispenserBlock, ItemStack toolSnapshot,
                                     CuttingRecipe match, ItemStack boardItem,
                                     CuttingRuntimeState state) {
        RuntimeSettings settings = state.settings();

        int remaining = boardItem.getAmount() - 1;
        if (remaining <= 0) {
            controller.item(null);
            removeDisplayEntity(block.getLocation());
            triggerComparatorUpdate(block);
        } else {
            boardItem.setAmount(remaining);
            controller.markUnsaved();
            triggerComparatorUpdate(block);
            updateDisplayInPlace(block, boardItem);
        }

        if (dispenserBlock.getState() instanceof org.bukkit.block.Dispenser dispenserState) {
            org.bukkit.inventory.Inventory inv = dispenserState.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack slot = inv.getItem(i);
                if (slot != null && slot.isSimilar(toolSnapshot)) {
                    damageTool(slot);
                    if (slot.isEmpty() || slot.getAmount() <= 0) {
                        inv.setItem(i, null);
                    } else {
                        inv.setItem(i, slot);
                    }
                    break;
                }
            }
        }

        String blockFacing = getFacing(block);
        float rightX = 0f, rightZ = 0f;
        switch (blockFacing) {
            case "east" ->  { rightZ = -1f; }
            case "south" -> { rightX = 1f; }
            case "west" ->  { rightZ = 1f; }
            default ->       { rightX = -1f; }
        }
        Location dropLoc = block.getLocation().clone().add(0.5, 0.2, 0.5);
        World world = block.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int fortuneLevel = toolSnapshot.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
        double fortuneBonus = fortuneLevel * ConfigManager.getDouble("cutting_board.fortune_bonus", 0.1);

        for (ItemResult result : match.results()) {
            if (result.chance() < 1.0) {
                double effectiveChance = Math.min(1.0, result.chance() + fortuneBonus);
                int outputAmount = result.count();
                for (int roll = 0; roll < result.count(); roll++) {
                    if (rng.nextDouble() >= effectiveChance) outputAmount--;
                }
                if (outputAmount <= 0) continue;
                ItemStack output = CraftEngineUtil.createItem(result.item(), outputAmount);
                if (output != null) {
                    org.bukkit.entity.Item itemEntity = world.dropItem(dropLoc, output);
                    itemEntity.setVelocity(new org.bukkit.util.Vector(rightX * 0.15, 0.1, rightZ * 0.15));
                    itemEntity.setPickupDelay(10);
                }
            } else {
                ItemStack output = CraftEngineUtil.createItem(result.item(), result.count());
                if (output != null) {
                    org.bukkit.entity.Item itemEntity = world.dropItem(dropLoc, output);
                    itemEntity.setVelocity(new org.bukkit.util.Vector(rightX * 0.15, 0.1, rightZ * 0.15));
                    itemEntity.setPickupDelay(10);
                }
            }
        }

        world.spawnParticle(Particle.ITEM, dropLoc, 5, 0.15, 0.05, 0.15, 0.04, boardItem);
        SoundConfig cutSound = match.sound();
        if (cutSound == null) {
            for (var entry : settings.toolSounds().entrySet()) {
                if (CraftEngineUtil.isItem(toolSnapshot, entry.getKey())) {
                    cutSound = entry.getValue();
                    break;
                }
            }
        }
        if (cutSound == null) {
            cutSound = new SoundConfig("farmersdelight:block.cutting_board.knife_cut");
        }
        ConfigManager.playSound(world, dropLoc.getX(), dropLoc.getY(), dropLoc.getZ(), cutSound);
    }

    private boolean isBlockDisplayItem(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        List<String> itemOverrides = ConfigManager.getStringList("cutting_board.item_display_overrides");
        if (!itemOverrides.isEmpty() && CraftEngineUtil.matchesAnyItem(item, itemOverrides)) return false;
        if (item.getType().isBlock()) return true;
        List<String> overrides = ConfigManager.getStringList("cutting_board.block_display_overrides");
        return CraftEngineUtil.matchesAnyItem(item, overrides);
    }

    private void spawnDisplayEntity(Block block, ItemStack item) {
        Location blockLoc = block.getLocation();

        removeDisplayEntity(blockLoc);

        String prefix = isBlockDisplayItem(item)
                ? "cutting_board.display_block"
                : "cutting_board.display";

        double translateX  = ConfigManager.getDouble(prefix + ".translate_x", 0.0);
        double translateY  = ConfigManager.getDouble(prefix + ".translate_y", 0.23);
        double translateZ  = ConfigManager.getDouble(prefix + ".translate_z", 0.0);
        float  scale       = (float) ConfigManager.getDouble(prefix + ".scale", 0.7);
        double stackY      = ConfigManager.getDouble(prefix + ".stack_y_offset", 0.03);
        double stackXz     = ConfigManager.getDouble(prefix + ".stack_xz_offset", 0.06);
        float  rotationOff = (float) ConfigManager.getDouble(prefix + ".rotation_y", 0.0);
        float  pitch       = (float) ConfigManager.getDouble(prefix + ".rotation_pitch", 0.0);
        float  roll        = (float) ConfigManager.getDouble(prefix + ".rotation_roll", 0.0);

        String facing = getFacing(block);
        float yaw = getDisplayYaw(facing) + rotationOff;

        int modelCount = getModelCount(item);

        Location center = blockLoc.clone().add(0.5 + translateX, translateY, 0.5 + translateZ);
        World world = center.getWorld();

        long seed = (long) item.getType().ordinal() << 32 | (blockLoc.hashCode() & 0xFFFFFFFFL);
        Random rng = new Random(seed);

        List<ItemDisplay> spawned = new ArrayList<>(modelCount);

        try {
            for (int i = 0; i < modelCount; i++) {

            final double xOff, yOff, zOff;
            if (modelCount > 1) {
                xOff = (rng.nextFloat() * 2.0 - 1.0) * stackXz;
                yOff = stackY * i;
                zOff = (rng.nextFloat() * 2.0 - 1.0) * stackXz;
            } else {
                xOff = 0; yOff = 0; zOff = 0;
            }

            final float yawRad = (float) Math.toRadians(yaw);
            final float pitchRad = (float) Math.toRadians(pitch);
            final float rollRad = (float) Math.toRadians(roll);

            Location spawnLoc = center.clone().add(xOff, yOff, zOff);
            ItemDisplay display = world.spawn(spawnLoc, ItemDisplay.class, d -> {
                Transformation transformation = new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf().rotateY(yawRad).rotateX(pitchRad).rotateZ(rollRad),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                );
                d.setTransformation(transformation);

                ItemStack visual = item.clone();
                visual.setAmount(1);
                d.setItemStack(visual);
                d.setPersistent(false);
                d.setVisibleByDefault(true);
            });
            spawned.add(display);
            }

            displayEntities.put(blockLoc.clone(), spawned);
        } catch (Exception e) {
            for (ItemDisplay d : spawned) {
                if (d.isValid()) d.remove();
            }
            plugin.getLogger().severe(ConfigManager.getOr(
                    "cutting_display_spawn_fail", "物品展示实体生成失败 @ %location%: %error%")
                    .replace("%location%", String.valueOf(blockLoc))
                    .replace("%error%", ConfigManager.describeError(e)));
            return;
        }
    }

    private void removeDisplayEntity(Location loc) {
        List<ItemDisplay> existing = displayEntities.remove(loc);
        if (existing != null) {
            for (ItemDisplay d : existing) {
                if (d.isValid()) d.remove();
            }
        }

        World world = loc.getWorld();
        if (world != null && world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            Location center = loc.clone().add(0.5, 0.5, 0.5);
            try {
                for (Entity e : world.getNearbyEntities(center, 0.75, 0.75, 0.75)) {
                    if (e instanceof ItemDisplay) {
                        e.remove();
                    }
                }
            } catch (Exception ex) {

            }
        }
    }

    private void updateDisplayInPlace(Block block, ItemStack item) {
        List<ItemDisplay> existing = displayEntities.get(block.getLocation());
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
            displayEntities.put(block.getLocation().clone(), new ArrayList<>(existing.subList(0, newCount)));
        }
    }

    private void spawnToolDisplayEntity(Block block, ItemStack tool) {
        RuntimeSettings settings = runtimeState.settings();
        Map<String, ToolPosition> toolPositions = settings.toolPositions();
        ToolPosition defaultToolPosition = settings.defaultToolPosition();
        Location blockLoc = block.getLocation();
        removeDisplayEntity(blockLoc);

        String toolId = null;
        for (String key : toolPositions.keySet()) {
            if (CraftEngineUtil.isItem(tool, key)) { toolId = key; break; }
        }
        if (toolId == null) toolId = tool.getType().getKey().asString();
        ToolPosition pos = toolPositions.getOrDefault(toolId, defaultToolPosition);

        double tx = pos.translateX() != null ? pos.translateX()
                : (defaultToolPosition.translateX() != null ? defaultToolPosition.translateX()
                : ConfigManager.getDouble("cutting_board.display.translate_x", 0.0));
        double ty = pos.translateY() != null ? pos.translateY()
                : (defaultToolPosition.translateY() != null ? defaultToolPosition.translateY()
                : ConfigManager.getDouble("cutting_board.display.translate_y", 0.23));
        double tz = pos.translateZ() != null ? pos.translateZ()
                : (defaultToolPosition.translateZ() != null ? defaultToolPosition.translateZ()
                : ConfigManager.getDouble("cutting_board.display.translate_z", 0.0));
        float sc = (float) (pos.scale() != null ? pos.scale()
                : (defaultToolPosition.scale() != null ? defaultToolPosition.scale()
                : ConfigManager.getDouble("cutting_board.display.scale", 0.7)));
        float ry = (float) (pos.rotationY() != null ? pos.rotationY()
                : (defaultToolPosition.rotationY() != null ? defaultToolPosition.rotationY()
                : ConfigManager.getDouble("cutting_board.display.rotation_y", 0.0)));
        float rp = (float) (pos.rotationPitch() != null ? pos.rotationPitch()
                : (defaultToolPosition.rotationPitch() != null ? defaultToolPosition.rotationPitch()
                : ConfigManager.getDouble("cutting_board.display.rotation_pitch", 0.0)));
        float rr = (float) (pos.rotationRoll() != null ? pos.rotationRoll()
                : (defaultToolPosition.rotationRoll() != null ? defaultToolPosition.rotationRoll()
                : ConfigManager.getDouble("cutting_board.display.rotation_roll", 0.0)));

        String facing = getFacing(block);
        float yaw = getDisplayYaw(facing) + ry;
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(rp);
        float rollRad = (float) Math.toRadians(rr);

        Location center = blockLoc.clone().add(0.5 + tx, ty, 0.5 + tz);
        World world = center.getWorld();

        ItemDisplay display = world.spawn(center, ItemDisplay.class, d -> {
            Transformation transformation = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf().rotateY(yawRad).rotateX(pitchRad).rotateZ(rollRad),
                    new Vector3f(sc, sc, sc),
                    new Quaternionf()
            );
            d.setTransformation(transformation);
            ItemStack visual = tool.clone();
            visual.setAmount(1);
            d.setItemStack(visual);
            d.setPersistent(false);
            d.setVisibleByDefault(true);
        });

        try {
            displayEntities.put(blockLoc.clone(), List.of(display));
        } catch (Exception e) {
            if (display.isValid()) display.remove();
            plugin.getLogger().severe(ConfigManager.getOr(
                    "cutting_tool_display_track_fail", "工具展示实体追踪失败 @ %location%: %error%")
                    .replace("%location%", String.valueOf(blockLoc))
                    .replace("%error%", ConfigManager.describeError(e)));
            return;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getFacing(Block block) {
        ImmutableBlockState state = CraftEngineUtil.getCustomBlockState(block);
        if (state == null) return "north";
        Property property = state.getProperty("facing");
        if (property == null) return "north";
        Comparable value = state.get(property);
        if (value == null) return "north";
        String s = value.toString();

        return s.toLowerCase(Locale.ROOT);
    }

    private static float getDisplayYaw(String facing) {
        return switch (facing) {
            case "east" -> 270f;
            case "south" -> 180f;
            case "west" -> 90f;
            default -> 0f;
        };
    }

    private static int getModelCount(ItemStack stack) {
        if (stack.getAmount() <= 1) return 1;
        return 1 + (int) Math.ceil(((float) stack.getAmount() / stack.getMaxStackSize()) * 4);
    }

    private CuttingRecipe findRecipe(ItemStack boardItem, ItemStack tool, CuttingRuntimeState state) {
        for (CuttingRecipe recipe : state.recipes()) {
            if (recipe.inputMatcher().matches(boardItem, DefaultItemMatcherResolver.INSTANCE)
                    && matchesTool(tool, recipe)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean matchesTool(ItemStack stack, CuttingRecipe recipe) {
        return recipe.toolsMatcher().matches(stack, DefaultItemMatcherResolver.INSTANCE);
    }

    private boolean isConfiguredTool(ItemStack stack, CuttingRuntimeState state) {
        if (isVoid(stack)) return false;
        RuntimeSettings settings = state.settings();

        if (state.defaultToolMatcher().matches(stack, DefaultItemMatcherResolver.INSTANCE)) return true;
        for (CuttingRecipe recipe : state.recipes()) {
            if (recipe.toolsMatcher().matches(stack, DefaultItemMatcherResolver.INSTANCE)) return true;
        }

        for (String key : settings.toolPositions().keySet()) {
            if (ItemMatcher.of(key).matches(stack, DefaultItemMatcherResolver.INSTANCE)) return true;
        }
        return false;
    }

    private boolean isShears(ItemStack stack) {
        if (isVoid(stack)) return false;
        return stack.getType() == Material.SHEARS
                || CraftEngineUtil.isItem(stack, "minecraft:shears");
    }

    private CuttingBoardBlockEntityController getController(Block block) {
        try {
            CEWorld ceWorld = CraftEngineUtil.getLoadedWorld(block.getWorld());
            if (ceWorld == null) return null;
            BlockPos cePos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(cePos);
            if (blockEntity == null) return null;

            return getController(blockEntity);
        } catch (Exception e) {
            plugin.getLogger().warning(ConfigManager.getOr("cb_access_fail", "无法访问砧板方块实体 at %location%: %error%").replace("%location%", String.valueOf(block.getLocation())).replace("%error%", ConfigManager.describeError(e)));
            return null;
        }
    }

    private CuttingBoardBlockEntityController getController(BlockEntity blockEntity) {
        if (blockEntity == null) return null;
        try {
            BlockEntityControllerRef ref = new BlockEntityControllerRef();
            blockEntity.controller.let(CuttingBoardBlockEntityController.class, ref::set);
            return ref.controller;
        } catch (Exception e) {
            return null;
        }
    }

    private static final class BlockEntityControllerRef {
        CuttingBoardBlockEntityController controller;
        void set(CuttingBoardBlockEntityController c) { this.controller = c; }
    }

    private static boolean isVoid(ItemStack stack) {
        return stack == null || stack.isEmpty() || stack.getType() == Material.AIR;
    }

    private static void damageTool(ItemStack tool) {
        if (tool == null || tool.isEmpty()) return;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        int maxDamage;
        if (damageable.hasMaxDamage()) {
            maxDamage = damageable.getMaxDamage();
        } else {
            maxDamage = tool.getType().getMaxDurability();
        }
        if (maxDamage <= 0) return;

        int nextDamage = damageable.getDamage() + 1;
        if (nextDamage >= maxDamage) {
            tool.setAmount(tool.getAmount() - 1);
            return;
        }
        damageable.setDamage(nextDamage);
        tool.setItemMeta(meta);
    }

    private void playSound(Block block, SoundConfig cfg) {
        if (cfg == null) return;
        ConfigManager.playSound(block.getWorld(),
                block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5,
                cfg);
    }

    private String getMsg(String key, String fallback) {
        return ConfigManager.getOr(key, fallback);
    }

    private void copyAllJarRecipes(File recipeDir, String resourceDir) {
        String prefix = resourceDir + "/";
        try {
            java.net.URL url = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (url == null) return;
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new java.io.File(url.toURI()))) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(prefix) && name.endsWith(".yml")) {
                        String fileName = name.substring(prefix.length());
                        if (fileName.isEmpty() || fileName.contains("/")) continue;
                        java.io.File target = new java.io.File(recipeDir, fileName);
                        if (!target.exists()) {
                            try (java.io.InputStream in = plugin.getResource(name)) {
                                if (in != null) java.nio.file.Files.copy(in, target.toPath());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
    }

    private void hopperTick(long generation) {
        if (!isHopperGenerationActive(generation)) return;
        hopperTickRuns++;
        if (trackedBoards.isEmpty()) return;
        Map<Long, List<Location>> byChunk = new HashMap<>();
        for (Location loc : trackedBoards) {
            if (!isHopperGenerationActive(generation)) return;
            byChunk.computeIfAbsent(chunkKey(loc), k -> new ArrayList<>(2)).add(loc);
        }
        for (List<Location> group : byChunk.values()) {
            SCHEDULER.getRegionScheduler().runTask(plugin, group.get(0), () -> {
                if (!isHopperGenerationActive(generation)) return;
                for (Location loc : group) processHopperAt(loc);
            });
        }
    }

    private static long chunkKey(Location loc) {
        int worldMix = loc.getWorld() != null
                ? (int) (loc.getWorld().getUID().getLeastSignificantBits() >>> 48) : 0;
        return ((long) ((loc.getBlockX() >> 4) ^ worldMix)) << 32 | ((long) (loc.getBlockZ() >> 4)) & 0xFFFFFFFFL;
    }

    private boolean isHopperGenerationActive(long generation) {
        return generation == hopperGeneration
                && hopperTask != null
                && runtimeState.settings().hopperInteraction()
                && instance == this;
    }

    private void processHopperAt(Location loc) {
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        Block block = loc.getBlock();
        CuttingBoardBlockEntityController controller = getController(block);
        if (controller == null) {
            trackedBoards.remove(loc);
            return;
        }

        hopperPushInto(block, controller);

        hopperPullFrom(block, controller);
    }

    private void hopperPushInto(Block board, CuttingBoardBlockEntityController controller) {

        if (controller.hasTool()) return;

        Block above = board.getRelative(BlockFace.UP);
        if (tryPushFrom(above, BlockFace.DOWN, controller, board)) return;

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block side = board.getRelative(face);
            if (side.getType() != Material.HOPPER) continue;
            if (!(side.getBlockData() instanceof org.bukkit.block.data.type.Dispenser)) {
                if (side.getBlockData() instanceof org.bukkit.block.data.Directional dir) {
                    if (!side.getRelative(dir.getFacing()).equals(board)) continue;
                    if (tryPushFrom(side, face, controller, board)) return;
                }
            }
        }
    }

    private boolean tryPushFrom(Block hopperBlock, BlockFace face, CuttingBoardBlockEntityController controller, Block board) {
        if (hopperBlock.getType() != Material.HOPPER) return false;
        if (!(hopperBlock.getState() instanceof org.bukkit.block.Container container)) return false;

        if (face != BlockFace.DOWN) {
            if (!(hopperBlock.getBlockData() instanceof org.bukkit.block.data.Directional dir)) return false;
            if (!hopperBlock.getRelative(dir.getFacing()).equals(board)) return false;
        }

        if (hopperBlock.getBlockData() instanceof org.bukkit.block.data.type.Hopper hopper) {
            if (!hopper.isEnabled()) return false;
        }

        org.bukkit.inventory.Inventory inv = container.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.isEmpty()) continue;

            if (controller.hasItem()) {
                ItemStack boardItem = controller.item();
                if (!boardItem.isSimilar(slot)) continue;
                int limit = Math.min(64, boardItem.getMaxStackSize());
                if (boardItem.getAmount() >= limit) continue;
                boardItem.setAmount(boardItem.getAmount() + 1);
                controller.markUnsaved();
            } else {
                ItemStack placed = slot.clone();
                placed.setAmount(1);
                controller.item(placed);
            }
            slot.setAmount(slot.getAmount() - 1);
            if (slot.getAmount() <= 0) inv.setItem(i, null);
            else inv.setItem(i, slot);

            updateDisplayInPlace(board, controller.item());
            triggerComparatorUpdate(board);
            return true;
        }
        return false;
    }

    private void hopperPullFrom(Block board, CuttingBoardBlockEntityController controller) {
        if (!controller.hasItem()) return;

        Block below = board.getRelative(BlockFace.DOWN);
        tryPullTo(below, board, controller);
    }

    private boolean tryPullTo(Block hopperBlock, Block board, CuttingBoardBlockEntityController controller) {
        if (hopperBlock.getType() != Material.HOPPER) return false;
        if (!(hopperBlock.getState() instanceof org.bukkit.block.Container container)) return false;

        if (hopperBlock.getBlockData() instanceof org.bukkit.block.data.type.Hopper hopper) {
            if (!hopper.isEnabled()) return false;
        }

        ItemStack boardItem = controller.item();
        ItemStack one = boardItem.clone();
        one.setAmount(1);
        Map<Integer, ItemStack> leftovers = container.getInventory().addItem(one);
        if (leftovers.isEmpty()) {
            int remaining = boardItem.getAmount() - 1;
            if (remaining <= 0) {
                controller.item(null);
                removeDisplayEntity(board.getLocation());
            } else {
                boardItem.setAmount(remaining);
                controller.markUnsaved();
                updateDisplayInPlace(board, boardItem);
            }
            triggerComparatorUpdate(board);
            return true;
        }
        return false;
    }

    public void refreshDisplayEntities() {
        removeTrackedDisplayEntities();
    }

    public synchronized void shutdown() {
        hopperGeneration++;
        cn.chengzhimeow.ccscheduler.task.CCTask previousTask = hopperTask;
        hopperTask = null;
        runtimeState = CuttingRuntimeState.empty();
        if (instance == this) instance = null;
        if (previousTask != null) cancelHopperTaskBestEffort(previousTask);
        removeTrackedDisplayEntities();
        trackedBoards.clear();
        recentlyPlaced.clear();
    }

    private void removeTrackedDisplayEntities() {
        for (List<ItemDisplay> list : new ArrayList<>(displayEntities.values())) {
            for (ItemDisplay display : list) {
                if (!display.isValid()) continue;
                if (!plugin.isEnabled()) {
                    try { display.remove(); } catch (Throwable ignored) { }
                    continue;
                }
                SCHEDULER.getEntityScheduler().runTask(plugin, display, display::remove);
            }
        }
        displayEntities.clear();
    }

    private static void triggerComparatorUpdate(Block block) {
        block.getState().update(true, true);
    }

    private void scanChunkForCuttingBoards(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        CEChunk chunk = CraftEngineUtil.getLoadedChunk(world, chunkX, chunkZ);
        if (chunk == null) return;

        for (BlockEntity entity : chunk.blockEntities()) {
            CuttingBoardBlockEntityController controller = getController(entity);
            if (controller == null) continue;
            BlockPos pos = entity.pos();
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            trackedBoards.add(block.getLocation());
            if (controller.hasItem()) {
                spawnDisplayEntity(block, controller.item());
            } else if (controller.hasTool()) {
                if (controller.isToolInserted()) {
                    spawnToolDisplayEntity(block, controller.tool());
                } else {
                    spawnDisplayEntity(block, controller.tool());
                }
            }
        }
    }

    public void loadDisplayEntitiesForAllLoadedChunks() {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                SCHEDULER.getRegionScheduler().runTask(plugin, world, chunkX, chunkZ, () -> scanChunkForCuttingBoards(world, chunkX, chunkZ));
            }
        }
    }

    private ToolPositions readToolPositions() {
        Map<String, ToolPosition> loadedToolPositions = new LinkedHashMap<>();
        ToolPosition loadedDefaultToolPosition = runtimeState.settings().defaultToolPosition();
        if (plugin == null) return new ToolPositions(Map.of(), loadedDefaultToolPosition);
        File toolsFile = new File(plugin.getDataFolder(), "insertable_tools.yml");
        if (!toolsFile.exists()) {
            try (InputStream in = plugin.getResource("insertable_tools.yml")) {
                if (in != null) Files.copy(in, toolsFile.toPath());
            } catch (Exception e) {
                plugin.getLogger().warning(ConfigManager.getOr("cb_copy_fail", "无法复制默认 insertable_tools.yml"));
            }
        }
        if (!toolsFile.exists()) return new ToolPositions(Map.of(), loadedDefaultToolPosition);

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(toolsFile);
        var defSec = yml.getConfigurationSection("default");
        if (defSec != null) loadedDefaultToolPosition = parseToolPosition(defSec);
        for (String key : yml.getKeys(false)) {
            if ("default".equals(key)) continue;
            var sec = yml.getConfigurationSection(key);
            if (sec != null) loadedToolPositions.put(normalizeToolExpression(key), parseToolPosition(sec));
        }
        return new ToolPositions(Collections.unmodifiableMap(new LinkedHashMap<>(loadedToolPositions)),
                loadedDefaultToolPosition);
    }

    private record ToolPositions(Map<String, ToolPosition> positions, ToolPosition defaultPosition) {
    }

    private ToolPosition parseToolPosition(org.bukkit.configuration.ConfigurationSection sec) {
        return new ToolPosition(
                dblIfSet(sec, "translate_x"),
                dblIfSet(sec, "translate_y"),
                dblIfSet(sec, "translate_z"),
                dblIfSet(sec, "scale"),
                dblIfSet(sec, "rotation_y"),
                dblIfSet(sec, "rotation_pitch"),
                dblIfSet(sec, "rotation_roll")
        );
    }

    private static Double dblIfSet(org.bukkit.configuration.ConfigurationSection sec, String key) {
        if (!sec.contains(key, true)) return null;
        return Double.valueOf(sec.getDouble(key));
    }

    public void removeDisplayEntityAt(Location loc) {
        removeDisplayEntity(loc);
    }
}
