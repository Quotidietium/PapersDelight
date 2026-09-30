package dev.tako.papersdelight;

import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import dev.tako.papersdelight.command.PapersDelightCommand;
import dev.tako.papersdelight.compat.CraftEngineVersionGate;
import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.cookingpot.CookingPotManager;
import dev.tako.papersdelight.jug.JugRuntimeInstaller;
import dev.tako.papersdelight.jug.JugSupport;
import dev.tako.papersdelight.jug.JugItemModelGenerator;
import dev.tako.papersdelight.gui.MenuManager;
import dev.tako.papersdelight.api.menu.MenuService;
import dev.tako.papersdelight.api.menu.SimpleMenuModule;
import dev.tako.papersdelight.gui.module.cookingpot.CookingPotEventHandler;
import dev.tako.papersdelight.gui.module.cookingpot.CookingPotMenu;
import dev.tako.papersdelight.gui.module.cookingpot.CookingPotRecipeBook;
import dev.tako.papersdelight.mechanic.cutting.CuttingBoardManager;
import dev.tako.papersdelight.mechanic.skillet.ItemModelGenerator;
import dev.tako.papersdelight.mechanic.skillet.SkilletManager;
import dev.tako.papersdelight.mechanic.skewer.HandheldSkewerManager;
import dev.tako.papersdelight.mechanic.petfood.PetFoodListener;
import dev.tako.papersdelight.mechanic.nourishment.NourishmentManager;
import dev.tako.papersdelight.mechanic.basket.BasketManager;
import dev.tako.papersdelight.mechanic.stove.StoveManager;
import dev.tako.papersdelight.recipe.AdvancedTagService;
import dev.tako.papersdelight.recipe.CampfireRecipeUtil;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.heat.HeatSourceService;
import dev.tako.papersdelight.api.heat.HeatSourceGate;
import dev.tako.papersdelight.api.item.AdvancedTagGate;
import dev.tako.papersdelight.registration.CraftEngineBehaviorRegistrations;
import dev.tako.papersdelight.registration.CraftEngineConfigRegistrations;
import dev.tako.papersdelight.registration.RuntimeConfigHandoff;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.util.CraftEngineSerializationWarmup;
import dev.tako.papersdelight.registration.CraftEngineContextRegistrations;
import dev.tako.papersdelight.client.PapersDelightClient;
import org.bukkit.plugin.ServicePriority;

public final class PapersDelight {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    public static final PapersDelight INSTANCE = new PapersDelight();

    private PapersDelightClient plugin;
    private volatile boolean disable = false;

    private RecipeManager recipeManager;
    private dev.tako.papersdelight.recipe.CustomRecipeManager customRecipeManager;
    private CookingPotManager cookingPotManager;

    private Object jugManager;
    private CookingPotRecipeBook cookingPotRecipeBook;
    private CuttingBoardManager cuttingBoardManager;
    private SkilletManager skilletManager;
    private HandheldSkewerManager handheldSkewerManager;
    private ItemModelGenerator handheldSkilletIngredientModels;
    private JugItemModelGenerator jugItemModels;
    private StoveManager stoveManager;
    private NourishmentManager nourishmentManager;
    private BasketManager basketManager;
    private dev.tako.papersdelight.mechanic.villager.VillagerTradeManager villagerTradeManager;
    private dev.tako.papersdelight.mechanic.villager.VillagerHarvestManager villagerHarvestManager;
    private dev.tako.papersdelight.mechanic.villager.VillagerBreedManager villagerBreedManager;
    private dev.tako.papersdelight.mechanic.villager.VillagerPickupManager villagerPickupManager;
    private RuntimeConfigHandoff runtimeConfigHandoff;
    private PapersDelightReloadCoordinator reloadCoordinator;
    private PapersDelightRuntimeReload runtimeReload;
    private final java.util.concurrent.atomic.AtomicBoolean earlyInitialized =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean enabled =
            new java.util.concurrent.atomic.AtomicBoolean();
    private long startTimeMillis;

