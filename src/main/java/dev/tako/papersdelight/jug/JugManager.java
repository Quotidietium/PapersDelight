package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.common.ExplosionSettleFlow;
import dev.tako.papersdelight.common.TickBatch;
import dev.tako.papersdelight.common.ExplosionStaging;
import dev.tako.papersdelight.common.MenuCloseFlow;

import dev.tako.libuid.api.FluidAction;
import dev.tako.libuid.api.FluidRegistry;
import dev.tako.libuid.api.FluidIngredient;
import dev.tako.libuid.api.FluidStack;
import dev.tako.libuid.api.FluidTank;
import dev.tako.libuid.api.ResourceKey;
import dev.tako.libuid.api.SizedFluidIngredient;
import dev.tako.libuid.api.item.FluidActionResult;
import dev.tako.libuid.api.item.FluidContainerRegistry;
import dev.tako.libuid.api.item.FluidHandlerItem;
import dev.tako.libuid.api.item.FluidUtil;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.gui.MenuManager;
import dev.tako.papersdelight.api.protection.ProtectionGate;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.util.TextUtil;
import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.gui.module.jug.JugMenu;
import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import dev.tako.papersdelight.recipe.IngredientDef;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.util.ItemMetaUtil;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.util.ExplosionUtils;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JugManager implements Listener, JugGate.Bridge {
    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    final JavaPlugin plugin;
    private final JavaPlugin javaPlugin;
    private final RecipeManager recipeManager;

    private final java.util.Map<java.util.UUID, Location> openMenus = new ConcurrentHashMap<>();

    private final JugMenuSessionRegistry<Location> menuSessions = new JugMenuSessionRegistry<>();

    private final java.util.Map<java.util.UUID, DisplaySnapshot> displaySnapshots = new ConcurrentHashMap<>();

    private final Map<Location, JugBlockEntityController> controllerCache = new ConcurrentHashMap<>();

    private final ExplosionStaging<Event, PendingExplosionJug> pendingExplosions = new ExplosionStaging<>();

    private final ExplosionSettleFlow<Location, PendingGuiExplosion> pendingGuiExplosions = new ExplosionSettleFlow<>();
    private final java.util.Queue<Runnable> pendingDeliveryCompensations = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final CCTask compensationTask;

    private static final String CAPACITY_BUCKET_FALLBACK_ID = "ce:farmersdelight:jug_capacity_bucket";
    private static final String CAPACITY_BOTTLE_FALLBACK_ID = "ce:farmersdelight:jug_capacity_bottle";

    static volatile JugManager instance;

    public JugManager(JavaPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.javaPlugin = (JavaPlugin) plugin;
        this.recipeManager = recipeManager;
        this.compensationTask = SCHEDULER.getGlobalRegionScheduler().runTaskTimer(javaPlugin, 20L, 20L, this::drainDeliveryCompensations);
        instance = this;
    }

    private void drainDeliveryCompensations() {
        Runnable pending;
        while ((pending = pendingDeliveryCompensations.poll()) != null) {
            pending.run();
        }
    }

    public void shutdown() {
        compensationTask.cancel();
        pendingDeliveryCompensations.clear();
        openMenus.forEach(this::persistSessionBeforeRelease);
        openMenus.clear();
        menuSessions.clear();
        displaySnapshots.clear();
        controllerCache.clear();
        pendingGuiExplosions.clear();
    }

    public void reload() {
        controllerCache.clear();
    }

    @Override
    public InteractionResult interact(Player player, Block block) {
        if (!ProtectionGate.canInteract(player, block.getLocation())) return InteractionResult.FAIL;
        JugBlockEntityController controller = getController(block);
        if (controller == null) return InteractionResult.PASS;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isEmpty(held) && (emptyHeld(player, block.getLocation(), controller, held)
                || fillHeld(player, block.getLocation(), controller, held)
                || emptyHeldGeneric(player, block.getLocation(), controller, held)
                || fillHeldGeneric(player, block.getLocation(), controller, held))) {
            player.swingMainHand();
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        if (openMenu(player, block, controller)) player.swingMainHand();
        return InteractionResult.SUCCESS_AND_CANCEL;
    }

    public BlockEntityController createController(Object blockEntity) {
        return blockEntity instanceof BlockEntity entity ? new JugBlockEntityController(entity) : null;
    }

    @Override
    public int analogSignal(Block block) {
        JugBlockEntityController controller = getController(block);
        return controller == null ? 0 : JugFluidLevel.comparatorSignal(controller.fluidAmount(), controller.tank().capacity());
    }

    @Override
    public void onPlaced(Block block) {
        controllerCache.remove(blockKey(block));
    }

    JugBlockEntityController getController(Block block) {
        if (block == null) return null;
        Location key = blockKey(block);
        JugBlockEntityController cached = controllerCache.get(key);
        if (cached != null) {
            try {
                if (cached.blockEntity().isValid()) return cached;
            } catch (Throwable ignored) {

            }
            controllerCache.remove(key, cached);
        }
        try {
            CEWorld world = CraftEngineUtil.getLoadedWorld(block.getWorld());
            if (world == null) { controllerCache.remove(key); return null; }
            JugBlockEntityController controller = getController(world.getBlockEntityAtIfLoaded(
                    new BlockPos(block.getX(), block.getY(), block.getZ())));
            if (controller == null || !controller.blockEntity().isValid()) {
                controllerCache.remove(key);
                return null;
            }
            controllerCache.put(key, controller);
            return controller;
        } catch (Throwable ignored) {
            controllerCache.remove(key);
            return null;
        }
    }

    private static JugBlockEntityController getController(BlockEntity entity) {
        if (entity == null || !entity.isValid()) return null;
        try {
            ControllerRef ref = new ControllerRef();
            entity.controller.let(JugBlockEntityController.class, ref::set);
            return ref.value;
        } catch (Throwable ignored) {
            return null;
        }
    }

    void onControllerUnloaded(JugBlockEntityController controller) {
        Location location = locationOf(controller);
        if (location == null) return;
        controllerCache.remove(location);
    }

    private static Location locationOf(JugBlockEntityController controller) {
        CEWorld ceWorld = controller.blockEntity().world();
        if (ceWorld == null) return null;
        World world = Bukkit.getWorld(ceWorld.name());
        if (world == null) return null;
        BlockPos pos = controller.blockEntity().pos();
        return new Location(world, pos.x(), pos.y(), pos.z()).toBlockLocation();
    }

    void tickJug(JugBlockEntityController controller, CEWorld ceWorld, BlockPos cePos) {
        if (!plugin.isEnabled()) return;
        World world = Bukkit.getWorld(ceWorld.name());
        if (world == null) return;
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());
        Location location = block.getLocation().toBlockLocation();
        int now = Bukkit.getCurrentTick();
        int elapsed = TickBatch.due(controller.lastPassTick, now, TickBatch.interval());
        if (elapsed == 0) return;
        controller.lastPassTick = now;

        Player owner = sessionOwner(location);
        Inventory open = owner == null ? null : openJugInventory(owner);
        if (open != null) syncInputSlot(location, controller, open);
        for (int i = 0; i < elapsed; i++) {
            if ((++controller.hopperTicks % 8) == 0) processHoppers(location, block, controller);
            boolean dormant = owner == null && !controller.hasInput() && !controller.hasOutput();
            if (dormant) continue;
            if (!emptyInput(location, controller)
                    && !fillInput(location, controller)
                    && !emptyInputGeneric(location, controller)
                    && !fillInputGeneric(location, controller)) processSoaking(location, controller);
        }
        if (open != null) populateMenu(owner, open, controller, false);
    }

    private void syncInputSlot(Location location, JugBlockEntityController controller, Inventory inventory) {
        if (menuSessions.consumeSkipNextInputRead(location)) {
            refreshInputSlot(location, inventory, controller);
            return;
        }
        ItemStack shown = copyMenuStack(inventory.getItem(JugLayout.INPUT));
        if (!itemsEqual(shown, controller.input())) controller.input(shown);
    }

    private void refreshInputSlot(Location location, Inventory inventory, JugBlockEntityController controller) {
        if (!itemsEqual(inventory.getItem(JugLayout.INPUT), controller.input())) {
            inventory.setItem(JugLayout.INPUT, copyMenuStack(controller.input()));
        }
        menuSessions.clearSkipNextInputRead(location);
    }

    private static void clearMenuInputSlot(Inventory inventory) {
        inventory.setItem(JugLayout.INPUT, null);
    }

    private Player sessionOwner(Location location) {
        java.util.UUID ownerId = ownerAt(location);
        if (ownerId == null) return null;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline()) return owner;
        persistSessionBeforeRelease(ownerId, location);
        closeSession(location, ownerId);
        completePendingGuiExplosion(location);
        return null;
    }

    private static boolean itemsEqual(ItemStack left, ItemStack right) {
        if (isEmpty(left) && isEmpty(right)) return true;
        if (isEmpty(left) || isEmpty(right)) return false;
        return left.equals(right);
    }

    private boolean openMenu(Player player, Block block, JugBlockEntityController controller) {
        Location key = blockKey(block);
        java.util.UUID playerId = player.getUniqueId();
        Location previous = openMenus.get(playerId);
        if (previous != null) {

            Inventory oldInventory = openJugInventory(player);
            long previousGeneration = menuSessions.generation(previous, playerId);
            if (oldInventory != null) closeMenu(player, previous, oldInventory, previousGeneration);
            else {
                closeSession(previous, playerId);
                SCHEDULER.getRegionScheduler().runTask(javaPlugin, previous, () -> completePendingGuiExplosion(previous));
            }
            if (openMenus.containsKey(playerId)) return false;
        }
        if (menuSessions.tryOpen(key, playerId) != null) {

            player.sendMessage(TextUtil.parse(player, ConfigManager.getOr("jug_menu_busy",
                    "<red>Someone else is using this Jug.")));
            return false;
        }
        long generation = menuSessions.generation(key, playerId);
        Inventory before = player.getOpenInventory().getTopInventory();

        MenuManager.getInstance().openMenu(player, "jug", inventory -> closeMenu(player, key, inventory, generation));
        Inventory opened = player.getOpenInventory().getTopInventory();
        if (opened == null || opened == before || opened.getSize() != JugLayout.SIZE) {
            menuSessions.close(key, playerId);
            openMenus.remove(playerId, key);
            SCHEDULER.getRegionScheduler().runTask(javaPlugin, key, () -> completePendingGuiExplosion(key));
            player.sendMessage(TextUtil.parse(player, ConfigManager.getOr("jug_menu_open_failed",
                    "<red>Unable to open Jug menu.")));
            return false;
        }
        menuSessions.setInventory(key, playerId, opened);
        openMenus.put(playerId, key);
        displaySnapshots.remove(playerId);
        populateMenu(player, opened, controller, true);
        return true;
    }

    private void closeMenu(Player player, Location location, Inventory inventory, long generation) {
        java.util.UUID playerId = player.getUniqueId();
        if (!menuSessions.isOwner(location, playerId)) return;

        if (!menuSessions.isCurrentGeneration(location, playerId, generation)) return;
        if (!menuSessions.hasInventory(location, playerId, inventory)) {

            plugin.getLogger().warning("Jug close callback carried a foreign inventory at " + location + "; closing session.");
            SCHEDULER.getRegionScheduler().runTask(javaPlugin, location, () -> {
                closeSession(location, playerId);
                completePendingGuiExplosion(location);
            });
            return;
        }
        MenuCloseFlow.persistInput(
                closeFlowScheduler(location, player),
                () -> menuSessions.isCurrentGeneration(location, playerId, generation),
                () -> copyMenuStack(inventory.getItem(JugLayout.INPUT)),
                input -> persistClosedMenuInput(location, input),
                () -> {
                    closeSession(location, playerId);
                    completePendingGuiExplosion(location);
                });
    }

    private void persistClosedMenuInput(Location location, ItemStack input) {
        JugBlockEntityController controller = getController(location.getBlock());
        if (controller != null && !menuSessions.consumeSkipNextInputRead(location)
                && !itemsEqual(input, controller.input())) {
            controller.input(copyMenuStack(input));
        }
    }

    private MenuCloseFlow.Scheduler closeFlowScheduler(Location location, Player owner) {
        return new MenuCloseFlow.Scheduler() {
            @Override
            public void runEntity(Runnable task) {
                if (owner == null || !owner.isValid()) return;
                if (!plugin.isEnabled()) {
                    try { task.run(); } catch (Throwable ignored) { }
                    return;
                }
                SCHEDULER.getEntityScheduler().runTask(javaPlugin, owner, task);
            }

            @Override
            public void runRegion(Runnable task) {
                SCHEDULER.getRegionScheduler().runTask(javaPlugin, location, task);
            }
        };
    }

    private void closeSession(Location location, java.util.UUID playerId) {
        menuSessions.close(location, playerId);
        openMenus.remove(playerId, location);
        displaySnapshots.remove(playerId);
    }

    private java.util.UUID ownerAt(Location location) {
        return menuSessions.owner(location);
    }

    public void takeOutputToCursor(Player player, org.bukkit.event.inventory.InventoryClickEvent event) {
        event.setCancelled(true);
        Location location = openMenus.get(player.getUniqueId());
        if (location == null || !isEmpty(event.getCursor())) return;
        Inventory inventory = openJugInventory(player);
        if (inventory == null) return;
        JugBlockEntityController controller = getController(location.getBlock());
        if (controller == null || isEmpty(controller.output())) return;
        ItemStack output = controller.output().clone();
        controller.output(null);
        inventory.setItem(JugLayout.OUTPUT, null);
        event.getView().setCursor(output);
    }

    private Inventory openJugInventory(Player player) {
        Location location = openMenus.get(player.getUniqueId());
        if (location == null) return null;
        Inventory inventory = player.getOpenInventory().getTopInventory();
        return menuSessions.hasInventory(location, player.getUniqueId(), inventory) ? inventory : null;
    }

    private void populateMenu(Player player, Inventory inventory, JugBlockEntityController controller, boolean force) {
        Location location = openMenus.get(player.getUniqueId());
        if (location == null || !menuSessions.hasInventory(location, player.getUniqueId(), inventory)) return;
        refreshInputSlot(location, inventory, controller);

        ItemStack authoritativeOutput = controller.output();
        if (!itemsEqual(inventory.getItem(JugLayout.OUTPUT), authoritativeOutput)) {
            inventory.setItem(JugLayout.OUTPUT, copyMenuStack(authoritativeOutput));
        }
        java.util.UUID playerId = player.getUniqueId();
        String fluidKey = controller.fluid().isEmpty() ? null : controller.fluid().fluidKey().toString();
        int amount = controller.fluidAmount();
        int capacity = controller.tank().capacity();
        int progressStage = JugFluidLevel.progressStage(controller.processingTime(), controller.processingTimeTotal());
        DisplaySnapshot previous = displaySnapshots.get(playerId);
        if (force || previous == null || previous.amount != amount || !java.util.Objects.equals(previous.fluidKey, fluidKey)) {
            inventory.setItem(JugLayout.FLUID, createFluidDisplay(controller.fluid(), capacity));
        }
        if (force || previous == null || previous.progressStage != progressStage) {
            inventory.setItem(JugLayout.PROGRESS, progressStage == 0 ? JugMenu.borderDecoration() : createProgressDisplay(progressStage, controller));
        }

        if (force || previous == null || previous.amount != amount || previous.capacity != capacity) {
            inventory.setItem(JugLayout.CAPACITY_BUCKETS, createCapacityDisplay(
                    "jug.buttons.capacity_bucket", CAPACITY_BUCKET_FALLBACK_ID,
                    JugCapacityDisplay.buckets(amount)));
            inventory.setItem(JugLayout.CAPACITY_BOTTLES, createCapacityDisplay(
                    "jug.buttons.capacity_bottle", CAPACITY_BOTTLE_FALLBACK_ID,
                    JugCapacityDisplay.bottles(amount)));
        }
        displaySnapshots.put(playerId, new DisplaySnapshot(fluidKey, amount, capacity, progressStage));
    }

    private ItemStack createCapacityDisplay(String configPath, String fallbackId, int units) {
        ItemStack display = ConfigManager.buildGuiItem(configPath, null, "<white> </white>", List.of());
        if (isEmpty(display) || display.getType() == Material.BARRIER) {
            ItemStack fallback = ConfigManager.parseIconString(fallbackId);
            if (!isEmpty(fallback)) display = fallback;
        }
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.translatable("container.farmersdelight.jug.ratio", Component.text(4))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            ItemMetaUtil.setLore(meta, List.of());
            display.setItemMeta(meta);
        }
        display.setAmount(JugCapacityDisplay.itemAmount(units));
        return display;
    }

    private ItemStack createFluidDisplay(FluidStack fluid, int capacity) {
        if (fluid.isEmpty()) {
            ItemStack empty = JugMenu.emptyDecoration();
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.displayName(JugFluidDisplay.emptyName());
                ItemMetaUtil.setLore(meta, List.of());
                empty.setItemMeta(meta);
            }
            return empty;
        }
        ItemStack display = null;
        var type = FluidRegistry.get(fluid.fluidKey());
        List<String> candidates = type.flatMap(t -> t.texture()
                        .filter(texture -> !texture.isBlank())
                        .map(JugFluidItemId::candidatesForTexture))
                .orElse(List.of());
        boolean candidateHit = false;
        for (String id : candidates) {
            ItemStack candidate = CraftEngineUtil.createItem(id, 1);
            if (!isEmpty(candidate)) { display = candidate; candidateHit = true; break; }
        }

        if (!candidateHit) {
            display = CraftEngineUtil.createItem("farmersdelight:jug_fluid_water", 1);
            if (isEmpty(display)) display = new ItemStack(Material.POTION);
        }
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;
        type.flatMap(t -> t.displayName()).ifPresent(name ->
                meta.displayName(JugFluidDisplay.preservingName(meta.displayName(), name, fluid.amount(), capacity)));
        Integer color = type.flatMap(t -> t.color()).orElse(null);

        ItemMetaUtil.applyColor(meta, color);
        ItemMetaUtil.setLore(meta, List.of());
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createProgressDisplay(int stage, JugBlockEntityController controller) {
        ItemStack display = CraftEngineUtil.createItem("farmersdelight:soaking_progress_" + stage, 1);
        if (isEmpty(display)) display = JugMenu.emptyDecoration();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            ItemMetaUtil.setLore(meta, List.of());
            display.setItemMeta(meta);
        }
        return display;
    }

    private static ItemStack copyMenuStack(ItemStack stack) {
        return isEmpty(stack) ? null : stack.clone();
    }

    private boolean emptyHeld(Player player, Location jugLocation, JugBlockEntityController controller, ItemStack held) {
        for (JugFluidEmptyingRecipe recipe : recipeManager.jugRecipes().emptying()) {
            if (!CraftEngineUtil.isItem(held, recipe.filledInput())) continue;
            FluidStack candidate = expressionStack(recipe.fluidExpression(), recipe.amount());
            if (candidate.isEmpty() || JugInvoker.fill(controller.tank(), candidate, FluidAction.SIMULATE) != recipe.amount()) continue;
            ItemStack result = CraftEngineUtil.createItem(recipe.emptyResult(), 1);
            refreshJugItem(result);
            if (isEmpty(result)) continue;
            FluidStack before = controller.fluid();
            byte[] beforeInvalid = controller.invalidLibuidFluid();
            if (JugInvoker.fill(controller.tank(), candidate, FluidAction.EXECUTE) != recipe.amount()) {
                controller.tank().setFluid(before);
                controller.invalidLibuidFluid(beforeInvalid);
                continue;
            }
            replaceHeldOneDelayed(player, jugLocation, held, result, () -> {
                controller.tank().setFluid(before);
                controller.invalidLibuidFluid(beforeInvalid);
            });
            playFluidTransferSound(jugLocation, Sound.ITEM_BUCKET_EMPTY);
            return true;
        }
        return false;
    }

    private boolean fillHeld(Player player, Location jugLocation, JugBlockEntityController controller, ItemStack held) {
        for (JugFluidFillingRecipe recipe : recipeManager.jugRecipes().filling()) {
            if (!CraftEngineUtil.isItem(held, recipe.emptyInput()) || !matches(recipe.fluidExpression(), recipe.amount(), controller.fluid())) continue;
            ItemStack result = CraftEngineUtil.createItem(recipe.filledResult(), 1);
            refreshJugItem(result);
            if (isEmpty(result)) continue;
            FluidStack before = controller.fluid();
            byte[] beforeInvalid = controller.invalidLibuidFluid();
            if (JugInvoker.drain(controller.tank(), recipe.amount(), FluidAction.EXECUTE).amount() != recipe.amount()) {
                controller.tank().setFluid(before);
                controller.invalidLibuidFluid(beforeInvalid);
                continue;
            }
            replaceHeldOneDelayed(player, jugLocation, held, result, () -> {
                controller.tank().setFluid(before);
                controller.invalidLibuidFluid(beforeInvalid);
            });
            playFluidTransferSound(jugLocation, Sound.ITEM_BUCKET_FILL);
            return true;
        }
        return false;
    }

    private boolean emptyHeldGeneric(Player player, Location jugLocation, JugBlockEntityController controller, ItemStack held) {
        FluidActionResult preview = FluidUtil.tryEmptyContainer(one(held), controller.tank(), emptyingMaxAmount(controller), FluidAction.SIMULATE);
        if (!preview.success()) return false;
        FluidStack before = controller.fluid();
        byte[] beforeInvalid = controller.invalidLibuidFluid();
        FluidActionResult result = FluidUtil.tryEmptyContainer(one(held), controller.tank(), emptyingMaxAmount(controller), FluidAction.EXECUTE);
        if (!result.success() || isEmpty(result.result())) {
            controller.tank().setFluid(before);
            controller.invalidLibuidFluid(beforeInvalid);
            return false;
        }
        refreshJugItem(result.result());
        replaceHeldOneDelayed(player, jugLocation, held, result.result(), () -> {
            controller.tank().setFluid(before);
            controller.invalidLibuidFluid(beforeInvalid);
        });
        playFluidTransferSound(jugLocation, Sound.ITEM_BUCKET_EMPTY);
        return true;
    }

    private boolean fillHeldGeneric(Player player, Location jugLocation, JugBlockEntityController controller, ItemStack held) {
        FluidActionResult preview = FluidUtil.tryFillContainer(one(held), controller.tank(), controller.fluidAmount(), FluidAction.SIMULATE);
        if (!preview.success()) return false;
        FluidStack before = controller.fluid();
        byte[] beforeInvalid = controller.invalidLibuidFluid();
        FluidActionResult result = FluidUtil.tryFillContainer(one(held), controller.tank(), controller.fluidAmount(), FluidAction.EXECUTE);
        if (!result.success() || isEmpty(result.result())) {
            controller.tank().setFluid(before);
            controller.invalidLibuidFluid(beforeInvalid);
            return false;
        }
        refreshJugItem(result.result());
        replaceHeldOneDelayed(player, jugLocation, held, result.result(), () -> {
            controller.tank().setFluid(before);
            controller.invalidLibuidFluid(beforeInvalid);
        });
        playFluidTransferSound(jugLocation, Sound.ITEM_BUCKET_FILL);
        return true;
    }

    private boolean emptyInput(Location location, JugBlockEntityController controller) {
        ItemStack input = controller.input();
        if (isEmpty(input)) return false;
        for (JugFluidEmptyingRecipe recipe : recipeManager.jugRecipes().emptying()) {
            if (!CraftEngineUtil.isItem(input, recipe.filledInput())) continue;
            FluidStack candidate = expressionStack(recipe.fluidExpression(), recipe.amount());
            if (candidate.isEmpty() || JugInvoker.fill(controller.tank(), candidate, FluidAction.SIMULATE) != recipe.amount()) continue;
            ItemStack result = CraftEngineUtil.createItem(recipe.emptyResult(), 1);
            refreshJugItem(result);
            if (!canInsert(controller.output(), result)) continue;
            FluidStack before = controller.fluid();
            byte[] beforeInvalid = controller.invalidLibuidFluid();
            if (JugInvoker.fill(controller.tank(), candidate, FluidAction.EXECUTE) != recipe.amount()) {
                controller.tank().setFluid(before);
                controller.invalidLibuidFluid(beforeInvalid);
                continue;
            }
            controller.output(insert(controller.output(), result));
            consumeInputOne(location, controller, input);
            playFluidTransferSound(location, Sound.ITEM_BUCKET_EMPTY);
            return true;
        }
        return false;
    }

    private boolean fillInput(Location location, JugBlockEntityController controller) {
        ItemStack input = controller.input();
        if (isEmpty(input)) return false;
        for (JugFluidFillingRecipe recipe : recipeManager.jugRecipes().filling()) {
            if (!CraftEngineUtil.isItem(input, recipe.emptyInput()) || !matches(recipe.fluidExpression(), recipe.amount(), controller.fluid())) continue;
            ItemStack result = CraftEngineUtil.createItem(recipe.filledResult(), 1);
            refreshJugItem(result);
            if (!canInsert(controller.output(), result)) continue;
            FluidStack before = controller.fluid();
            byte[] beforeInvalid = controller.invalidLibuidFluid();
            if (JugInvoker.drain(controller.tank(), recipe.amount(), FluidAction.EXECUTE).amount() != recipe.amount()) {
                controller.tank().setFluid(before);
                controller.invalidLibuidFluid(beforeInvalid);
                continue;
            }
            controller.output(insert(controller.output(), result));
            consumeInputOne(location, controller, input);
            playFluidTransferSound(location, Sound.ITEM_BUCKET_FILL);
            return true;
        }
        return false;
    }

    private boolean emptyInputGeneric(Location location, JugBlockEntityController controller) {
        ItemStack input = controller.input();
        if (isEmpty(input)) return false;
        FluidActionResult simulated = FluidUtil.tryEmptyContainer(one(input), previewTank(controller), emptyingMaxAmount(controller), FluidAction.SIMULATE);
        if (!simulated.success()) return false;
        ItemStack predicted = predictContainerResult(input, simulated.moved(), true);
        if (isEmpty(predicted) || !canInsert(controller.output(), predicted)) return false;
        FluidStack before = controller.fluid();
        byte[] beforeInvalid = controller.invalidLibuidFluid();
        FluidActionResult result = FluidUtil.tryEmptyContainer(one(input), controller.tank(), emptyingMaxAmount(controller), FluidAction.EXECUTE);
        if (!acceptGenericExecuteResult(result)) {
            controller.tank().setFluid(before);
            controller.invalidLibuidFluid(beforeInvalid);
            return false;
        }
        refreshJugItem(result.result());
        controller.output(insert(controller.output(), result.result()));
        consumeInputOne(location, controller, input);
        playFluidTransferSound(location, Sound.ITEM_BUCKET_EMPTY);
        return true;
    }

    private boolean fillInputGeneric(Location location, JugBlockEntityController controller) {
        ItemStack input = controller.input();
        if (isEmpty(input)) return false;
        FluidActionResult simulated = FluidUtil.tryFillContainer(one(input), previewTank(controller), controller.fluidAmount(), FluidAction.SIMULATE);
        if (!simulated.success()) return false;
        ItemStack predicted = predictContainerResult(input, simulated.moved(), false);
        if (isEmpty(predicted) || !canInsert(controller.output(), predicted)) return false;
        FluidStack before = controller.fluid();
        byte[] beforeInvalid = controller.invalidLibuidFluid();
        FluidActionResult result = FluidUtil.tryFillContainer(one(input), controller.tank(), controller.fluidAmount(), FluidAction.EXECUTE);
        if (!acceptGenericExecuteResult(result)) {
            controller.tank().setFluid(before);
            controller.invalidLibuidFluid(beforeInvalid);
            return false;
        }
        refreshJugItem(result.result());
        controller.output(insert(controller.output(), result.result()));
        consumeInputOne(location, controller, input);
        playFluidTransferSound(location, Sound.ITEM_BUCKET_FILL);
        return true;
    }

    private static void playFluidTransferSound(Location location, Sound sound) {
        if (location.getWorld() != null) location.getWorld().playSound(location, sound, 1.0f, 1.0f);
    }

    static boolean acceptGenericExecuteResult(FluidActionResult result) {
        return result != null && result.success() && !isEmpty(result.result());
    }

    static void refreshJugItem(ItemStack item) {
        if (isEmpty(item)) return;
        try {
            String modelPrefix = JugItemBehavior.modelPrefix(item);
            if (modelPrefix == null) return;
            var handler = FluidContainerRegistry.handlerFor(one(item));
            if (handler.isEmpty()) return;
            JugItemPresentation.refresh(item, handler.get().tankCapacity(0), modelPrefix);
        } catch (Throwable ignored) {

        }
    }

    private static ItemStack predictContainerResult(ItemStack input, FluidStack moved, boolean emptying) {
        if (moved.isEmpty()) return null;
        java.util.Optional<dev.tako.libuid.api.item.FluidHandlerItem> lookup = FluidContainerRegistry.handlerFor(one(input));
        if (lookup.isEmpty()) return null;
        FluidHandlerItem handler = lookup.get();
        if (emptying) {
            if (!FluidStack.matches(moved, handler.drain(moved, FluidAction.EXECUTE))) return null;
        } else if (handler.fill(moved, FluidAction.EXECUTE) != moved.amount()) {
            return null;
        }
        return handler.container();
    }

    private static FluidTank previewTank(JugBlockEntityController controller) {
        FluidTank preview = new FluidTank(controller.tank().capacity());
        preview.setFluid(controller.fluid());
        return preview;
    }

    private static int emptyingMaxAmount(JugBlockEntityController controller) {
        return Math.max(0, controller.tank().capacity() - controller.fluidAmount());
    }

    private void processSoaking(Location location, JugBlockEntityController controller) {
        ItemStack input = controller.input();
        if (isEmpty(input)) { controller.resetProgress(); return; }
        for (JugSoakingRecipe recipe : recipeManager.jugRecipes().soaking()) {
            if (!matchesIngredient(input, recipe.ingredient()) || !matches(recipe.fluidExpression(), recipe.amount(), controller.fluid())) continue;

            ItemStack result = CraftEngineUtil.createItem(recipe.result(), 1);
            if (isEmpty(result) || !canInsert(controller.output(), result)) {
                controller.resetProgress();
                controller.markUnsaved();
                return;
            }
            int total = Math.max(1, recipe.time());
            controller.processingTimeTotal(total);
            controller.processingTime(controller.processingTime() + 1);
            if (controller.processingTime() >= total) {
                controller.output(insert(controller.output(), result));
                consumeInputOne(location, controller, input);
                if (recipe.consumeFluid()) JugInvoker.drain(controller.tank(), recipe.amount(), FluidAction.EXECUTE);
                controller.resetProgress();
            }
            controller.markUnsaved();
            return;
        }
        controller.resetProgress();
    }

    private void consumeInputOne(Location location, JugBlockEntityController controller, ItemStack input) {
        consumeOne(controller, input);
        menuSessions.skipNextInputRead(location);
    }

    private boolean matchesIngredient(ItemStack stack, String expression) {
        return recipeManager.matchesIngredient(stack, new IngredientDef(null, null, expression));
    }

    private static boolean matches(Object expression, int amount, FluidStack fluid) {
        try {
            SizedFluidIngredient ingredient = FluidIngredient.parseSized(expression, amount);
            return ingredient.test(fluid);
        } catch (Throwable ignored) { return false; }
    }

    private static FluidStack expressionStack(Object expression, int amount) {
        String fluidId = switch (expression) {
            case String id when !id.isBlank() && !id.startsWith("#") -> id;
            case java.util.Map<?, ?> map when map.get("fluid") instanceof String id
                    && !id.isBlank() && !id.startsWith("#")
                    && map.keySet().stream().allMatch(key -> "fluid".equals(key) || "amount".equals(key)) -> id;
            default -> null;
        };
        if (fluidId == null) return FluidStack.EMPTY;
        try {
            return FluidRegistry.get(ResourceKey.of(fluidId))
                    .map(type -> FluidStack.of(type, amount)).orElse(FluidStack.EMPTY);
        } catch (Throwable ignored) {
            return FluidStack.EMPTY;
        }
    }

    private void processHoppers(Location location, Block block, JugBlockEntityController controller) {
        Block above = block.getRelative(BlockFace.UP);
        if (above.getType() == Material.HOPPER && above.getState() instanceof Container source) {

            if (transferOneFromHopper(source, controller)) menuSessions.skipNextInputRead(location);
        }
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.HOPPER && below.getState() instanceof Container destination && controller.hasOutput()) {
            ItemStack one = one(controller.output());
            if (destination.getInventory().addItem(one).isEmpty()) consumeOneOutput(controller);
        }
    }

    static boolean transferOneFromHopper(Container source, JugBlockEntityController controller) {
        return JugHopperTransfer.transferOne(source, controller);
    }

    private void requestOwnerMenuCloseForBreak(Location location, java.util.UUID ownerId) {
        long generation = menuSessions.generation(location, ownerId);
        Player owner = Bukkit.getPlayer(ownerId);
        if (generation == JugMenuSessionRegistry.NO_GENERATION || owner == null || !owner.isOnline()) {
            closeSession(location, ownerId);
            completePendingGuiExplosion(location);
            return;
        }
        MenuCloseFlow.requestOwnerClose(
                closeFlowScheduler(location, owner),
                () -> menuSessions.isCurrentGeneration(location, ownerId, generation),
                () -> menuSessions.hasInventory(location, ownerId, owner.getOpenInventory().getTopInventory()),
                owner::closeInventory);

        SCHEDULER.getRegionScheduler().runTaskLater(javaPlugin, location, 1L, () -> {
            boolean currentGeneration = menuSessions.isCurrentGeneration(location, ownerId, generation);
            Player current = Bukkit.getPlayer(ownerId);
            boolean ownerInvalid = current == null || !current.isOnline() || !current.isValid();
            if (currentGeneration && ownerInvalid) closeSession(location, ownerId);
            if (!currentGeneration || ownerInvalid) completePendingGuiExplosion(location);
        });
    }

    private void closeOwnerInventory(java.util.UUID ownerId) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) return;
        if (!plugin.isEnabled()) {
            try { owner.closeInventory(); } catch (Throwable ignored) { }
            return;
        }
        SCHEDULER.getEntityScheduler().runTask(javaPlugin, owner, (Runnable) owner::closeInventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location key = blockKey(event.getBlock());
        java.util.UUID ownerId = ownerAt(key);
        if (ownerId != null) {

            boolean firstCloseRequest = menuSessions.requestClose(key, ownerId);
            JugBreakSessionPolicy.Decision decision = JugBreakSessionPolicy.decide(true, !firstCloseRequest);
            event.setCancelled(decision.cancelBreak());
            event.getPlayer().sendMessage(TextUtil.parse(event.getPlayer(), ConfigManager.getOr("jug_menu_break_closing",
                    "<yellow>This Jug menu is closing. Try breaking it again.")));
            if (decision.requestOwnerClose()) requestOwnerMenuCloseForBreak(key, ownerId);
            return;
        }

        JugBlockEntityController controller = getController(event.getBlock());
        if (controller == null) return;
        boolean dropBlock = event.isDropItems() && event.getPlayer().getGameMode() != GameMode.CREATIVE;

        if (dropBlock) event.setDropItems(false);
        dropStatefulJug(event.getBlock(), controller, true, dropBlock);
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
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (getController(block) == null) continue;
            iterator.remove();
            pendingExplosions.stage(event, new PendingExplosionJug(block));
        }
    }

    private void settleExplosion(Event event, boolean cancelled, float radius) {
        List<PendingExplosionJug> staged = pendingExplosions.drain(event);
        ExplosionSettleFlow.settleStaged(staged, cancelled,
                pending -> settleExplosionJug(pending.block(), radius));
    }

    private void settleExplosionJug(Block block, float radius) {
        JugBlockEntityController controller = getController(block);
        if (controller == null) return;
        Location key = blockKey(block);
        java.util.UUID ownerId = ownerAt(key);
        long generation = ownerId == null ? JugMenuSessionRegistry.NO_GENERATION : menuSessions.generation(key, ownerId);
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        boolean hasGui = generation != JugMenuSessionRegistry.NO_GENERATION
                && owner != null && owner.isOnline() && owner.isValid();
        pendingGuiExplosions.settle(false, key, new PendingGuiExplosion(block, radius), hasGui,
                () -> requestOwnerMenuCloseForBreak(key, ownerId), pending -> {
                    if (ownerId != null) closeSession(key, ownerId);
                    finishExplosionJug(block, controller, radius);
                });
    }

    private void completePendingGuiExplosion(Location location) {
        pendingGuiExplosions.complete(location, pending -> {
            JugBlockEntityController controller = getController(pending.block());
            if (controller != null) finishExplosionJug(pending.block(), controller, pending.radius());
        });
    }

    private void finishExplosionJug(Block block, JugBlockEntityController controller, float radius) {
        boolean dropItems = survivesExplosionLoot(radius, RandomUtils.generateRandomFloat(0, 1));
        dropStatefulJug(block, controller, dropItems, dropItems);
        CraftEngineBlocks.remove(block, false);
    }

    private float explosionRadius(BlockExplodeEvent event) {
        return ExplosionSettleFlow.radius(VersionHelper.isOrAbove1_21,
                VersionHelper.isOrAbove1_21 && ExplosionUtils.isDroppingItems(event),
                event.getYield(), () -> ExplosionUtils.getRadius(event.getYield(), event.getExplosionResult()));
    }

    private float explosionRadius(EntityExplodeEvent event) {
        return ExplosionSettleFlow.radius(VersionHelper.isOrAbove1_21,
                VersionHelper.isOrAbove1_21 && ExplosionUtils.isDroppingItems(event),
                event.getYield(), () -> ExplosionUtils.getRadius(event.getYield(), event.getExplosionResult()));
    }

    static boolean survivesExplosionLoot(float radius, float randomValue) {
        return ExplosionSettleFlow.survives(radius, randomValue);
    }

    private void dropStatefulJug(Block block, JugBlockEntityController controller, boolean dropContents, boolean dropBlock) {
        Location key = blockKey(block);
        controllerCache.remove(key);

        Location contentsAt = block.getLocation().add(.5, .5, .5);
        JugDropFlow.settleContents(
                dropContents,
                () -> dropIfPresent(block, contentsAt, controller.input()),
                () -> dropIfPresent(block, contentsAt, controller.output()),
                () -> {
                    controller.input(null);
                    controller.output(null);
                });
        if (!dropBlock) return;

        String id = JugDropId.resolve(CraftEngineUtil.getCustomBlockId(block), JugDropId.FALLBACK_JUG_ID);
        ItemStack drop = CraftEngineUtil.createItem(id, 1);
        if (isEmpty(drop)) {
            plugin.getLogger().warning(ConfigManager.getOr("jug_drop_item_missing", "Jug block %block_id% has no matching item; falling back to %fallback_id%.")
                    .replace("%block_id%", String.valueOf(id)).replace("%fallback_id%", JugDropId.FALLBACK_JUG_ID));
            drop = CraftEngineUtil.createItem(JugDropId.FALLBACK_JUG_ID, 1);
        }
        if (isEmpty(drop)) return;
        byte[] opaqueFluid = controller.invalidLibuidFluid();
        if (opaqueFluid != null) {
            JugFluidItemData.writeOpaqueFluid(drop, opaqueFluid, null, null);
        } else {
            JugFluidItemData.writeTo(drop, controller.tank(), null, null);
            refreshJugItem(drop);
        }
        JugCapacityBar.apply(drop, controller.fluidAmount(), controller.tank().capacity());
        block.getWorld().dropItemNaturally(contentsAt, drop);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (getController(block) == null) return;
        onPlaced(block);
        ItemStack placed = event.getItemInHand().clone();
        SCHEDULER.getRegionScheduler().runTaskLater(javaPlugin, blockKey(block), 1L, () -> {
            JugBlockEntityController controller = getController(block);
            if (controller == null) return;
            if (JugFluidItemData.readInto(placed, controller.tank(), controller::input, controller::output)
                    == dev.tako.libuid.api.item.ItemFluidDataReadResult.Status.INVALID) {
                controller.invalidLibuidFluid(JugFluidItemData.readInvalidFluidBytes(placed));
            } else {
                controller.invalidLibuidFluid(null);
            }
            controller.markUnsaved();
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        for (Location location : List.copyOf(controllerCache.keySet())) {
            if (event.getWorld().equals(location.getWorld())
                    && (location.getBlockX() >> 4) == chunkX
                    && (location.getBlockZ() >> 4) == chunkZ) {
                controllerCache.remove(location);
            }
        }
        for (java.util.Map.Entry<java.util.UUID, Location> entry : java.util.Map.copyOf(openMenus).entrySet()) {
            Location location = entry.getValue();
            if (!event.getWorld().equals(location.getWorld())) continue;
            if ((location.getBlockX() >> 4) != chunkX || (location.getBlockZ() >> 4) != chunkZ) continue;
            java.util.UUID playerId = entry.getKey();
            persistSessionBeforeRelease(playerId, location);
            closeSession(location, playerId);

            pendingGuiExplosions.discard(location);
            closeOwnerInventory(playerId);
        }
    }

    static Location blockKey(Block block) { return blockKey(block.getLocation()); }
    static Location blockKey(Location location) { return location.toBlockLocation(); }
    private static boolean isEmpty(ItemStack stack) { return stack == null || stack.isEmpty(); }

    static int remainingAfterSingleTransfer(int amount) { return Math.max(0, amount - 1); }
    private static ItemStack one(ItemStack stack) { ItemStack copy = stack.clone(); copy.setAmount(1); return copy; }
    private static boolean canInsert(ItemStack current, ItemStack incoming) { return !isEmpty(incoming) && (isEmpty(current) || (current.isSimilar(incoming) && current.getAmount() + incoming.getAmount() <= current.getMaxStackSize())); }
    private static ItemStack insert(ItemStack current, ItemStack incoming) { if (isEmpty(current)) return incoming.clone(); ItemStack next = current.clone(); next.setAmount(next.getAmount() + incoming.getAmount()); return next; }

    private void replaceHeldOneDelayed(Player player, Location jugLocation, ItemStack held, ItemStack result, Runnable rollback) {
        if (isEmpty(result)) {
            rollback.run();
            return;
        }
        ItemStack payout = result.clone();
        JugDeliveryFlow flow = new JugDeliveryFlow(new JugDeliveryFlow.Scheduler() {
            public JugDeliveryFlow.Handle entityLater(Runnable task, Runnable retired, long delay) {
                AtomicBoolean retiredCalled = new AtomicBoolean();
                Runnable retiredOnce = () -> {
                    if (retiredCalled.compareAndSet(false, true)) retired.run();
                };
                if (player == null || !player.isValid() || !plugin.isEnabled()) {
                    retiredOnce.run();
                    return () -> true;
                }
                ScheduledTask scheduled = player.getScheduler().runDelayed(
                        javaPlugin, st -> task.run(), retiredOnce, Math.max(1L, delay));
                if (scheduled == null) retiredOnce.run();
                return () -> scheduled == null || scheduled.isCancelled();
            }
            public void region(Runnable task, Runnable retired) {
                AtomicBoolean retiredCalled = new AtomicBoolean();
                Runnable retiredOnce = () -> {
                    if (retiredCalled.compareAndSet(false, true)) retired.run();
                };
                if (jugLocation == null || jugLocation.getWorld() == null) {
                    retiredOnce.run();
                    return;
                }
                ScheduledTask scheduled = Bukkit.getRegionScheduler().run(
                        javaPlugin, jugLocation, st -> task.run());
                if (scheduled == null) retiredOnce.run();
            }
            public void retain(Runnable pending) { pendingDeliveryCompensations.add(pending); }
        }, () -> {
            if (held.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
            else { held.setAmount(remainingAfterSingleTransfer(held.getAmount())); player.getInventory().setItemInMainHand(held); }
        }, player::isOnline, () -> {
            for (ItemStack leftover : player.getInventory().addItem(payout.clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }, rollback, () -> {
            if (jugLocation.getWorld() != null) jugLocation.getWorld().dropItemNaturally(jugLocation, payout.clone());
        });
        flow.register();
    }
    private static void consumeOne(JugBlockEntityController controller, ItemStack input) { if (input.getAmount() <= 1) controller.input(null); else { ItemStack next = input.clone(); next.setAmount(next.getAmount() - 1); controller.input(next); } }
    private static void consumeOneOutput(JugBlockEntityController controller) {
        ItemStack output = controller.output();
        if (isEmpty(output)) return;
        controller.output(output.getAmount() <= 1 ? null : decrement(output));
    }

    private static ItemStack decrement(ItemStack stack) {
        ItemStack next = stack.clone();
        next.setAmount(next.getAmount() - 1);
        return next;
    }
    private static void dropIfPresent(Block block, Location at, ItemStack item) { if (!isEmpty(item)) block.getWorld().dropItemNaturally(at, item); }

    private void persistSessionBeforeRelease(java.util.UUID playerId, Location location) {
        try {
            Inventory inventory = menuSessions.inventory(location, playerId);
            JugBlockEntityController controller = inventory == null ? null : getController(location.getBlock());
            if (controller != null) {
                syncInputSlot(location, controller, inventory);
                clearMenuInputSlot(inventory);
            }
        } catch (Throwable failure) {
            plugin.getLogger().warning("Unable to persist Jug menu input at " + location + ": " + failure);
        }
    }

    private static final class ControllerRef { private JugBlockEntityController value; void set(JugBlockEntityController value) { this.value = value; } }

    private record DisplaySnapshot(String fluidKey, int amount, int capacity, int progressStage) {
    }

    private record PendingExplosionJug(Block block) {
    }

    private record PendingGuiExplosion(Block block, float radius) {
    }

}
