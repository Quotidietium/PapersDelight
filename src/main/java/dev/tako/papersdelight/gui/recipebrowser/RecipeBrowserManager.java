package dev.tako.papersdelight.gui.recipebrowser;

import dev.tako.papersdelight.config.ConfigManager;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.cookingpot.CookingPotLayout;
import dev.tako.papersdelight.mechanic.cutting.CuttingBoardManager;
import dev.tako.papersdelight.mechanic.cutting.CuttingRecipe;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.recipe.CustomRecipe;
import dev.tako.papersdelight.recipe.CustomRecipeManager;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.util.ItemMetaUtil;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeBrowserManager implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    final JavaPlugin plugin;
    private final RecipeManager recipeManager;
    private final CuttingBoardManager cuttingBoardManager;
    private final CustomRecipeManager customRecipeManager;

    private static final int HOME_SIZE = 27;
    private static final int LIST_SIZE = 54;
    private static final int DETAIL_SIZE = 27;
    private static final int ITEMS_PER_PAGE = 45;

    private static final int NAV_BACK = 45;
    private static final int NAV_PREV = 48;
    private static final int NAV_PAGE_INFO = 49;
    private static final int NAV_NEXT = 50;

    private static final int DETAIL_BACK = 18;

    private final Map<UUID, CCTask> animationTasks = new ConcurrentHashMap<>();

    private final Map<UUID, CCTask> tagAnimTasks = new ConcurrentHashMap<>();

    private final Map<String, List<String>> tagExpansionCache = new ConcurrentHashMap<>();

    public RecipeBrowserManager(JavaPlugin plugin, RecipeManager recipeManager,
                               CuttingBoardManager cuttingBoardManager, CustomRecipeManager customRecipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.cuttingBoardManager = cuttingBoardManager;
        this.customRecipeManager = customRecipeManager;
    }

    private static String browserTitle(String image, String langKey, String fallback) {
        return "<shift:-8><white><image:farmersdelight:" + image + "></white><shift:-168><reset>"
                + ConfigManager.getOr(langKey, fallback);
    }

    public void openHome(Player player) {
        RecipeBrowserHolder holder = RecipeBrowserHolder.home();
        Inventory inv = Bukkit.createInventory(holder, HOME_SIZE,
                TextUtil.parse(browserTitle("recipe_command_home",
                        "recipe_browser_home_title", "配方浏览器")));
        holder.inventory = inv;

        ItemStack border = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border");
        for (int i = 0; i < HOME_SIZE; i++) inv.setItem(i, border);

        inv.setItem(11, buildCategoryIcon("recipe_browser.buttons.cooking_pot",

                "<!i><white><lang:jei.farmersdelight.cooking>",
                ConfigManager.getList("recipe_browser_cooking_pot_lore")));
        inv.setItem(13, buildCategoryIcon("recipe_browser.buttons.cutting_board",

                "<!i><white><lang:jei.farmersdelight.cutting>",
                ConfigManager.getList("recipe_browser_cutting_board_lore")));
        inv.setItem(15, buildCategoryIcon("recipe_browser.buttons.misc",
                ConfigManager.getOr("recipe_browser_misc_name", "<!i><white>信息"),
                ConfigManager.getList("recipe_browser_misc_lore")));

        player.openInventory(inv);
    }

    private void openCookingList(Player player, int page) {
        List<CookingRecipe> recipes = recipeManager.recipes();
        int maxPage = Math.max(0, (recipes.size() - 1) / ITEMS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, maxPage));

        RecipeBrowserHolder holder = RecipeBrowserHolder.list(BrowserPage.COOKING_LIST, safePage);
        Inventory inv = Bukkit.createInventory(holder, LIST_SIZE,
                TextUtil.parse(browserTitle("recipe_command_list",
                        "recipe_browser_cooking_list_title", "厨锅配方")));
        holder.inventory = inv;
        renderList(inv, recipes.size(), safePage, maxPage, i -> createCookingRecipeIcon(recipes.get(i)));
        player.openInventory(inv);
    }

    private void openCookingDetail(Player player, int listPage, int recipeIndex) {
        List<CookingRecipe> recipes = recipeManager.recipes();
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) { openCookingList(player, listPage); return; }

        CookingRecipe recipe = recipes.get(recipeIndex);
        RecipeBrowserHolder holder = RecipeBrowserHolder.detail(BrowserPage.COOKING_DETAIL, listPage, recipeIndex);
        Inventory inv = Bukkit.createInventory(holder, DETAIL_SIZE,
                TextUtil.parse(browserTitle("recipe_command_cooking_pot",
                        "recipe_browser_cooking_detail_title", "厨锅配方详情")));
        holder.inventory = inv;
        renderCookingDetail(inv, recipe);
        player.openInventory(inv);
        startCookingAnimation(player, inv, recipe);
        startIngredientTagAnimation(player, inv, recipe);
    }

    private void openCuttingList(Player player, int page) {
        List<CuttingRecipe> recipes = cuttingBoardManager.getRecipes();
        int maxPage = Math.max(0, (recipes.size() - 1) / ITEMS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, maxPage));

        RecipeBrowserHolder holder = RecipeBrowserHolder.list(BrowserPage.CUTTING_LIST, safePage);
        Inventory inv = Bukkit.createInventory(holder, LIST_SIZE,
                TextUtil.parse(browserTitle("recipe_command_list",
                        "recipe_browser_cutting_list_title", "砧板配方")));
        holder.inventory = inv;
        renderList(inv, recipes.size(), safePage, maxPage, i -> createCuttingRecipeIcon(recipes.get(i)));
        player.openInventory(inv);
    }

    private void openCuttingDetail(Player player, int listPage, int recipeIndex) {
        List<CuttingRecipe> recipes = cuttingBoardManager.getRecipes();
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) { openCuttingList(player, listPage); return; }

        RecipeBrowserHolder holder = RecipeBrowserHolder.detail(BrowserPage.CUTTING_DETAIL, listPage, recipeIndex);
        Inventory inv = Bukkit.createInventory(holder, DETAIL_SIZE,
                TextUtil.parse(browserTitle("recipe_command_cutting_board",
                        "recipe_browser_cutting_detail_title", "砧板配方详情")));
        holder.inventory = inv;
        renderCuttingDetail(inv, recipes.get(recipeIndex));
        player.openInventory(inv);
    }

    @FunctionalInterface
    private interface IconFactory {
        ItemStack create(int index);
    }

    private void renderList(Inventory inv, int totalRecipes, int page, int maxPage, IconFactory factory) {
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(totalRecipes, start + ITEMS_PER_PAGE);

        if (totalRecipes == 0) {

            ItemStack hint = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border_tooltip");
            ItemMeta meta = hint.getItemMeta();
            if (meta != null) {
                ItemMetaUtil.setDisplayName(meta, ConfigManager.getOr(
                        "recipe_browser_empty_list_name", "<!i><red>暂无配方"));
                hint.setItemMeta(meta);
            }
            inv.setItem(22, hint);
        } else {
            for (int i = start; i < end; i++) {
                inv.setItem(i - start, factory.create(i));
            }
        }

        ItemStack border = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border");
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        inv.setItem(NAV_BACK, buildNavIcon("recipe_browser.buttons.back",
                ConfigManager.getOr("recipe_browser_nav_back_home_name", "<!i><green>返回主页"), List.of()));

        boolean isFirstPage = page <= 0;
        inv.setItem(NAV_PREV, buildNavIcon("recipe_browser.buttons.previous_page",
                ConfigManager.getOr(isFirstPage ? "recipe_browser_nav_previous_disabled_name"
                                : "recipe_browser_nav_previous_name",
                        isFirstPage ? "<!i><gray>上一页" : "<!i><green>上一页"),
                isFirstPage
                        ? List.of(ConfigManager.getOr("recipe_browser_nav_previous_disabled_lore", "<!i><gray>已经是第一页了"))
                        : List.of(ConfigManager.getOr("recipe_browser_nav_previous_lore", "<!i><gray>点击查看第 <yellow>%page% <gray>页")
                                .replace("%page%", String.valueOf(page)))));

        inv.setItem(NAV_PAGE_INFO, buildPageInfo(page, maxPage, totalRecipes));

        boolean isLastPage = page >= maxPage;
        inv.setItem(NAV_NEXT, buildNavIcon("recipe_browser.buttons.next_page",
                ConfigManager.getOr(isLastPage ? "recipe_browser_nav_next_disabled_name"
                                : "recipe_browser_nav_next_name",
                        isLastPage ? "<!i><gray>下一页" : "<!i><green>下一页"),
                isLastPage
                        ? List.of(ConfigManager.getOr("recipe_browser_nav_next_disabled_lore", "<!i><gray>已经是最后一页了"))
                        : List.of(ConfigManager.getOr("recipe_browser_nav_next_lore", "<!i><gray>点击查看第 <yellow>%page% <gray>页")
                                .replace("%page%", String.valueOf(page + 2)))));
    }

    private void renderCookingDetail(Inventory inv, CookingRecipe recipe) {
        ItemStack border = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border");
        for (int i = 0; i < DETAIL_SIZE; i++) inv.setItem(i, border);

        inv.setItem(DETAIL_BACK, buildNavIcon("recipe_browser.buttons.back",
                ConfigManager.getOr("recipe_browser_nav_back_list_name", "<!i><green>返回列表"), List.of()));

        for (int i = 0; i < recipe.ingredients.size() && i < 6; i++) {
            inv.setItem(CookingPotLayout.INGREDIENTS[i], createIngredientIcon(recipe.ingredients.get(i)));
        }

        inv.setItem(CookingPotLayout.STATUS, ConfigManager.buildIconFromConfig("cooking_pot.buttons.heat_indicator.heated"));

        ItemStack resultItem = safeCreateItem(recipe.result, recipe.resultCount);
        inv.setItem(CookingPotLayout.WAITING_OUTPUT, resultItem.clone());
        inv.setItem(CookingPotLayout.FINAL_OUTPUT, resultItem);

        if (recipe.container != null && !recipe.container.isBlank()) {
            inv.setItem(CookingPotLayout.UTENSIL, safeCreateItem(recipe.container, 1));
        }
    }

    private void renderCuttingDetail(Inventory inv, CuttingRecipe recipe) {
        ItemStack border = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border");
        for (int i = 0; i < DETAIL_SIZE; i++) inv.setItem(i, border);

        inv.setItem(DETAIL_BACK, buildNavIcon("recipe_browser.buttons.back",
                ConfigManager.getOr("recipe_browser_nav_back_list_name", "<!i><green>返回列表"), List.of()));

        if (!recipe.tools().isEmpty()) {
            inv.setItem(2, resolveIdOrTag(recipe.tools().get(0)));
        }

        inv.setItem(11, resolveIdOrTag(recipe.input()));

        int[] outputSlots = {6, 7, 15, 16};
        int slotIdx = 0;
        for (var result : recipe.results()) {
            if (slotIdx >= outputSlots.length) break;
            ItemStack icon = safeCreateItem(result.item(), result.count());
            if (result.chance() < 1.0) {
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    List<net.kyori.adventure.text.Component> existingLore = meta.lore();
                    List<net.kyori.adventure.text.Component> newLore = existingLore != null
                            ? new ArrayList<>(existingLore) : new ArrayList<>();
                    newLore.add(TextUtil.parse(ConfigManager.getOr(
                            "recipe_browser_cutting_result_chance_lore", "<!i><yellow>%chance%% 概率")
                            .replace("%chance%", String.valueOf(Math.round(result.chance() * 100)))));
                    meta.lore(newLore);
                    icon.setItemMeta(meta);
                }
            }
            inv.setItem(outputSlots[slotIdx++], icon);
        }
    }

    private ItemStack resolveIdOrTag(String id) {
        if (id == null || id.isEmpty()) return new ItemStack(Material.BARRIER);
        if (id.startsWith("#")) {
            for (String itemId : expandTag(id.substring(1))) {
                ItemStack item = CraftEngineUtil.createItem(itemId, 1);
                if (item != null && !item.isEmpty()) return item;
            }
            return new ItemStack(Material.BARRIER);
        }
        return safeCreateItem(id, 1);
    }

    static List<String> cachedTagExpansion(Map<String, List<String>> cache, String key,
                                            Supplier<List<String>> resolver) {
        return cache.computeIfAbsent(key, ignored -> List.copyOf(resolver.get()));
    }

    public void clearTagExpansionCache() {
        tagExpansionCache.clear();
    }

    private List<String> expandTag(String tagName) {
        String cacheKey = tagName.toLowerCase(Locale.ROOT);
        return cachedTagExpansion(tagExpansionCache, cacheKey, () -> {
            List<String> customItems = RecipeManager.getTagItems(tagName);
            return customItems.isEmpty() ? expandCeOrBukkitTag(tagName) : customItems;
        });
    }

    private void startCookingAnimation(Player player, Inventory inv, CookingRecipe recipe) {
        stopAnimation(player);
        final int[] frame = {-1};
        final int totalProgressFrames = 22;

        long ticksPerFrame = Math.max(2L, recipe.cookingTime / totalProgressFrames);
        String timeName = ConfigManager.getOr("recipe_browser_cooking_time_name", "<!i><white>%time%秒")
                .replace("%time%", String.valueOf(Math.round(recipe.cookingTime / 20.0)));
        String expLore = ConfigManager.getOr("recipe_browser_cooking_experience_lore", "<!i><white>%experience%点经验")
                .replace("%experience%", String.format("%.1f", recipe.experience));

        inv.setItem(CookingPotLayout.PROGRESS[0], buildProgressIcon(0, timeName, expLore));

        CCTask task = SCHEDULER.getEntityScheduler().runTaskTimer(plugin, player, st -> {
            if (!player.isOnline()) { stopAnimation(player); return; }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeBrowserHolder h)
                    || h.page != BrowserPage.COOKING_DETAIL) {
                stopAnimation(player);
                return;
            }
            frame[0] = (frame[0] + 1) % (totalProgressFrames + 1);
            inv.setItem(CookingPotLayout.PROGRESS[0], buildProgressIcon(frame[0], timeName, expLore));
        }, ticksPerFrame, ticksPerFrame);
        animationTasks.put(player.getUniqueId(), task);
    }

    private ItemStack buildProgressIcon(int frame, String timeName, String expLore) {
        ItemStack icon;
        if (frame <= 0) {

            icon = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border_tooltip");
        } else {

            String ceId = "farmersdelight:cooking_progress_" + Math.min(frame, 22);
            icon = ConfigManager.parseIconString("ce:" + ceId);
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setDisplayName(meta, timeName);
            ItemMetaUtil.setLore(meta, List.of(expLore));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void stopAnimation(Player player) {
        CCTask task = animationTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        CCTask tagTask = tagAnimTasks.remove(player.getUniqueId());
        if (tagTask != null) tagTask.cancel();
    }

    private void startIngredientTagAnimation(Player player, Inventory inv, CookingRecipe recipe) {
        List<int[]> tagSlotFrames = new ArrayList<>();
        List<List<ItemStack>> tagItemLists = new ArrayList<>();

        for (int i = 0; i < recipe.ingredients.size() && i < 6; i++) {
            var def = recipe.ingredients.get(i);
            List<String> itemIds = resolveIngredientToMultipleIds(def);
            if (itemIds.size() > 1) {
                List<ItemStack> items = new ArrayList<>();

                String tagLabel = def.tag() != null ? def.tag() : (def.anyOf().isEmpty() ? "" : ConfigManager.getOr(
                        "recipe_browser_multi_select_label", "多选"));
                List<String> loreParts = new ArrayList<>();
                loreParts.add("<!i><gray>#" + tagLabel);
                loreParts.add(ConfigManager.getOr("recipe_browser_matching_items_lore", "<!i><gray>匹配以下物品:"));
                for (String id : itemIds) {
                    loreParts.add("<gray>" + localizeItemId(id));
                }

                for (String id : itemIds) {
                    ItemStack item = CraftEngineUtil.createItem(id, 1);
                    if (item != null && !item.isEmpty()) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            ItemMetaUtil.setLore(meta, loreParts);
                            item.setItemMeta(meta);
                        }
                        items.add(item);
                    }
                }
                if (items.size() > 1) {
                    tagSlotFrames.add(new int[]{CookingPotLayout.INGREDIENTS[i], 0});
                    tagItemLists.add(items);
                }
            }
        }

        if (tagSlotFrames.isEmpty()) return;

        CCTask task = SCHEDULER.getEntityScheduler().runTaskTimer(plugin, player, st -> {
            if (!player.isOnline()) { stopAnimation(player); return; }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeBrowserHolder h)
                    || h.page != BrowserPage.COOKING_DETAIL) {
                stopAnimation(player);
                return;
            }
            for (int i = 0; i < tagSlotFrames.size(); i++) {
                int[] slotFrame = tagSlotFrames.get(i);
                List<ItemStack> items = tagItemLists.get(i);
                slotFrame[1] = (slotFrame[1] + 1) % items.size();
                inv.setItem(slotFrame[0], items.get(slotFrame[1]));
            }
        }, 20L, 20L);
        tagAnimTasks.put(player.getUniqueId(), task);
    }

    private List<String> resolveIngredientToMultipleIds(dev.tako.papersdelight.recipe.IngredientDef def) {
        List<String> result = new ArrayList<>();
        for (String id : def.displayExpressions()) {
            if (id.startsWith("#")) {
                result.addAll(expandTag(id.substring(1)));
            } else if (id.startsWith("advtag:")) {
                result.addAll(dev.tako.papersdelight.registration.config.AdvancedTagParser
                        .resolve(id.substring("advtag:".length())));
            } else {
                result.add(id);
            }
        }
        return result;
    }

    private List<String> expandCeOrBukkitTag(String tagName) {
        List<String> result = new ArrayList<>();

        try {

            var ceKeys = new ArrayList<net.momirealms.craftengine.core.util.Key>();
            ceKeys.add(net.momirealms.craftengine.core.util.Key.of(tagName));
            if (!tagName.contains(":")) {
                ceKeys.add(net.momirealms.craftengine.core.util.Key.ce(tagName));
            }
            for (var key : net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems().keySet()) {
                var def = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(key);
                if (def != null && ceKeys.stream().anyMatch(def::is)) {
                    result.add(key.toString());
                }
            }
        } catch (Throwable ignored) {}
        if (!result.isEmpty()) return result;

        try {
            org.bukkit.NamespacedKey nsk = org.bukkit.NamespacedKey.fromString(tagName);
            if (nsk != null) {
                org.bukkit.Tag<Material> tag = Bukkit.getTag(org.bukkit.Tag.REGISTRY_ITEMS, nsk, Material.class);
                if (tag != null) {
                    for (Material mat : tag.getValues()) {
                        result.add("minecraft:" + mat.name().toLowerCase());
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private String localizeItemId(String id) {
        try {
            var def = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(id);
            if (def != null) return "<lang:" + def.translationKey() + ">";
        } catch (Throwable ignored) {}
        String[] parts = id.split(":", 2);
        if (parts.length == 2 && "minecraft".equals(parts[0])) {

            Material mat = Material.matchMaterial(id);
            if (mat != null) {
                if (mat.isBlock()) {
                    return "<lang:block.minecraft." + parts[1] + ">";
                }
                return "<lang:item.minecraft." + parts[1] + ">";
            }

            return "<lang:item.minecraft." + parts[1] + ">";
        }
        return id;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecipeBrowserHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0) return;

        switch (holder.page) {
            case HOME -> handleHomeClick(player, slot);
            case COOKING_LIST -> handleListClick(player, holder, slot, BrowserPage.COOKING_LIST);
            case CUTTING_LIST -> handleListClick(player, holder, slot, BrowserPage.CUTTING_LIST);
            case MISC_LIST -> handleListClick(player, holder, slot, BrowserPage.MISC_LIST);
            case COOKING_DETAIL, CUTTING_DETAIL, COMPOST_DETAIL, SINGLE_DETAIL ->
                    handleDetailBack(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RecipeBrowserHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof RecipeBrowserHolder) {
            if (event.getPlayer() instanceof Player player) {
                stopAnimation(player);
            }
        }
    }

    private void handleHomeClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openCookingList(player, 0);
            case 13 -> openCuttingList(player, 0);
            case 15 -> openMiscList(player, 0);
        }
    }

    private void handleListClick(Player player, RecipeBrowserHolder holder, int slot, BrowserPage listType) {
        if (slot == NAV_BACK) { openHome(player); return; }
        if (slot == NAV_PREV && holder.listPage > 0) {
            openListByType(player, listType, holder.listPage - 1);
            return;
        }

        int totalRecipes = getRecipeCount(listType);
        int maxPage = Math.max(0, (totalRecipes - 1) / ITEMS_PER_PAGE);
        if (slot == NAV_NEXT && holder.listPage < maxPage) {
            openListByType(player, listType, holder.listPage + 1);
            return;
        }

        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int recipeIndex = holder.listPage * ITEMS_PER_PAGE + slot;
            if (listType == BrowserPage.MISC_LIST) {
                openMiscDetail(player, holder.listPage, recipeIndex);
                return;
            }
            if (recipeIndex < totalRecipes) {
                openDetailByType(player, listType, holder.listPage, recipeIndex);
            }
        }
    }

    private void handleDetailBack(Player player, RecipeBrowserHolder holder, int slot) {
        if (slot == DETAIL_BACK) {
            switch (holder.page) {
                case COOKING_DETAIL -> openCookingList(player, holder.listPage);
                case CUTTING_DETAIL -> openCuttingList(player, holder.listPage);
                case COMPOST_DETAIL, SINGLE_DETAIL -> openMiscList(player, holder.listPage);
                default -> openHome(player);
            }
        }
    }

    private void openMiscList(Player player, int page) {
        List<Object> allMisc = new ArrayList<>();
        allMisc.addAll(customRecipeManager.decompositions());
        allMisc.addAll(customRecipeManager.singles());

        int total = allMisc.size();
        int maxPage = Math.max(0, (total - 1) / ITEMS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, maxPage));

        RecipeBrowserHolder holder = RecipeBrowserHolder.list(BrowserPage.MISC_LIST, safePage);
        Inventory inv = Bukkit.createInventory(holder, LIST_SIZE,
                TextUtil.parse(browserTitle("recipe_command_list",
                        "recipe_browser_misc_list_title", "信息")));
        holder.inventory = inv;

        ItemStack border = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border");
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(NAV_BACK, buildNavIcon("recipe_browser.buttons.back",
                ConfigManager.getOr("recipe_browser_nav_back_home_name", "<!i><green>返回主页"), List.of()));
        inv.setItem(NAV_PREV, buildNavIcon("recipe_browser.buttons.previous_page",
                ConfigManager.getOr(safePage > 0 ? "recipe_browser_nav_previous_name"
                                : "recipe_browser_nav_previous_disabled_name",
                        safePage > 0 ? "<!i><green>上一页" : "<!i><gray>上一页"), List.of()));
        inv.setItem(NAV_PAGE_INFO, buildPageInfo(safePage, maxPage, total));
        inv.setItem(NAV_NEXT, buildNavIcon("recipe_browser.buttons.next_page",
                ConfigManager.getOr(safePage < maxPage ? "recipe_browser_nav_next_name"
                                : "recipe_browser_nav_next_disabled_name",
                        safePage < maxPage ? "<!i><green>下一页" : "<!i><gray>下一页"), List.of()));

        int start = safePage * ITEMS_PER_PAGE;
        int end = Math.min(total, start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            Object recipe = allMisc.get(i);
            ItemStack icon;
            if (recipe instanceof CustomRecipe.Decomposition d) {
                icon = safeCreateItem(d.output(), 1);
            } else if (recipe instanceof CustomRecipe.Single s) {
                icon = safeCreateItem(s.item(), 1);
            } else {
                icon = new ItemStack(Material.BARRIER);
            }
            inv.setItem(i - start, icon);
        }

        player.openInventory(inv);
    }

    private void openMiscDetail(Player player, int listPage, int recipeIndex) {
        List<Object> allMisc = new ArrayList<>();
        allMisc.addAll(customRecipeManager.decompositions());
        allMisc.addAll(customRecipeManager.singles());

        if (recipeIndex < 0 || recipeIndex >= allMisc.size()) { openMiscList(player, listPage); return; }

        Object recipe = allMisc.get(recipeIndex);
        if (recipe instanceof CustomRecipe.Decomposition d) {
            openDecompositionDetail(player, listPage, recipeIndex, d);
        } else if (recipe instanceof CustomRecipe.Single s) {
            openSingleDetail(player, listPage, recipeIndex, s);
        }
    }

    private void openDecompositionDetail(Player player, int listPage, int recipeIndex, CustomRecipe.Decomposition recipe) {
        RecipeBrowserHolder holder = RecipeBrowserHolder.detail(BrowserPage.COMPOST_DETAIL, listPage, recipeIndex);
        Inventory inv = Bukkit.createInventory(holder, DETAIL_SIZE,
                TextUtil.parse(browserTitle("recipe_command_decomposition",
                        "recipe_browser_compost_detail_title", "分解配方")));
        holder.inventory = inv;
        renderDecompositionDetail(inv, recipe, player);
        player.openInventory(inv);
        startCatalystAnimation(player, inv, recipe.catalysts());
    }

    private void openSingleDetail(Player player, int listPage, int recipeIndex, CustomRecipe.Single recipe) {
        RecipeBrowserHolder holder = RecipeBrowserHolder.detail(BrowserPage.SINGLE_DETAIL, listPage, recipeIndex);
        Inventory inv = Bukkit.createInventory(holder, DETAIL_SIZE,
                TextUtil.parse(browserTitle("recipe_command_single",
                        "recipe_browser_single_detail_title", "信息")));
        holder.inventory = inv;

        ItemStack border = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border");
        for (int i = 0; i < DETAIL_SIZE; i++) inv.setItem(i, border);

        inv.setItem(DETAIL_BACK, buildNavIcon("recipe_browser.buttons.back",
                ConfigManager.getOr("recipe_browser_nav_back_list_name", "<!i><green>返回列表"), List.of()));

        ItemStack icon = safeCreateItem(recipe.item(), 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setLore(meta, recipe.description());
            icon.setItemMeta(meta);
        }
        inv.setItem(13, icon);

        player.openInventory(inv);
    }

    private void renderDecompositionDetail(Inventory inv, CustomRecipe.Decomposition recipe, Player player) {
        ItemStack border = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border");
        for (int i = 0; i < DETAIL_SIZE; i++) inv.setItem(i, border);

        inv.setItem(DETAIL_BACK, buildNavIcon("recipe_browser.buttons.back",
                ConfigManager.getOr("recipe_browser_nav_back_list_name", "<!i><green>返回列表"), List.of()));
        inv.setItem(10, safeCreateItem(recipe.input(), 1));
        inv.setItem(16, safeCreateItem(recipe.output(), 1));

        if (!recipe.catalysts().isEmpty()) {
            inv.setItem(24, buildCatalystIcon(recipe.catalysts(), 0));
        }

        inv.setItem(21, buildInfoItem("<!i><lang:jei.farmersdelight.decomposition.light>"));
        inv.setItem(22, buildInfoItem("<!i><lang:jei.farmersdelight.decomposition.fluid>"));
        inv.setItem(23, buildInfoItem("<!i><lang:jei.farmersdelight.decomposition.accelerators>"));
    }

    private ItemStack buildInfoItem(String name) {
        ItemStack item = ConfigManager.buildIconFromConfig("recipe_browser.buttons.border_tooltip");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setDisplayName(meta, name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startCatalystAnimation(Player player, Inventory inv, List<String> catalysts) {
        if (catalysts.isEmpty()) return;
        stopAnimation(player);
        final int[] frame = {0};
        CCTask task = SCHEDULER.getEntityScheduler().runTaskTimer(plugin, player, st -> {
            if (!player.isOnline()) { stopAnimation(player); return; }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeBrowserHolder h)
                    || h.page != BrowserPage.COMPOST_DETAIL) {
                stopAnimation(player);
                return;
            }
            frame[0] = (frame[0] + 1) % catalysts.size();
            inv.setItem(24, buildCatalystIcon(catalysts, frame[0]));
        }, 20L, 20L);
        animationTasks.put(player.getUniqueId(), task);
    }

    private ItemStack buildCatalystIcon(List<String> catalysts, int index) {
        ItemStack icon = safeCreateItem(catalysts.get(index), 1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ConfigManager.getOr("recipe_browser_catalyst_list_lore", "<!i><gray>催化物列表:"));
            for (String id : catalysts) {
                lore.add("<gray> - " + localizeItemId(id));
            }
            ItemMetaUtil.setLore(meta, lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private void openListByType(Player player, BrowserPage listType, int page) {
        switch (listType) {
            case COOKING_LIST -> openCookingList(player, page);
            case CUTTING_LIST -> openCuttingList(player, page);
            case MISC_LIST -> openMiscList(player, page);
            default -> openHome(player);
        }
    }

    private void openDetailByType(Player player, BrowserPage listType, int listPage, int recipeIndex) {
        switch (listType) {
            case COOKING_LIST -> openCookingDetail(player, listPage, recipeIndex);
            case CUTTING_LIST -> openCuttingDetail(player, listPage, recipeIndex);
            default -> openHome(player);
        }
    }

    private int getRecipeCount(BrowserPage listType) {
        return switch (listType) {
            case COOKING_LIST -> recipeManager.recipes().size();
            case CUTTING_LIST -> cuttingBoardManager.getRecipes().size();
            case MISC_LIST -> customRecipeManager.count();
            default -> 0;
        };
    }

    private ItemStack createCookingRecipeIcon(CookingRecipe recipe) {
        return safeCreateItem(recipe.result, recipe.resultCount);
    }

    static String cuttingListIconSource(CuttingRecipe recipe) {
        return recipe.input();
    }

    private ItemStack createCuttingRecipeIcon(CuttingRecipe recipe) {
        return resolveIdOrTag(cuttingListIconSource(recipe));
    }

    private ItemStack buildCategoryIcon(String configPath, String name, List<String> lore) {
        ItemStack item = ConfigManager.buildIconFromConfig(configPath);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setDisplayName(meta, name);
            if (lore != null && !lore.isEmpty()) ItemMetaUtil.setLore(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildNavIcon(String configPath, String name, List<String> lore) {
        return buildCategoryIcon(configPath, name, lore);
    }

    private ItemStack buildPageInfo(int page, int maxPage, int totalRecipes) {
        ItemStack item = ConfigManager.buildIconFromConfig("recipe_browser.buttons.page_info");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setDisplayName(meta, ConfigManager.getOr(
                    "recipe_browser_page_info_name", "<!i><yellow>第 %current% / %total% 页")
                    .replace("%current%", String.valueOf(page + 1))
                    .replace("%total%", String.valueOf(maxPage + 1)));
            ItemMetaUtil.setLore(meta, List.of(ConfigManager.getOr(
                    "recipe_browser_page_info_lore", "<!i><gray>共 %count% 个配方")
                    .replace("%count%", String.valueOf(totalRecipes))));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack safeCreateItem(String id, int amount) {
        ItemStack item = CraftEngineUtil.createItem(id, amount);
        return item != null ? item : new ItemStack(Material.BARRIER);
    }

    private ItemStack createIngredientIcon(dev.tako.papersdelight.recipe.IngredientDef def) {

        for (String id : resolveIngredientToMultipleIds(def)) {
            ItemStack item = CraftEngineUtil.createItem(id, 1);
            if (item != null && !item.isEmpty()) return applyTagLore(item, def);
        }
        return new ItemStack(Material.BARRIER);
    }

    private ItemStack applyTagLore(ItemStack item, dev.tako.papersdelight.recipe.IngredientDef def) {
        List<String> allIds = resolveIngredientToMultipleIds(def);
        if (allIds.size() <= 1) return item;
        String tagLabel = def.tag() != null ? def.tag() : ConfigManager.getOr(
                "recipe_browser_multi_select_label", "多选");
        List<String> lore = new ArrayList<>();
        lore.add("<!i><gray>#" + tagLabel);
        lore.add(ConfigManager.getOr("recipe_browser_matching_items_lore", "<!i><gray>匹配以下物品:"));
        for (String id : allIds) {
            lore.add("<gray>" + localizeItemId(id));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setLore(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