    public static boolean papiAvailable = false;

    private PapersDelight() {
    }

    public boolean earlyInit() {
        if (disable) {
            return false;
        }
        if (!earlyInitialized.compareAndSet(false, true)) {
            return true;
        }
        startTimeMillis = System.currentTimeMillis();
        PapersDelightClient client = PapersDelightClient.getInstance();
        if (client == null) {
            return false;
        }
        this.plugin = client;
        if (dev.tako.papersdelight.support.FeatureSupport.tryUnlockAllFeatures()) {
            client.getLogger().warning("本插件为 Paper's Delight 社区版，不包含高级版功能\n"
                    + "若您是付费取得该副本，请立即联系退款，支持正版\n"
                    + "This plugin is the Paper's Delight Community Edition and does not include premium features.\n"
                    + "If you paid for this copy, please request a refund immediately and support the official release.");
        }

        HeatSourceGate.register(HeatSourceService::isActiveHeatSource);
        AdvancedTagGate.register(AdvancedTagService::isAdvancedTagged, AdvancedTagService::resolveItems);

        ConfigManager.load(client);
        CraftEngineSerializationWarmup.warmNetworkProxy(client);

        boolean jugDecoderInstalled = JugRuntimeInstaller.installDecoderEarly(client);
        if (!jugDecoderInstalled) {
            client.getLogger().warning(ConfigManager.getOr("jug_requires_libuid",
                    "Libuid is not available; the jug mechanic is disabled."));
        }

        PapersDelightLoadPhase.Result loadPhase;
        try {
            loadPhase = PapersDelightLoadPhase.initialize(
                    client.getLogger(),
                    () -> CraftEngineConfigRegistrations.registerAll(client));
        } catch (RuntimeException ex) {
            failStartup(ConfigManager.getOr("startup_load_parser_registration_failed", "PapersDelight 在 onLoad() 阶段注册配置 parser 时失败；PapersDelight 将停止启动。"), ex);
            return false;
        }
        runtimeConfigHandoff = loadPhase.handoff();
        if (loadPhase.parserRegistrations().values().stream().allMatch(outcome ->
                outcome == CraftEngineConfigRegistrations.RegistrationOutcome.PACK_MANAGER_NOT_READY)) {
            failStartup(ConfigManager.getOr("startup_pack_manager_unavailable", "CraftEngine 在 paper-plugin.yml 中被声明为 required=true 且 load: BEFORE，但其 PackManager 在 PapersDelight.onLoad() 阶段仍不可用，PapersDelight parser 无法注册；PapersDelight 将停止启动。"), null);
            return false;
        }
        CraftEngineBehaviorRegistrations.registerAll(client.getLogger());

        CraftEngineContextRegistrations.registerAll(client.getLogger());
        return true;
    }

    public boolean enablePhase() {
        if (disable || !earlyInitialized.get()) {
            return false;
        }
        if (!enabled.compareAndSet(false, true)) {
            return true;
        }
        PapersDelightClient client = PapersDelightClient.getInstance();
        if (client == null) {
            return false;
        }
        this.plugin = client;
        boolean ok = enable(client);
        if (ok) {
            client.console(ConfigManager.getOr("plugin_startup_timing", "&aPapersDelight &f已启动！&f(&d%millis%&7ms&f)")
                    .replace("%millis%", String.valueOf(System.currentTimeMillis() - startTimeMillis)));
        }
        return ok;
    }

    public void stop() {
        disable = true;
        HeatSourceGate.unregister();
        AdvancedTagGate.unregister();
        shutdown();
    }

