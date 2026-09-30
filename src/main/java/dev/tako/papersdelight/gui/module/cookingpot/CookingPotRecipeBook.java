package dev.tako.papersdelight.gui.module.cookingpot;

import dev.tako.papersdelight.config.ConfigManager;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.cookingpot.CookingPotBlockBehavior;
import dev.tako.papersdelight.cookingpot.CookingPotData;
import dev.tako.papersdelight.cookingpot.CookingPotLayout;
import dev.tako.papersdelight.cookingpot.CookingPotManager;
import dev.tako.papersdelight.gui.MenuManager;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.recipe.IngredientDef;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.support.FeatureSupport;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.util.ItemMetaUtil;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CookingPotRecipeBook implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private static final DecimalFormat SECONDS = new DecimalFormat("0.#");

    private static List<String> replacePage(List<String> lore, int page) {
        List<String> source = lore.isEmpty()
                ? List.of(ConfigManager.getOr("cooking_pot_recipe_book_page_lore", "<!i><gray>点击查看第 <yellow>%page% <gray>页")) : lore;
        return source.stream().map(line -> line.replace("%page%", String.valueOf(page))).toList();
    }

    private static List<String> orDefault(List<String> lore, String fallback) {
        return lore.isEmpty() ? List.of(fallback) : lore;
    }

    private static List<String> replaceCount(List<String> lore, int count) {
        List<String> source = lore.isEmpty()
                ? List.of(ConfigManager.getOr("cooking_pot_recipe_book_loaded_recipes_lore", "<!i><gray>已加载配方: <white>%count%")) : lore;
        return source.stream().map(line -> line.replace("%count%", String.valueOf(count))).toList();
    }

    final JavaPlugin plugin;
    private final RecipeManager recipeManager;
    private final MenuManager menuManager;
    private final CookingPotManager cookingPotManager;

    private final Map<UUID, Integer> expandedClickTick = new ConcurrentHashMap<>();

    private final Set<UUID> viewTransitions = ConcurrentHashMap.newKeySet();

    private final Map<UUID, CCTask> tagAnimationTasks = new ConcurrentHashMap<>();

    private int expListPrevPage;
    private int expListNextPage;
    private int expListPageInfo;
    private int expListHeatIcon;
    private int expListFilterToggle;
    private int expListRecipesStart;
    private int expListRecipesPerPage;
    private int[] expListBorder;

    private int expDetailBack;
    private int expDetailAutoFill;
    private int[] expDetailBorderRow3;
    private int[] expDetailBorderRow45;
    private int[] expDetailIngredients;
    private int expDetailCookInfo;
    private int expDetailResult;
    private int expDetailContainer;

    private long tagCycleIntervalTicks;

    public CookingPotRecipeBook(JavaPlugin plugin, RecipeManager recipeManager,
                                MenuManager menuManager, CookingPotManager cookingPotManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.menuManager = menuManager;
        this.cookingPotManager = cookingPotManager;
        reloadConfig();
    }

    public void reloadConfig() {

        this.expListPrevPage = CookingPotLayout.RECIPE_LIST_PREVIOUS_PAGE;
        this.expListNextPage = CookingPotLayout.RECIPE_LIST_NEXT_PAGE;
        this.expListPageInfo = CookingPotLayout.RECIPE_LIST_PAGE_INFO;
        this.expListHeatIcon = CookingPotLayout.RECIPE_LIST_HEAT_ICON;
        this.expListFilterToggle = CookingPotLayout.RECIPE_LIST_FILTER_TOGGLE;
        this.expListRecipesStart = CookingPotLayout.RECIPE_LIST_START;
        this.expListRecipesPerPage = CookingPotLayout.RECIPES_PER_PAGE;
        this.expListBorder = CookingPotLayout.RECIPE_LIST_BORDER;

        this.expDetailBack = CookingPotLayout.RECIPE_DETAIL_BACK;
        this.expDetailAutoFill = CookingPotLayout.RECIPE_DETAIL_AUTO_FILL;
        this.expDetailBorderRow3 = CookingPotLayout.RECIPE_DETAIL_BORDER_ROW3;
        this.expDetailBorderRow45 = CookingPotLayout.RECIPE_DETAIL_BORDER_ROW45;
        this.expDetailIngredients = CookingPotLayout.RECIPE_DETAIL_INGREDIENTS;
        this.expDetailCookInfo = CookingPotLayout.RECIPE_DETAIL_COOK_INFO;
        this.expDetailResult = CookingPotLayout.RECIPE_DETAIL_RESULT;
        this.expDetailContainer = CookingPotLayout.RECIPE_DETAIL_CONTAINER;

        this.tagCycleIntervalTicks = Math.max(1L, ConfigManager.getInt("recipe_book.tag_cycle_interval_ticks", 20));
    }

    public void openExpanded(Player player, Block potBlock) {
        openExpandedList(player, potBlock, 0);
    }

    private void openExpandedList(Player player, Block potBlock, int page) {
        openExpandedList(player, potBlock, page, false);
    }

    private void openExpandedList(Player player, Block potBlock, int page, boolean filterEnabled) {
        List<CookingRecipe> recipes = recipeManager.recipes();
        if (filterEnabled) {
            recipes = recipes.stream().filter(r -> canCraftWithInventory(player, r)).toList();
        }
        int maxPage = Math.max(0, (recipes.size() - 1) / expListRecipesPerPage);
        int safePage = Math.max(0, Math.min(page, maxPage));

        Location potLoc = potBlock.getLocation();
        ExpandedCookingPotHolder holder = ExpandedCookingPotHolder.list(safePage, potLoc, filterEnabled);
        Inventory inv = Bukkit.createInventory(holder, CookingPotLayout.EXPANDED_SIZE,

                TextUtil.parse("<shift:-8><white><image:farmersdelight:recipe_list></white>"
                        + "<shift:-153><reset><lang:container.farmersdelight.cooking_pot>"));
        holder.inventory = inv;

        populateCookingPotArea(inv, potBlock);

        renderRecipeList(inv, recipes, safePage, maxPage, filterEnabled);

        openExpandedView(player, inv);

        cookingPotManager.openSession(potBlock, player);
    }

    private void openExpandedDetail(Player player, Block potBlock, int page, int recipeIndex) {
        openExpandedDetail(player, potBlock, page, recipeIndex, false);
    }

    private void openExpandedDetail(Player player, Block potBlock, int page, int recipeIndex, boolean filterEnabled) {
        List<CookingRecipe> recipes = filterEnabled
                ? recipeManager.recipes().stream().filter(r -> canCraftWithInventory(player, r)).toList()
                : recipeManager.recipes();
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) {
            openExpandedList(player, potBlock, page, filterEnabled);
            return;
        }

        CookingRecipe recipe = recipes.get(recipeIndex);
        Location potLoc = potBlock.getLocation();
        ExpandedCookingPotHolder holder = ExpandedCookingPotHolder.detail(page, recipeIndex, potLoc, filterEnabled);
        Inventory inv = Bukkit.createInventory(holder, CookingPotLayout.EXPANDED_SIZE,

                TextUtil.parse("<shift:-8><white><image:farmersdelight:recipe_cooking_pot></white>"
                        + "<shift:-153><reset><lang:container.farmersdelight.cooking_pot>"));
        holder.inventory = inv;

        populateCookingPotArea(inv, potBlock);

        renderRecipeDetail(inv, recipe, recipeIndex, player);

        openExpandedView(player, inv);
        cookingPotManager.openSession(potBlock, player);

        startTagAnimation(player, inv, recipe, expDetailIngredients);
    }

    private void openExpandedView(Player player, Inventory inventory) {
        UUID uuid = player.getUniqueId();
        viewTransitions.add(uuid);
        try {
            player.openInventory(inventory);
        } finally {
            viewTransitions.remove(uuid);
        }
    }

    private void foldBackToPot(Player player, Location potLoc) {
        player.closeInventory();
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, potLoc, () -> {
            Block block = potLoc.getBlock();
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return;
            if (!CookingPotBlockBehavior.isCookingPot(block)) return;
            SCHEDULER.getEntityScheduler().runTask(plugin, player, () -> {
                menuManager.openMenu(player, "cooking_pot", inv -> {
                    if (!cookingPotManager.isSessionOwner(block, player)) return;
                    cookingPotManager.syncFromInventory(block, inv);
                    cookingPotManager.stopCookingIfOwner(block, player);
                });
                CookingPotData data = cookingPotManager.openSession(block, player);
                if (data != null) {
                    Inventory top = player.getOpenInventory().getTopInventory();
                    cookingPotManager.populateInventory(top, data);
                    cookingPotManager.updateHeatIndicator(top, block);
                }
            });
        }, 1L);
    }

    private void populateCookingPotArea(Inventory inv, Block potBlock) {

        ItemStack border = ConfigManager.buildGuiItem(
                "cooking_pot.buttons.border", null, "<white> </white>", List.of());
        List<Integer> lockedSlots = ConfigManager.getIntegerList("cooking_pot.locked_slots");
        if (lockedSlots.isEmpty()) lockedSlots = List.of(0, 4, 6, 8, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 26);
        for (int slot : lockedSlots) {
            inv.setItem(slot, border);
        }

        inv.setItem(5, null);

        inv.setItem(9, ConfigManager.buildGuiItem("cooking_pot.buttons.recipe_book", "cooking_pot_recipe_book",
                ConfigManager.getOr("cooking_pot_recipe_book_name", "<!i><green><bold>配方书"), List.of()));

        cookingPotManager.updateHeatIndicator(inv, potBlock);

        CookingPotData data = cookingPotManager.getSessionData(potBlock);
        if (data != null) {
            cookingPotManager.populateInventory(inv, data);
        }
    }

    private void renderRecipeList(Inventory inv, List<CookingRecipe> recipes, int page, int maxPage, boolean filterEnabled) {
        ItemStack border = ConfigManager.buildGuiItem(
                "cooking_pot.buttons.border", null, "<white> </white>", List.of());

        for (int slot : expListBorder) {
            inv.setItem(slot, border);
        }

        inv.setItem(expListHeatIcon, border);

        if (!FeatureSupport.cookingPotRecipeControls()) {
            inv.setItem(expListFilterToggle, null);
        } else if (filterEnabled) {
            inv.setItem(expListFilterToggle, buildGuiItem(
                    "recipe_book.expanded.list.buttons.filter_enabled", null,
                    ConfigManager.getOr("cooking_pot_recipe_book_filter_enabled_name", "<!i><green>可烹饪配方"),
                    List.of(ConfigManager.getOr("cooking_pot_recipe_book_filter_enabled_lore", "<!i><gray>点击显示全部配方"))));
        } else {
            inv.setItem(expListFilterToggle, buildGuiItem(
                    "recipe_book.expanded.list.buttons.filter_disabled", null,
                    ConfigManager.getOr("cooking_pot_recipe_book_filter_disabled_name", "<!i><yellow>全部配方"),
                    List.of(ConfigManager.getOr("cooking_pot_recipe_book_filter_disabled_lore", "<!i><gray>点击只显示可烹饪的配方"))));
        }

        boolean isFirstPage = page <= 0;
        inv.setItem(expListPrevPage, buildGuiItem(
                "recipe_book.expanded.list.buttons.previous_page", null,
                ConfigManager.getOr(isFirstPage
                                ? "cooking_pot_recipe_book_list_button_previous_page_disabled_name"
                                : "cooking_pot_recipe_book_list_button_previous_page_name",
                        isFirstPage ? "<!i><gray>上一页" : "<!i><green>上一页"),
                isFirstPage
                        ? orDefault(ConfigManager.getList(
                                "cooking_pot_recipe_book_list_button_previous_page_disabled_lore"),
                                "<!i><gray>已经是第一页了")
                        : replacePage(ConfigManager.getList(
                                "cooking_pot_recipe_book_list_button_previous_page_lore"), page)));

        inv.setItem(expListPageInfo, buildGuiItem(
                "recipe_book.expanded.list.buttons.page_info", null,
                ConfigManager.getOr("cooking_pot_recipe_book_list_button_page_info_name",
                                "<!i><yellow>第 %current% / %total% 页")
                        .replace("%current%", String.valueOf(page + 1))
                        .replace("%total%", String.valueOf(maxPage + 1)),
                replaceCount(ConfigManager.getList(
                        "cooking_pot_recipe_book_list_button_page_info_lore"), recipes.size())));

        boolean isLastPage = page >= maxPage;
        inv.setItem(expListNextPage, buildGuiItem(
                "recipe_book.expanded.list.buttons.next_page", null,
                ConfigManager.getOr(isLastPage
                                ? "cooking_pot_recipe_book_list_button_next_page_disabled_name"
                                : "cooking_pot_recipe_book_list_button_next_page_name",
                        isLastPage ? "<!i><gray>下一页" : "<!i><green>下一页"),
                isLastPage
                        ? orDefault(ConfigManager.getList(
                                "cooking_pot_recipe_book_list_button_next_page_disabled_lore"),
                                "<!i><gray>已经是最后一页了")
                        : replacePage(ConfigManager.getList(
                                "cooking_pot_recipe_book_list_button_next_page_lore"), page + 2)));

        int start = page * expListRecipesPerPage;
        int end = Math.min(recipes.size(), start + expListRecipesPerPage);
        for (int i = start; i < end; i++) {
            inv.setItem(expListRecipesStart + (i - start), createRecipeIcon(recipes.get(i), i));
        }
    }

    private void renderRecipeDetail(Inventory inv, CookingRecipe recipe, int recipeIndex, Player player) {
        ItemStack border = ConfigManager.buildGuiItem(
                "cooking_pot.buttons.border", null, "<white> </white>", List.of());

        for (int slot : expDetailBorderRow3) {
            inv.setItem(slot, border);
        }
        inv.setItem(expDetailBack, buildGuiItem(
                "recipe_book.expanded.detail.buttons.back_to_list", null,
                ConfigManager.getOr("cooking_pot_recipe_book_detail_button_back_to_list_name",
                        "<!i><green>返回配方列表"),
                List.of()));
        if (FeatureSupport.cookingPotRecipeControls()) {
            if (canCraftWithInventory(player, recipe)) {
                inv.setItem(expDetailAutoFill, buildGuiItem(
                        "recipe_book.expanded.detail.buttons.auto_fill", null,
                        ConfigManager.getOr("cooking_pot_recipe_book_auto_fill_name", "<!i><aqua>一键填入食材"),
                        List.of(ConfigManager.getOr("cooking_pot_recipe_book_auto_fill_available_lore", "<!i><gray>将背包中匹配的食材移入厨锅"))));
            } else {
                inv.setItem(expDetailAutoFill, buildGuiItem(
                        "recipe_book.expanded.detail.buttons.auto_fill", null,
                        ConfigManager.getOr("cooking_pot_recipe_book_auto_fill_name", "<!i><aqua>一键填入食材"),
                        List.of(ConfigManager.getOr("cooking_pot_recipe_book_auto_fill_unavailable_lore", "<!i><red>当前食材不足以烹饪"))));
            }
        } else {
            inv.setItem(expDetailAutoFill, null);
        }

        for (int slot : expDetailBorderRow45) {
            inv.setItem(slot, border);
        }

        for (int i = 0; i < recipe.ingredients.size() && i < 6; i++) {
            inv.setItem(expDetailIngredients[i], createIngredientIcon(recipe.ingredients.get(i)));
        }

        inv.setItem(expDetailCookInfo, buildRecipeInfoIcon(recipe));

        inv.setItem(expDetailResult, createResultIcon(recipe));

        if (recipe.container != null && !recipe.container.isBlank()) {
            inv.setItem(expDetailContainer, createIdIcon(recipe.container,
                    ConfigManager.getOr("cooking_pot_recipe_book_detail_icon_container_name", "<!i><aqua>容器"),
                    ConfigManager.getList("cooking_pot_recipe_book_detail_icon_container_lore")));
        }
    }

    @EventHandler
    public void onExpandedClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ExpandedCookingPotHolder holder)) return;

        int slot = event.getRawSlot();
        if (slot < 0) return;

        int currentTick = org.bukkit.Bukkit.getCurrentTick();
        Integer lastTick = expandedClickTick.put(player.getUniqueId(), currentTick);
        if (lastTick != null && lastTick == currentTick) {
            event.setCancelled(true);
            return;
        }

        if (slot >= CookingPotLayout.EXPANDED_SIZE) {
            if (event.isShiftClick()) {

                event.setCancelled(true);
                ItemStack moving = event.getCurrentItem();
                if (moving == null || moving.isEmpty()) return;
                Inventory inv = event.getView().getTopInventory();
                ItemStack remainder = manualShiftIntoUpperSlots(inv, moving);
                event.setCurrentItem(remainder.isEmpty() ? null : remainder);
            }
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        if (slot < CookingPotLayout.SIZE) {

            if (slot == CookingPotLayout.RECIPE_BOOK_BUTTON) {
                event.setCancelled(true);
                if (event.isShiftClick()) return;
                if (event.getCursor() != null && !event.getCursor().isEmpty()) return;
                foldBackToPot(player, holder.potLocation);
                return;
            }

            if (isIngredientSlot(slot) || slot == CookingPotLayout.UTENSIL) {

                if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                    event.setCancelled(true);
                }
                return;
            }

            if (slot == CookingPotLayout.FINAL_OUTPUT) {
                event.setCancelled(true);
                cookingPotManager.takeFinalOutputToCursor(player, event);
                return;
            }

            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (event.isShiftClick()) return;
        if (event.getCursor() != null && !event.getCursor().isEmpty()) return;

        if (holder.view == ExpandedCookingPotHolder.View.LIST) {
            handleExpandedListClick(player, holder, slot);
        } else {
            handleExpandedDetailClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onExpandedDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ExpandedCookingPotHolder holder)) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0) continue;

            if (rawSlot >= CookingPotLayout.SIZE && rawSlot < CookingPotLayout.EXPANDED_SIZE) {
                event.setCancelled(true);
                return;
            }

            if (rawSlot < CookingPotLayout.SIZE && !isIngredientSlot(rawSlot) && rawSlot != CookingPotLayout.UTENSIL) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onExpandedClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ExpandedCookingPotHolder holder)) return;

        cancelTagAnimation(player);
        UUID uuid = player.getUniqueId();
        if (viewTransitions.contains(uuid)) return;
        expandedClickTick.remove(uuid);

        Block block = holder.potLocation.getBlock();
        if (cookingPotManager.isSessionOwner(block, player)) {
            cookingPotManager.syncFromInventory(block, event.getInventory());
            cookingPotManager.stopCookingIfOwner(block, player);
        }
    }

    private void handleExpandedListClick(Player player, ExpandedCookingPotHolder holder, int slot) {
        List<CookingRecipe> recipes = holder.filterEnabled
                ? recipeManager.recipes().stream().filter(r -> canCraftWithInventory(player, r)).toList()
                : recipeManager.recipes();
        int maxPage = Math.max(0, (recipes.size() - 1) / expListRecipesPerPage);

        if (slot == expListPrevPage && holder.page > 0) {
            Block block = holder.potLocation.getBlock();
            if (!isCookingPot(block)) { player.closeInventory(); return; }
            syncAndReopen(player, block, () -> openExpandedList(player, block, holder.page - 1, holder.filterEnabled));
            return;
        }
        if (slot == expListNextPage && holder.page < maxPage) {
            Block block = holder.potLocation.getBlock();
            if (!isCookingPot(block)) { player.closeInventory(); return; }
            syncAndReopen(player, block, () -> openExpandedList(player, block, holder.page + 1, holder.filterEnabled));
            return;
        }
        if (slot == expListFilterToggle && FeatureSupport.cookingPotRecipeControls()) {
            Block block = holder.potLocation.getBlock();
            if (!isCookingPot(block)) { player.closeInventory(); return; }
            syncAndReopen(player, block, () -> openExpandedList(player, block, 0, !holder.filterEnabled));
            return;
        }

        int listSlot = slot - expListRecipesStart;
        if (listSlot >= 0 && listSlot < expListRecipesPerPage) {
            int recipeIndex = holder.page * expListRecipesPerPage + listSlot;
            if (recipeIndex >= 0 && recipeIndex < recipes.size()) {
                Block block = holder.potLocation.getBlock();
                if (!isCookingPot(block)) { player.closeInventory(); return; }
                syncAndReopen(player, block, () -> openExpandedDetail(player, block, holder.page, recipeIndex, holder.filterEnabled));
            }
        }
    }

    private void handleExpandedDetailClick(Player player, ExpandedCookingPotHolder holder, int slot) {
        if (slot == expDetailBack) {
            Block block = holder.potLocation.getBlock();
            if (!isCookingPot(block)) { player.closeInventory(); return; }
            syncAndReopen(player, block, () -> openExpandedList(player, block, holder.page, holder.filterEnabled));
            return;
        }
        if (slot == expDetailAutoFill && FeatureSupport.cookingPotRecipeControls()) {
            Block block = holder.potLocation.getBlock();
            if (!isCookingPot(block)) { player.closeInventory(); return; }
            List<CookingRecipe> recipes = holder.filterEnabled
                    ? recipeManager.recipes().stream().filter(r -> canCraftWithInventory(player, r)).toList()
                    : recipeManager.recipes();
            if (holder.recipeIndex >= 0 && holder.recipeIndex < recipes.size()) {
                CookingRecipe recipe = recipes.get(holder.recipeIndex);
                if (!canCraftWithInventory(player, recipe)) return;
                autoFillIngredients(player, holder.potLocation.getBlock(), recipe);
            }
        }
    }

    private boolean canCraftWithInventory(Player player, CookingRecipe recipe) {
        ItemStack[] contents = player.getInventory().getContents();

        int[] remaining = new int[contents.length];
        for (int i = 0; i < contents.length; i++) {
            remaining[i] = (contents[i] != null && !contents[i].isEmpty()) ? contents[i].getAmount() : 0;
        }

        for (dev.tako.papersdelight.recipe.IngredientDef ingredient : recipe.ingredients) {
            boolean found = false;
            for (int i = 0; i < contents.length; i++) {
                if (remaining[i] <= 0 || contents[i] == null) continue;
                if (recipeManager.matchesIngredient(contents[i], ingredient)) {
                    remaining[i]--;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void autoFillIngredients(Player player, Block potBlock, CookingRecipe recipe) {

        if (!canCraftWithInventory(player, recipe)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        org.bukkit.inventory.PlayerInventory playerInv = player.getInventory();

        for (int targetSlot : CookingPotLayout.INGREDIENTS) {
            ItemStack existing = top.getItem(targetSlot);
            if (existing == null || existing.isEmpty()) continue;

            boolean belongsToRecipe = false;
            for (dev.tako.papersdelight.recipe.IngredientDef ingredient : recipe.ingredients) {
                if (recipeManager.matchesIngredient(existing, ingredient)) {
                    belongsToRecipe = true;
                    break;
                }
            }
            if (!belongsToRecipe) {

                java.util.Map<Integer, ItemStack> leftover = playerInv.addItem(existing);
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                top.setItem(targetSlot, null);
            }
        }

        int[] emptySlots = new int[recipe.ingredients.size()];
        dev.tako.papersdelight.recipe.IngredientDef[] slotIngredients =
                new dev.tako.papersdelight.recipe.IngredientDef[recipe.ingredients.size()];
        int emptyCount = 0;
        for (int i = 0; i < recipe.ingredients.size() && i < CookingPotLayout.INGREDIENTS.length; i++) {
            int targetSlot = CookingPotLayout.INGREDIENTS[i];
            ItemStack existing = top.getItem(targetSlot);
            if (existing != null && !existing.isEmpty()) continue;
            emptySlots[emptyCount] = targetSlot;
            slotIngredients[emptyCount] = recipe.ingredients.get(i);
            emptyCount++;
        }

        for (int idx = 0; idx < emptyCount; idx++) {
            int targetSlot = emptySlots[idx];
            dev.tako.papersdelight.recipe.IngredientDef ingredient = slotIngredients[idx];

            int sameTypeRemaining = 0;
            for (int j = idx; j < emptyCount; j++) {
                if (ingredientsSameType(ingredient, slotIngredients[j])) {
                    sameTypeRemaining++;
                }
            }

            for (int i = 0; i < playerInv.getSize(); i++) {
                ItemStack stack = playerInv.getItem(i);
                if (stack == null || stack.isEmpty()) continue;
                if (recipeManager.matchesIngredient(stack, ingredient)) {

                    int total = stack.getAmount();
                    int share = (int) Math.ceil((double) total / sameTypeRemaining);
                    share = Math.min(share, total);

                    ItemStack portion = stack.clone();
                    portion.setAmount(share);
                    top.setItem(targetSlot, portion);

                    stack.setAmount(total - share);
                    if (stack.getAmount() <= 0) playerInv.setItem(i, null);
                    break;
                }
            }
        }

        cookingPotManager.syncFromInventory(potBlock, top);
        player.updateInventory();
    }

    private static boolean ingredientsSameType(dev.tako.papersdelight.recipe.IngredientDef a,
                                               dev.tako.papersdelight.recipe.IngredientDef b) {
        if (a == b) return true;

        if (a.material() != null && a.material().equals(b.material())) return true;
        if (a.tag() != null && a.tag().equals(b.tag())) return true;
        if (a.ceItem() != null && a.ceItem().equals(b.ceItem())) return true;

        if (!a.anyOf().isEmpty() && a.anyOf().equals(b.anyOf())) return true;
        return false;
    }

    private void syncAndReopen(Player player, Block block, Runnable action) {
        Inventory top = player.getOpenInventory().getTopInventory();
        cookingPotManager.syncFromInventory(block, top);
        action.run();
    }

    private void startTagAnimation(Player player, Inventory inv, CookingRecipe recipe, int[] ingredientSlots) {
        cancelTagAnimation(player);

        List<int[]> animSlots = new ArrayList<>();
        List<List<ItemStack>> animItems = new ArrayList<>();

        for (int i = 0; i < recipe.ingredients.size() && i < ingredientSlots.length; i++) {
            IngredientDef ing = recipe.ingredients.get(i);
            List<ItemStack> stacks = buildAnimationStacks(ing);
            if (stacks != null && stacks.size() > 1) {
                animSlots.add(new int[]{ingredientSlots[i]});
                animItems.add(stacks);
            }
        }

        if (animSlots.isEmpty()) return;

        int[] counter = {0};
        CCTask task = SCHEDULER.getEntityScheduler().runTaskTimer(plugin, player, st -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
                cancelTagAnimation(player);
                return;
            }
            counter[0]++;
            for (int i = 0; i < animSlots.size(); i++) {
                int slot = animSlots.get(i)[0];
                List<ItemStack> items = animItems.get(i);
                int idx = counter[0] % items.size();
                inv.setItem(slot, items.get(idx));
            }
        }, tagCycleIntervalTicks, tagCycleIntervalTicks);
        tagAnimationTasks.put(player.getUniqueId(), task);
    }

    private List<ItemStack> buildAnimationStacks(IngredientDef ing) {

        if (ing.tag() != null && !ing.tag().isBlank()) {
            List<String> tagItemIds = RecipeManager.getTagItems(ing.tag());
            if (tagItemIds.size() <= 1) return null;
            List<ItemStack> stacks = new ArrayList<>();
            List<String> loreLines = new ArrayList<>();
            loreLines.add("<!i><gray>#" + ing.tag());
            loreLines.add(ConfigManager.getOr("cooking_pot_recipe_book_matching_items_lore", "<!i><gray>匹配以下物品:"));
            for (String tid : tagItemIds) {
                loreLines.add("<gray>" + localizeItemId(tid));
            }
            for (String id : tagItemIds) {
                ItemStack item = CraftEngineUtil.createItem(id, 1);
                if (item != null && !item.isEmpty()) {
                    ItemMeta meta = item.getItemMeta();
                    ItemMetaUtil.setLore(meta, loreLines);
                    item.setItemMeta(meta);
                    stacks.add(item);
                }
            }
            return stacks.size() > 1 ? stacks : null;
        }

        List<String> expandedIds = expandIngredientExpressions(ing.displayExpressions());
        if (expandedIds.size() <= 1) return null;

        List<ItemStack> stacks = new ArrayList<>();
        List<String> loreLines = new ArrayList<>();
        loreLines.add(ConfigManager.getOr("cooking_pot_recipe_book_multi_select_ingredients_lore", "<!i><gray>多选原料:"));
        for (String id : expandedIds) {
            loreLines.add("<gray>" + localizeItemId(id));
        }
        for (String id : expandedIds) {
            ItemStack item = CraftEngineUtil.createItem(id, 1);
            if (item != null && !item.isEmpty()) {
                ItemMeta meta = item.getItemMeta();
                ItemMetaUtil.setLore(meta, loreLines);
                item.setItemMeta(meta);
                stacks.add(item);
            }
        }
        return stacks.size() > 1 ? stacks : null;
    }

    private void cancelTagAnimation(Player player) {
        CCTask task = tagAnimationTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    private boolean isIngredientSlot(int slot) {
        for (int s : CookingPotLayout.INGREDIENTS) {
            if (s == slot) return true;
        }
        return false;
    }

    private boolean isCookingPot(Block block) {
        return CookingPotBlockBehavior.isCookingPot(block);
    }

    private ItemStack manualShiftIntoUpperSlots(Inventory inv, ItemStack moving) {
        ItemStack remainder = moving.clone();

        int[] targetSlots;
        if (moving.getType() == Material.BOWL) {
            targetSlots = new int[]{CookingPotLayout.UTENSIL};
        } else {
            targetSlots = CookingPotLayout.INGREDIENTS;
        }

        for (int slot : targetSlots) {
            if (remainder.isEmpty()) break;
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.isEmpty() || !existing.isSimilar(remainder)) continue;
            int maxStack = Math.min(existing.getMaxStackSize(), 64);
            int room = maxStack - existing.getAmount();
            if (room <= 0) continue;
            int moved = Math.min(room, remainder.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            inv.setItem(slot, existing);
            remainder.setAmount(remainder.getAmount() - moved);
        }

        for (int slot : targetSlots) {
            if (remainder.isEmpty()) break;
            ItemStack existing = inv.getItem(slot);
            if (existing != null && !existing.isEmpty()) continue;
            int moved = Math.min(remainder.getMaxStackSize(), remainder.getAmount());
            ItemStack placed = remainder.clone();
            placed.setAmount(moved);
            inv.setItem(slot, placed);
            remainder.setAmount(remainder.getAmount() - moved);
        }

        return remainder.getAmount() <= 0 ? ItemStack.empty() : remainder;
    }

    private ItemStack createRecipeIcon(CookingRecipe recipe, int index) {
        ItemStack icon = createResultIcon(recipe);
        ItemMeta meta = icon.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("<!i><dark_gray>#" + (index + 1) + "<i><gray> " + recipe.result + " x" + Math.max(1, recipe.resultCount));
        lore.add(ConfigManager.getOr("cooking_pot_recipe_book_recipe_ingredients_lore", "<!i><white>原料<gray>:"));
        for (IngredientDef ingredient : recipe.ingredients) {
            lore.addAll(ingredientMiniMessageLines(ingredient));
        }
        if (recipe.container != null && !recipe.container.isBlank()) {
            lore.add(ConfigManager.getOr("cooking_pot_recipe_book_recipe_container_lore", "<gray>容器: <white>%container%")
                    .replace("%container%", localizeItemId(recipe.container)));
        } else {
            lore.add(ConfigManager.getOr("cooking_pot_recipe_book_recipe_no_container_lore", "<gray>容器: <white>无"));
        }
        lore.add(ConfigManager.getOr("cooking_pot_recipe_book_recipe_time_lore", "<gray>耗时: <white>%time% 秒")
                .replace("%time%", SECONDS.format(recipe.cookingTime / 20.0)));
        lore.add(ConfigManager.getOr("cooking_pot_recipe_book_recipe_experience_lore", "<gray>经验: <white>%experience%")
                .replace("%experience%", SECONDS.format(recipe.experience)));
        lore.add(ConfigManager.getOr("cooking_pot_recipe_book_recipe_heat_requirement_lore", "<gray>条件: <white>厨锅下方需要热源"));
        lore.add(ConfigManager.getOr("cooking_pot_recipe_book_recipe_view_detail_lore", "<!i><yellow><b>点击查看详情"));
        ItemMetaUtil.setLore(meta, lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createResultIcon(CookingRecipe recipe) {
        ItemStack result = CraftEngineUtil.createItem(recipe.result, Math.max(1, recipe.resultCount));
        if (result != null && !result.isEmpty()) return result;
        return createSimpleItem(Material.BARRIER,
                ConfigManager.getOr("cooking_pot_recipe_book_unknown_result_name", "§c未知成品"),
                List.of("§7" + recipe.result));
    }

    private ItemStack createIngredientIcon(IngredientDef ingredient) {

        boolean matcherOnly = ingredient.anyOf().isEmpty()
                && ingredient.material() == null
                && (ingredient.tag() == null || ingredient.tag().isBlank())
                && (ingredient.ceItem() == null || ingredient.ceItem().isBlank());
        if (matcherOnly) {
            List<String> expressions = ingredient.displayExpressions();
            List<String> expandedIds = expandIngredientExpressions(expressions);
            for (String id : expandedIds) {
                ItemStack item = CraftEngineUtil.createItem(id, 1);
                if (item != null && !item.isEmpty()) return item;
            }
            return buildGuiItem("recipe_book.icons.ingredient_any",
                    "cooking_pot_recipe_book_detail_icon_ingredient_any",
                    ConfigManager.getOr("cooking_pot_recipe_book_multi_select_ingredient_name", "<!i><white>多选原料"), expressions.stream()
                            .map(id -> "<!i><gray>" + localizeItemId(id))
                            .toList());
        }

        if (!ingredient.anyOf().isEmpty()) {

            List<String> expandedIds = expandIngredientExpressions(ingredient.anyOf());

            for (String id : expandedIds) {
                ItemStack item = CraftEngineUtil.createItem(id, 1);
                if (item != null && !item.isEmpty()) return item;
            }
            return buildGuiItem("recipe_book.icons.ingredient_any",
                    "cooking_pot_recipe_book_detail_icon_ingredient_any",
                    ConfigManager.getOr("cooking_pot_recipe_book_multi_select_ingredient_name", "<!i><white>多选原料"), ingredient.anyOf().stream()
                            .map(id -> id.startsWith("#")
                                    ? RecipeManager.getTagItems(id.substring(1)).stream()
                                            .map(tid -> "<!i><gray>" + localizeItemId(tid))
                                            .collect(java.util.stream.Collectors.joining(" <!i>/<i> "))
                                    : "<!i><gray>" + localizeItemId(id))
                            .toList());
        }
        if (ingredient.material() != null) {
            return new ItemStack(ingredient.material());
        }
        if (ingredient.ceItem() != null && !ingredient.ceItem().isBlank()) {
            ItemStack item = CraftEngineUtil.createItem(ingredient.ceItem(), 1);
            if (item != null && !item.isEmpty()) return item;
            return createSimpleItem(Material.PAPER, "§f" + ingredient.ceItem(),
                    List.of(ConfigManager.getOr("cooking_pot_recipe_book_craftengine_item_lore", "§7CraftEngine 物品")));
        }
        if (ingredient.tag() != null && !ingredient.tag().isBlank()) {
            List<String> tagItems = RecipeManager.getTagItems(ingredient.tag());

            if (tagItems.size() == 1) {
                ItemStack item = CraftEngineUtil.createItem(tagItems.get(0), 1);
                if (item != null && !item.isEmpty()) return item;
            }

            if (!tagItems.isEmpty()) {
                ItemStack item = CraftEngineUtil.createItem(tagItems.get(0), 1);
                if (item != null && !item.isEmpty()) {
                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = new ArrayList<>();
                    lore.add("<!i><gray>#" + ingredient.tag());
                    lore.add(ConfigManager.getOr("cooking_pot_recipe_book_matching_items_lore", "<!i><gray>匹配以下物品:"));
                    for (String itemId : tagItems) {
                        lore.add("<gray>" + localizeItemId(itemId));
                    }
                    ItemMetaUtil.setLore(meta, lore);
                    item.setItemMeta(meta);
                    return item;
                }
            }

            List<String> lore = new ArrayList<>();
            lore.add(ConfigManager.getOr("cooking_pot_recipe_book_matching_items_lore", "<!i><gray>匹配以下物品:"));
            for (String itemId : tagItems) {
                lore.add("<gray>" + localizeItemId(itemId));
            }
            return createSimpleItem(Material.NAME_TAG, "§f#" + ingredient.tag(), lore);
        }
        return buildGuiItem("recipe_book.icons.unknown",
                null, ConfigManager.getOr("cooking_pot_recipe_book_unknown_ingredient_name", "§7未知原料"), List.of());
    }

    private List<String> expandIngredientExpressions(List<String> expressions) {
        List<String> expanded = new ArrayList<>();
        for (String expression : expressions) {
            if (expression.startsWith("#")) {
                String tagName = expression.substring(1);
                List<String> tagItems = RecipeManager.getTagItems(tagName);
                expanded.addAll(tagItems.isEmpty() ? expandCeOrBukkitTag(tagName) : tagItems);
            } else if (expression.startsWith("advtag:")) {
                expanded.addAll(dev.tako.papersdelight.registration.config.AdvancedTagParser
                        .resolve(expression.substring("advtag:".length())));
            } else {
                expanded.add(expression);
            }
        }
        return expanded;
    }

    private List<String> expandCeOrBukkitTag(String tagName) {
        List<String> expanded = new ArrayList<>();
        try {
            var ceKey = net.momirealms.craftengine.core.util.Key.of(tagName);
            for (var key : net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems().keySet()) {
                var definition = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(key);
                if (definition != null && definition.is(ceKey)) expanded.add(key.toString());
            }
        } catch (Throwable ignored) {
        }
        if (!expanded.isEmpty()) return expanded;

        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(tagName);
            if (key != null) {
                org.bukkit.Tag<Material> tag = Bukkit.getTag(org.bukkit.Tag.REGISTRY_ITEMS, key, Material.class);
                if (tag != null) {
                    for (Material material : tag.getValues()) {
                        expanded.add(material.getKey().toString());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return expanded;
    }

    private ItemStack createIdIcon(String id, String namePrefix, List<String> extraLore) {
        ItemStack item = CraftEngineUtil.createItem(id, 1);
        if (item == null || item.isEmpty()) item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        String translationKey = getItemTranslationKey(item);
        ItemMetaUtil.setDisplayName(meta, namePrefix + " <white><lang:" + translationKey + ">");
        List<String> lore = new ArrayList<>();
        lore.add("§8" + id);
        if (extraLore != null) lore.addAll(extraLore);
        ItemMetaUtil.setLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private String getItemTranslationKey(ItemStack stack) {
        var ceDef = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byItemStack(stack);
        if (ceDef != null) return ceDef.translationKey();
        return stack.getType().translationKey();
    }

    private ItemStack buildRecipeInfoIcon(CookingRecipe recipe) {
        String name = ConfigManager.getOr("cooking_pot_recipe_book_detail_button_cook_info_name", "<!i><light_purple>烹饪信息");
        List<String> defaultLore = List.of(
                ConfigManager.getOr("cooking_pot_recipe_book_cook_info_time_lore", "<!i><gray>耗时: <white>%time% 秒")
                        .replace("%time%", SECONDS.format(recipe.cookingTime / 20.0)),
                ConfigManager.getOr("cooking_pot_recipe_book_cook_info_experience_lore", "<!i><gray>经验: <white>%experience%")
                        .replace("%experience%", SECONDS.format(recipe.experience)),
                ConfigManager.getOr("cooking_pot_recipe_book_cook_info_heat_requirement_lore", "<!i><gray>条件: <white>厨锅下方需要热源"));
        List<String> langLore = ConfigManager.getList("cooking_pot_recipe_book_detail_button_cook_info_lore");
        List<String> lore = langLore.isEmpty() ? defaultLore : replaceLorePlaceholders(langLore, recipe);

        return buildGuiItem("recipe_book.expanded.detail.buttons.cook_info",
                null, name, lore);
    }

    private List<String> replaceLorePlaceholders(List<String> lore, CookingRecipe recipe) {
        return lore.stream()
                .map(s -> s.replace("%time%", SECONDS.format(recipe.cookingTime / 20.0)))
                .map(s -> s.replace("%exp%", SECONDS.format(recipe.experience)))
                .toList();
    }

    private ItemStack buildGuiItem(String guiPath, String langPath,
                                   String defaultName, List<String> defaultLore) {
        if (langPath != null) {
            return ConfigManager.buildGuiItem(guiPath, langPath, defaultName, defaultLore);
        }

        ItemStack item = ConfigManager.buildIconFromConfig(guiPath);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setDisplayName(meta, defaultName);
            if (defaultLore != null && !defaultLore.isEmpty()) {
                ItemMetaUtil.setLore(meta, defaultLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack applyPlaceholders(ItemStack item, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) return item;
        ItemMeta meta = item.getItemMeta();
        Component name = meta.displayName();
        if (name != null) {
            meta.displayName(applyPlaceholders(name, placeholders));
        }
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> applyPlaceholders(line, placeholders))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component applyPlaceholders(Component component, Map<String, String> placeholders) {
        Component result = component;
        for (var entry : placeholders.entrySet()) {
            result = result.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(entry.getKey())
                    .replacement(entry.getValue())
                    .build());
        }
        return result;
    }

    private ItemStack createSimpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        ItemMetaUtil.setDisplayName(meta, name);
        if (lore != null && !lore.isEmpty()) {
            ItemMetaUtil.setLore(meta, lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<String> ingredientMiniMessageLines(IngredientDef ingredient) {
        List<String> ids = collectLocalizedIds(ingredient);
        if (ids.isEmpty()) return List.of(ConfigManager.getOr(
                "cooking_pot_recipe_book_unknown_ingredient_list_lore", "<i><gray>- 未知原料"));

        List<String> lines = new ArrayList<>();
        sbAppendLine(lines, ids);
        return lines;
    }

    private void sbAppendLine(List<String> lines, List<String> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("<i><gray>- ");

        int perLine = 4;
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0 && i % perLine == 0) {
                sb.append(" <!i>/<i>");
                lines.add(sb.toString());
                sb = new StringBuilder();
                sb.append("<i><gray>  ");
            } else if (i > 0) {
                sb.append(" <!i>/<i> ");
            }
            sb.append("<i><gray>").append(localizeItemId(ids.get(i)));
        }
        lines.add(sb.toString());
    }

    private List<String> collectLocalizedIds(IngredientDef ingredient) {
        List<String> expressions = ingredient.displayExpressions();
        if (!expressions.isEmpty()) return expandIngredientExpressions(expressions);
        return List.of();
    }

    private String ingredientMiniMessage(IngredientDef ingredient) {
        if (ingredient.material() != null) {
            return "<i><gray><lang:" + ingredient.material().translationKey() + ">";
        }
        List<String> ids = collectLocalizedIds(ingredient);
        if (!ids.isEmpty()) {
            return ids.stream()
                    .map(id -> "<i><gray>" + localizeItemId(id))
                    .collect(java.util.stream.Collectors.joining(" <!i>/<i> "));
        }
        return ConfigManager.getOr("cooking_pot_recipe_book_unknown_ingredient_inline_lore", "<i><gray>未知原料");
    }

    private String localizeItemId(String id) {
        var def = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(id);
        if (def != null) return "<lang:" + def.translationKey() + ">";
        String[] parts = id.split(":", 2);
        if (parts.length == 2 && "minecraft".equals(parts[0])) {
            try {
                Material mat = Material.valueOf(parts[1].toUpperCase());
                return "<lang:" + mat.translationKey() + ">";
            } catch (IllegalArgumentException ignored) {}
        }
        return id;
    }
}
