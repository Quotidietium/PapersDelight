package dev.tako.papersdelight.command;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.cookingpot.CookingPotManager;
import dev.tako.papersdelight.mechanic.cutting.CuttingBoardManager;
import dev.tako.papersdelight.mechanic.skillet.ItemModelGenerator;
import dev.tako.papersdelight.mechanic.skillet.SkilletManager;
import dev.tako.papersdelight.mechanic.nourishment.NourishmentManager;
import dev.tako.papersdelight.mechanic.stove.StoveManager;
import dev.tako.papersdelight.jug.JugDiagnostics;
import dev.tako.papersdelight.jug.JugDiagnosticsMessages;
import dev.tako.papersdelight.jug.JugDiagnosticsReport;
import dev.tako.papersdelight.jug.JugGate;
import dev.tako.papersdelight.jug.JugSupport;
import dev.tako.papersdelight.recipe.RecipeManager;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.util.TextUtil;
import dev.tako.papersdelight.util.ItemMetaUtil;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PapersDelightCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_RELOAD = "papersdelight.reload";
    private static final String PERMISSION_DEV = "papersdelight.dev";
    private static final String PERMISSION_RECIPE = "papersdelight.recipe";
    private static final String PERMISSION_REGENERATE_SKILLET_MODELS = "papersdelight.regenerate_skillet_models";

    private static final List<String> ROOT_COMMANDS = buildRootCommands();

    private static List<String> buildRootCommands() {
        List<String> commands = new java.util.ArrayList<>(
                List.of("help", "status", "version", "reload", "inspect", "effect"));
        if (dev.tako.papersdelight.support.FeatureSupport.recipeBrowser()) commands.add("recipe");
        commands.add("regenerate-item-models");
        commands.add("jug");
        return List.copyOf(commands);
    }

    private static final List<String> JUG_SUBCOMMANDS = List.of("fluid-items");

    private final Plugin plugin;
    private final java.util.function.Consumer<CommandSender> reloadAction;
    private final RecipeManager recipeManager;
    private final CookingPotManager cookingPotManager;
    private final CuttingBoardManager cuttingBoardManager;
    private final SkilletManager skilletManager;
    private final StoveManager stoveManager;
    private final ItemModelGenerator handheldSkilletIngredientModels;
    private dev.tako.papersdelight.gui.recipebrowser.RecipeBrowserManager recipeBrowser;

    public void setRecipeBrowser(dev.tako.papersdelight.gui.recipebrowser.RecipeBrowserManager browser) {
        this.recipeBrowser = browser;
    }

    public PapersDelightCommand(
            Plugin plugin,
            java.util.function.Consumer<CommandSender> reloadAction,
            RecipeManager recipeManager,
            CookingPotManager cookingPotManager,
            CuttingBoardManager cuttingBoardManager,
            SkilletManager skilletManager,
            StoveManager stoveManager,
            ItemModelGenerator handheldSkilletIngredientModels
    ) {
        this.plugin = plugin;
        this.reloadAction = reloadAction;
        this.recipeManager = recipeManager;
        this.cookingPotManager = cookingPotManager;
        this.cuttingBoardManager = cuttingBoardManager;
        this.skilletManager = skilletManager;
        this.stoveManager = stoveManager;
        this.handheldSkilletIngredientModels = handheldSkilletIngredientModels;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "status" -> handleStatus(sender);
            case "version" -> handleVersion(sender);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender);
            case "effect" -> handleEffect(sender, args);
            case "recipe" -> handleRecipe(sender);
            case "regenerate-item-models" -> handleRegenerateItemModels(sender);
            case "jug" -> handleJug(sender, args);
            default -> {
                sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_unknown_subcommand",
                        "§c未知子命令。使用 §f/pd help §c查看可用指令。")));
                yield true;
            }
        };
    }

    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_status_header", "§6PapersDelight §7状态")));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_status_version", "§7版本: §f") + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_status_craftengine", "§7CraftEngine: ")
                + (CraftEngineUtil.isCraftEngineEnabled(plugin)
                ? ConfigManager.getOr("command_status_craftengine_loaded", "§a已加载")
                : ConfigManager.getOr("command_status_craftengine_not_loaded", "§c未加载"))));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_status_cooking_pot_recipes", "§7厨锅配方: §f") + recipeManager.count()));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_status_cutting_board_recipes", "§7砧板配方: §f") + cuttingBoardManager.countRecipes()));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_status_skillet", "§7煎锅: §f使用原版营火配方")));
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        sender.sendMessage(TextUtil.parse(sender, "§6Paper's Delight §fv" + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(TextUtil.parse(sender, "&aAuthor: &fShimamura Tako&7, &fMr Dg32z_&7, &fgukuan&7, &fCold Leaves, &fyuuka0"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, PERMISSION_RELOAD)) return true;

        reloadAction.accept(sender);
        if (recipeBrowser != null) recipeBrowser.clearTagExpansionCache();
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_reload_success",
                "§fPaper's Delight§a已重载完毕！ 加载了§f%recipes%§a个厨锅配方和§f%cutting_recipes%§a个砧板配方。")
                .replace("%recipes%", String.valueOf(recipeManager.count()))
                .replace("%cutting_recipes%", String.valueOf(cuttingBoardManager.countRecipes()))));
        return true;
    }

    private boolean handleRegenerateItemModels(CommandSender sender) {
        if (!hasPermission(sender, PERMISSION_REGENERATE_SKILLET_MODELS)) return true;
        boolean generated = handheldSkilletIngredientModels.regenerate();
        sender.sendMessage(TextUtil.parse(sender, generated
                ? ConfigManager.getOr("command_regenerate_item_models_success", "Item models were written to the resource-pack source directory.")
                : ConfigManager.getOr("command_regenerate_item_models_failure", "§cFailed to generate item models; check the server log.")));
        return true;
    }

    private boolean handleJug(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_DEV)) return true;
        if (args.length < 2 || !args[1].equalsIgnoreCase("fluid-items")) {
            sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_jug_usage",
                    "§c用法: §f/pd jug fluid-items")));
            return true;
        }

        JugDiagnosticsReport report = JugDiagnostics.collect(
                JugSupport.isAvailable(plugin),
                JugGate.available(),
                recipeManager.jugRecipes(),
                JugDiagnostics.fluidKeys(plugin),
                id -> CraftEngineUtil.createItem(id, 1) != null);
        for (String line : JugDiagnosticsMessages.lines(report)) {
            sender.sendMessage(TextUtil.parse(sender, line));
        }
        return true;
    }

    private boolean handleRecipe(CommandSender sender) {
        if (!hasPermission(sender, PERMISSION_RECIPE)) return true;
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(Objects.requireNonNull(ConfigManager.getOr("command_players_only", "仅玩家可使用此命令")));
            return true;
        }
        if (recipeBrowser != null) {
            recipeBrowser.openHome(player);
        }
        return true;
    }

    private boolean handleInspect(CommandSender sender) {
        if (!hasPermission(sender, PERMISSION_DEV)) return true;
        Player player = requirePlayer(sender);
        if (player == null) return true;

        ItemStack held = player.getInventory().getItemInMainHand();
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_inspect_held_item", "§6手持物品")));
        if (held == null || held.isEmpty()) {
            sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_inspect_empty", "§7- 空")));
        } else {
            sender.sendMessage(TextUtil.parse(sender, "§7- material: §f" + held.getType().getKey()));
            String customItemId = CraftEngineUtil.getCustomItemId(held);
            sender.sendMessage(TextUtil.parse(sender, "§7- craftengine: §f" + (customItemId == null ? "-" : customItemId)));
            sender.sendMessage(TextUtil.parse(sender, "§7- item_model: §f" + ItemMetaUtil.getItemModel(held.getItemMeta())));
            sender.sendMessage(TextUtil.parse(sender, "§7- amount: §f" + held.getAmount()));
        }

        Block target = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_inspect_target_block", "§6准星方块")));
        if (target == null) {
            sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_inspect_none", "§7- 无")));
        } else {
            sender.sendMessage(TextUtil.parse(sender, "§7- material: §f" + target.getType().getKey()));
            String customBlockId = CraftEngineUtil.getCustomBlockId(target);
            sender.sendMessage(TextUtil.parse(sender, "§7- craftengine: §f" + (customBlockId == null ? "-" : customBlockId)));
            sender.sendMessage(TextUtil.parse(sender, "§7- location: §f" + formatLocation(target)));
        }
        return true;
    }

    private boolean handleEffect(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_DEV)) return true;
        Player player = requirePlayer(sender);
        if (player == null) return true;

        if (args.length < 2) {
            sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_effect_usage",
                    "§c用法: §f/pd effect <nourishment> [秒数]")));
            return true;
        }

        String effectType = args[1].toLowerCase(Locale.ROOT);
        int seconds = args.length >= 3 ? parseInt(args[2], 30, 1, 3600) : 30;
        int ticks = seconds * 20;

        switch (effectType) {
            case "nourishment" -> {
                NourishmentManager mgr = NourishmentManager.getInstance();
                if (mgr == null) {
                    sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_effect_nourishment_uninitialized", "§cNourishmentManager 未初始化。")));
                    return true;
                }
                mgr.applyNourishment(player, ticks);
                sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_effect_nourishment_applied",
                        "§a已施加滋养效果，持续 §f%duration%").replace("%duration%", NourishmentManager.formatDuration(ticks))));
            }
            default -> sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_effect_unknown_type",
                    "§c未知效果类型: §f%type%§c。可选: nourishment").replace("%type%", effectType)));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_help_header", "§6PapersDelight §7指令")));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_help_status", "§e/pd status §7- 查看插件、CraftEngine 和配方加载状态")));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_help_reload", "§e/pd reload §7- 重载配置、语言和机制配方")));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_help_inspect", "§e/pd inspect §7- 检查手持物品和准星方块 ID")));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_help_regenerate_item_models", "§e/pd regenerate-item-models §7- Regenerate item models")));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_help_effect", "§e/pd effect <nourishment> [秒数] §7- 施加滋养效果（默认30秒）")));
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_help_jug", "§e/pd jug fluid-items §7- 诊断液罐流体与展示物品缺失情况")));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_no_permission",
                "§c你没有权限执行此操作。需要权限: §f%permission%").replace("%permission%", permission)));
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            sender.sendMessage(TextUtil.parse(sender, ConfigManager.getOr("command_player_required", "§c这个指令只能由玩家执行。")));
        }
        return player;
    }

    private static Player asPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    private static String formatLocation(Block block) {
        return block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ();
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> available = new java.util.ArrayList<>();
            for (String cmd : ROOT_COMMANDS) {
                if (canUseSubcommand(sender, cmd)) available.add(cmd);
            }
            return startsWith(available, args[0]);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (!canUseSubcommand(sender, subCommand)) return List.of();
        if (args.length == 2) {
            return switch (subCommand) {
                case "effect" -> startsWith(List.of("nourishment"), args[1]);
                case "jug" -> startsWith(JUG_SUBCOMMANDS, args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (subCommand) {
                case "effect" -> startsWith(List.of("30", "60", "120", "300", "600"), args[2]);
                default -> List.of();
            };
        }

        return List.of();
    }

    private boolean canUseSubcommand(CommandSender sender, String sub) {
        return switch (sub) {
            case "reload" -> sender.hasPermission(PERMISSION_RELOAD);
            case "inspect", "effect", "jug" -> sender.hasPermission(PERMISSION_DEV);
            case "recipe" -> sender.hasPermission(PERMISSION_RECIPE);
            case "regenerate-item-models" -> sender.hasPermission(PERMISSION_REGENERATE_SKILLET_MODELS);
            default -> true;
        };
    }

    private static List<String> startsWith(List<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .toList();
    }
}