    private boolean enable(PapersDelightClient client) {
        if (!isCraftEngineAvailable()) {
            client.getLogger().severe(ConfigManager.getOr("ce_missing",
                    "未检测到 CraftEngine 插件，PapersDelight 已自动禁用。"));
            client.getLogger().severe(ConfigManager.getOr("ce_hint",
                    "请把 CraftEngine 服务端插件 jar 放入 plugins 目录。papersdelight.zip 只是资源/配置包，不是插件本体。"));
            client.getServer().getPluginManager().disablePlugin(client);
            return false;
        }

        if (!checkCraftEngineVersion()) {
            client.getServer().getPluginManager().disablePlugin(client);
            return false;
        }

        new Metrics(client, 32924);
        CraftEngineSerializationWarmup.run(client);

        papiAvailable = client.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        dev.tako.papersdelight.util.TextUtil.setPapiAvailability(() -> papiAvailable);
        if (papiAvailable) {
            client.getLogger().info(ConfigManager.getOr("papi_found", "已检测到 PlaceholderAPI，lang 文件中的 %papi% 变量将被解析。"));
        }

        dev.tako.papersdelight.stats.StatsManager.start(client);
        dev.tako.papersdelight.stats.StatsLifecycleListener.register(client);
        if (papiAvailable) {
            new dev.tako.papersdelight.stats.PapersDelightExpansion(client).register();
            client.getLogger().info(ConfigManager.getOr("papi_expansion_registered", "已注册 PlaceholderAPI 扩展: papersdelight"));
        }

        if (ConfigManager.hasStaleLegacyDelightsOptIn()) {
            client.getLogger().warning(ConfigManager.getOr("compatibility_legacy_delights_removed",
                    "检测到 config.yml 中存在 compatibility_legacy_delights=true，但旧 delights/ 格式兼容已在本版本移除，该选项不再生效；确认迁移完毕后，请删除该配置项。"));
        }

        recipeManager = new RecipeManager();
        customRecipeManager = new dev.tako.papersdelight.recipe.CustomRecipeManager(client);

        MenuManager menuManager = MenuManager.getInstance();

        menuManager.setMessageResolver(ConfigManager::getOr);
        client.getServer().getPluginManager().registerEvents(menuManager, client);
        client.getServer().getServicesManager().register(MenuService.class, menuManager, client, ServicePriority.Normal);

        cookingPotManager = new CookingPotManager(client, recipeManager);
        client.getServer().getPluginManager().registerEvents(cookingPotManager, client);
        cookingPotRecipeBook = new CookingPotRecipeBook(client, recipeManager, menuManager, cookingPotManager);
        client.getServer().getPluginManager().registerEvents(cookingPotRecipeBook, client);

        jugManager = JugRuntimeInstaller.installRuntime(client, recipeManager);
        if (jugManager == null) {
            if (JugSupport.isAvailable(client, JugRuntimeInstaller.runtimeClassLoader())) {
                client.getLogger().warning(ConfigManager.getOr("jug_runtime_install_failed",
                        "Libuid is available but the jug runtime failed to install; the jug mechanic is disabled."));
            } else {
                client.getLogger().warning(ConfigManager.getOr("jug_requires_libuid",
                        "Libuid is not available; the jug mechanic is disabled."));
            }
        }

        cuttingBoardManager = new CuttingBoardManager(client);
        client.getServer().getPluginManager().registerEvents(cuttingBoardManager, client);

        handheldSkilletIngredientModels = new ItemModelGenerator(client);
        if (dev.tako.papersdelight.support.FeatureSupport.handheldSkillet()) {
            client.getServer().getPluginManager().registerEvents(handheldSkilletIngredientModels, client);
        }
        jugItemModels = new JugItemModelGenerator(client);
        client.getServer().getPluginManager().registerEvents(jugItemModels, client);
        skilletManager = new SkilletManager(client, handheldSkilletIngredientModels);
        skilletManager.load();
        client.getServer().getPluginManager().registerEvents(skilletManager, client);
        if (dev.tako.papersdelight.support.FeatureSupport.handheldSkewer()) {
            handheldSkewerManager = new HandheldSkewerManager(client);
            handheldSkewerManager.load();
            client.getServer().getPluginManager().registerEvents(handheldSkewerManager, client);
        }

        stoveManager = new StoveManager(client);
        stoveManager.load();
        client.getServer().getPluginManager().registerEvents(stoveManager, client);

        basketManager = new BasketManager(client);
        basketManager.load();
        client.getServer().getPluginManager().registerEvents(basketManager, client);

        client.getServer().getPluginManager().registerEvents(new dev.tako.papersdelight.mechanic.farm.CropBonemealFix(), client);

        if (dev.tako.papersdelight.support.FeatureSupport.villagerTrade()) {
            villagerTradeManager = new dev.tako.papersdelight.mechanic.villager.VillagerTradeManager(client);
            client.getServer().getPluginManager().registerEvents(villagerTradeManager, client);
            villagerTradeManager.start();
        }
        if (dev.tako.papersdelight.support.FeatureSupport.villagerHarvest()) {
            villagerHarvestManager = new dev.tako.papersdelight.mechanic.villager.VillagerHarvestManager(client);
            client.getServer().getPluginManager().registerEvents(villagerHarvestManager, client);
            villagerHarvestManager.start();
        }
        if (dev.tako.papersdelight.support.FeatureSupport.villagerBreed()) {
            villagerBreedManager = new dev.tako.papersdelight.mechanic.villager.VillagerBreedManager(client);
            villagerBreedManager.start();
        }
        if (dev.tako.papersdelight.support.FeatureSupport.villagerPickup()) {
            villagerPickupManager = new dev.tako.papersdelight.mechanic.villager.VillagerPickupManager(client);
            client.getServer().getPluginManager().registerEvents(villagerPickupManager, client);
            villagerPickupManager.start();
        }

        nourishmentManager = new NourishmentManager(client);
        nourishmentManager.load();

        if (runtimeConfigHandoff == null) {
            failStartup(ConfigManager.getOr("startup_runtime_config_handoff_missing", "RuntimeConfigHandoff 未在 onLoad() 阶段创建；PapersDelight 将停止启动。"), null);
            return false;
        }
        boolean runtimeConfigActivated;
        try {
            runtimeConfigActivated = PapersDelightEnablePhase.activate(
                    runtimeConfigHandoff,
                    new PapersDelightRuntimeTargets(
                            client, recipeManager, cuttingBoardManager, customRecipeManager),
                    listener -> client.getServer().getPluginManager().registerEvents(listener, client));
        } catch (RuntimeException ex) {
            failStartup(ConfigManager.getOr("startup_enable_listener_registration_failed", "PapersDelight 在 onEnable() 阶段注册配置监听器时失败；PapersDelight 将停止启动。"), ex);
            return false;
        }
        if (!runtimeConfigActivated) {
            failStartup(ConfigManager.getOr("startup_runtime_config_activation_failed", "初始运行时配置交接失败；PapersDelight 将停止启动。"), null);
            return false;
        }
        runtimeReload = createRuntimeReload();
        reloadCoordinator = new PapersDelightReloadCoordinator(
                () -> ConfigManager.reload(client),
                runtimeConfigHandoff,
                runtimeReload,
                this::captureReloadStateRollback);

        if (dev.tako.papersdelight.support.FeatureSupport.petFood()) {
            PetFoodListener.reload();
            client.getServer().getPluginManager().registerEvents(new PetFoodListener(), client);
        }

        SimpleMenuModule cookingPotModule = new SimpleMenuModule.Builder()
                .id("cooking_pot")
                .menu(CookingPotMenu::create)
                .handler(new CookingPotEventHandler(cookingPotRecipeBook, cookingPotManager))
                .build();
        menuManager.registerModule(cookingPotModule);

        if (jugManager != null && !JugRuntimeInstaller.registerMenu(menuManager, jugManager)) {
            Throwable cause = JugRuntimeInstaller.lastMenuRegistrationFailure();
            client.getLogger().warning(ConfigManager.getOr("jug_menu_registration_failed",
                            "Unable to register Jug GUI menu; Jug interaction GUI is unavailable: %reason%")
                    .replace("%reason%", cause == null ? "menu module rejected" : cause.toString()));
        }

        PapersDelightCommand pdCmd = new PapersDelightCommand(
                client,
                this::reloadRuntime,
                recipeManager,
                cookingPotManager,
                cuttingBoardManager,
                skilletManager,
                stoveManager,
                handheldSkilletIngredientModels
        );
        org.bukkit.command.Command cmd = new org.bukkit.command.Command(
                "papersdelight", ConfigManager.getOr("command_papersdelight_description", "PapersDelight 主指令"), "/pd help",
                java.util.List.of("pd")
        ) {
            @Override
            public boolean execute(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender,
                                   @org.jetbrains.annotations.NotNull String label,
                                   @org.jetbrains.annotations.NotNull String[] args) {
                return pdCmd.onCommand(sender, this, label, args);
            }

            @Override
            public @org.jetbrains.annotations.NotNull java.util.List<String> tabComplete(
                    @org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender,
                    @org.jetbrains.annotations.NotNull String alias,
                    @org.jetbrains.annotations.NotNull String[] args
            ) throws IllegalArgumentException {
                java.util.List<String> completions = pdCmd.onTabComplete(sender, this, alias, args);
                return completions == null ? java.util.List.of() : completions;
            }
        };
        client.getServer().getCommandMap().register(client.getName(), cmd);

        if (dev.tako.papersdelight.support.FeatureSupport.recipeBrowser()) {
            var recipeBrowserManager = new dev.tako.papersdelight.gui.recipebrowser.RecipeBrowserManager(
                    client, recipeManager, cuttingBoardManager, customRecipeManager);
            client.getServer().getPluginManager().registerEvents(recipeBrowserManager, client);

            final var browserRef = recipeBrowserManager;
            org.bukkit.command.Command fdCmd = new org.bukkit.command.Command(
                    "farmersdelight", ConfigManager.getOr("command_farmersdelight_description", "打开配方浏览器"), "/fd",
                    java.util.List.of("fd")
            ) {
                @Override
                public boolean execute(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender,
                                       @org.jetbrains.annotations.NotNull String label,
                                       @org.jetbrains.annotations.NotNull String[] args) {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        sender.sendMessage(ConfigManager.getOr("command_players_only", "仅玩家可使用此命令"));
                        return true;
                    }
                    if (!player.hasPermission("papersdelight.recipe")) {
                        player.sendMessage(dev.tako.papersdelight.util.TextUtil.parse(player,
                                ConfigManager.getOr("command_recipe_no_permission", "§c你没有权限使用此命令。")));
                        return true;
                    }
                    browserRef.openHome(player);
                    return true;
                }
            };
            fdCmd.setPermission("papersdelight.recipe");
            client.getServer().getCommandMap().register(client.getName(), fdCmd);

            pdCmd.setRecipeBrowser(recipeBrowserManager);
        }

        if (cuttingBoardManager != null) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(client,
                    cuttingBoardManager::loadDisplayEntitiesForAllLoadedChunks, 100L);
        }

        if (stoveManager != null) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(client,
                    stoveManager::discoverAllStoves, 110L);
        }

        if (skilletManager != null) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(client,
                    skilletManager::discoverAllSkillets, 120L);
        }

        if (basketManager != null) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(client,
                    basketManager::discoverAllBaskets, 130L);
        }

        client.getLogger().info(ConfigManager.getOr("plugin_started", "PapersDelight 已启动。"));
        return true;
    }

    private void shutdown() {
        MenuManager.getInstance().closeAll();
        CraftEngineConfigRegistrations.unregisterAll(plugin.getLogger());
        dev.tako.papersdelight.stats.StatsManager.beginShutdownWindow();

        if (cookingPotManager != null) cookingPotManager.shutdown();
        JugRuntimeInstaller.shutdownRuntime(jugManager);
        JugRuntimeInstaller.uninstallDecoder();
        jugManager = null;
        if (skilletManager != null) skilletManager.stopAll();
        if (handheldSkewerManager != null) handheldSkewerManager.stopAll();
        if (cuttingBoardManager != null) cuttingBoardManager.shutdown();
        if (stoveManager != null) stoveManager.stopAll();
        if (basketManager != null) basketManager.stopAll();
        if (nourishmentManager != null) nourishmentManager.stopAll();
        if (villagerBreedManager != null) villagerBreedManager.stop();
        if (villagerPickupManager != null) villagerPickupManager.stop();

        dev.tako.papersdelight.stats.StatsManager.stop();
        plugin.getLogger().info(ConfigManager.getOr("plugin_stopped", "PapersDelight 已关闭。"));
    }

    public NourishmentManager getNourishmentManager() {
        return nourishmentManager;
    }

    private void reloadRuntime(org.bukkit.command.CommandSender sender) {
        if (reloadCoordinator != null) {
            reloadCoordinator.reload();
        } else {
            reloadWithoutCoordinator();
        }
    }

    private boolean reloadWithoutCoordinator() {
        if (runtimeReload == null) runtimeReload = createRuntimeReload();
        RuntimeConfigHandoff handoff = runtimeConfigHandoff;
        if (handoff != null) {
            return new PapersDelightReloadCoordinator(
                    () -> ConfigManager.reload(plugin),
                    handoff,
                    runtimeReload,
                    this::captureReloadStateRollback).reload();
        }

        Runnable rollback = captureReloadStateRollback();
        try {
            ConfigManager.reload(plugin);
            runtimeReload.commit(rollback);
            return true;
        } catch (RuntimeException | Error failure) {
            try {
                rollback.run();
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private Runnable captureReloadStateRollback() {
        ConfigManager.State configState = ConfigManager.captureState();
        java.util.concurrent.atomic.AtomicBoolean restored = new java.util.concurrent.atomic.AtomicBoolean();
        return () -> {
            if (!restored.compareAndSet(false, true)) return;
            ConfigManager.restoreState(configState);
        };
    }

    private PapersDelightRuntimeReload createRuntimeReload() {
        return new PapersDelightRuntimeReload(plugin.getLogger(), java.util.List.of(
                new PapersDelightRuntimeReload.Step(
                        "cooking-pot", this::reloadCookingPotRuntime, this::reloadCookingPotRuntime),
                new PapersDelightRuntimeReload.Step(
                        "cutting-displays", this::reloadCuttingDisplays, this::restoreCuttingDisplays),
                new PapersDelightRuntimeReload.Step(
                        "skillet", this::reloadSkilletRuntime, this::recoverSkilletRuntime),
                new PapersDelightRuntimeReload.Step(
                        "handheld-skewer", this::reloadHandheldSkewerRuntime, this::reloadHandheldSkewerRuntime),
                new PapersDelightRuntimeReload.Step(
                        "stove", this::reloadStoveRuntime, this::recoverStoveRuntime),
                new PapersDelightRuntimeReload.Step(
                        "basket", this::reloadBasketRuntime, this::reloadBasketRuntime),
                new PapersDelightRuntimeReload.Step(
                        "nourishment", this::reloadNourishmentRuntime, this::reloadNourishmentRuntime),
                new PapersDelightRuntimeReload.Step(
                        "cooking-pot-recipe-book", this::reloadCookingPotRecipeBook,
                        this::reloadCookingPotRecipeBook),
                new PapersDelightRuntimeReload.Step(
                        "pet-food", PetFoodListener::reload, PetFoodListener::reload),
                new PapersDelightRuntimeReload.Step(
                        "villager", this::reloadVillagerRuntime, this::reloadVillagerRuntime),
                new PapersDelightRuntimeReload.Step(
                        "campfire-cache", CampfireRecipeUtil::clearCache, () -> { }),
                new PapersDelightRuntimeReload.Step(
                        "stats", this::reloadStatsRuntime, this::reloadStatsRuntime)
        ));
    }

    private void reloadCookingPotRuntime() {
        if (cookingPotManager != null) cookingPotManager.reload();
    }

    private void reloadVillagerRuntime() {
        if (villagerTradeManager != null) villagerTradeManager.reload();
        if (villagerHarvestManager != null) villagerHarvestManager.reload();
        if (villagerBreedManager != null) villagerBreedManager.reload();
        if (villagerPickupManager != null) villagerPickupManager.reload();
    }

    private void reloadCuttingDisplays() {
        if (cuttingBoardManager == null) return;
        cuttingBoardManager.refreshDisplayEntities();
        restoreCuttingDisplays();
    }

    private void restoreCuttingDisplays() {
        if (cuttingBoardManager != null) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(plugin,
                    cuttingBoardManager::loadDisplayEntitiesForAllLoadedChunks, 5L);
        }
    }

    private void reloadSkilletRuntime() {
        if (skilletManager == null) return;
        skilletManager.stopAll();
        recoverSkilletRuntime();
    }

    private void recoverSkilletRuntime() {
        if (skilletManager == null) return;
        skilletManager.load();
        SCHEDULER.getGlobalRegionScheduler().runTaskLater(plugin, skilletManager::discoverAllSkillets, 5L);
    }

    private void reloadHandheldSkewerRuntime() {
        if (handheldSkewerManager == null) return;
        handheldSkewerManager.stopAll();
        handheldSkewerManager.load();
    }

    private void reloadStoveRuntime() {
        if (stoveManager == null) return;
        stoveManager.stopAll();
        recoverStoveRuntime();
    }

    private void recoverStoveRuntime() {
        if (stoveManager == null) return;
        stoveManager.load();
        SCHEDULER.getGlobalRegionScheduler().runTaskLater(plugin, stoveManager::discoverAllStoves, 5L);
    }

    private void reloadBasketRuntime() {
        if (basketManager != null) basketManager.load();
    }

    private void reloadNourishmentRuntime() {
        if (nourishmentManager != null) nourishmentManager.load();
    }

    private void reloadCookingPotRecipeBook() {
        if (cookingPotRecipeBook != null) cookingPotRecipeBook.reloadConfig();
    }

    private void reloadStatsRuntime() {
        dev.tako.papersdelight.stats.StatsManager.start(plugin);
        dev.tako.papersdelight.stats.StatsManager stats =
                dev.tako.papersdelight.stats.StatsManager.getInstance();
        if (stats == null) return;
        for (org.bukkit.entity.Player online : plugin.getServer().getOnlinePlayers()) {
            java.util.UUID uuid = online.getUniqueId();
            SCHEDULER.getAsyncScheduler().runTask(plugin, () -> stats.warmUp(uuid));
        }
    }

    private void failStartup(String message, Throwable cause) {
        if (cause == null) {
            plugin.getLogger().severe(message);
            throw new IllegalStateException(message);
        }

        plugin.getLogger().log(java.util.logging.Level.SEVERE, message, cause);
        throw new IllegalStateException(message, cause);
    }

    private boolean isCraftEngineAvailable() {
        return CraftEngineUtil.isCraftEngineEnabled(plugin);
    }

    private boolean checkCraftEngineVersion() {
        org.bukkit.plugin.Plugin craftEngine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        String version = craftEngine == null ? null : craftEngine.getPluginMeta().getVersion();
        if (!CraftEngineVersionGate.isBelowRequired(version)) {
            return true;
        }

        String required = CraftEngineVersionGate.REQUIRED_VERSION;
        String banner = "=".repeat(72);
        plugin.getLogger().severe(banner);
        plugin.getLogger().severe(ConfigManager.getOr("ce_version_too_old",
                        "CraftEngine 版本过低：当前 %current%，PapersDelight 需要 %required% 或更高版本。")
                .replace("%current%", String.valueOf(version))
                .replace("%required%", required));
        plugin.getLogger().severe(ConfigManager.getOr("ce_version_too_old_action",
                        "PapersDelight 已停止启动。请将 CraftEngine 升级到 %required% 或更高版本后重启服务器。")
                .replace("%required%", required));
        plugin.getLogger().severe(banner);
        return false;
    }
}
