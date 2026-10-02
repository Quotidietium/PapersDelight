package dev.tako.papersdelight.config;

import dev.tako.papersdelight.util.ItemMetaUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {

    private static YamlConfiguration config;
    private static YamlConfiguration lang;
    private static YamlConfiguration defaultConfig;

    private static volatile List<HeatSourceDef> heatSources = Collections.emptyList();

    private static final Set<String> MERGE_EXCLUDES = Set.of(
            "cutting_board.msg",
            "skillet.msg"
    );

    private static final List<String> BUILTIN_LANGS = List.of("zh_cn", "en_us");
    private static final int CURRENT_CONFIG_VERSION = 12;

    /**
     * getOr/getList 的读穿缓存：键 → 已解析的 YAML 借用值（String / List）或 MISSING 哨兵。
     * 仅在 load()/restoreState()（均 synchronized）末尾整体失效。
     * 安全前提（已审计）：运行期没有经 getConfig() 突变 config/lang 的调用点——
     * 全项目对 getConfig() 的使用均为只读读取。
     * 上界保护：真实配置/语言键为有限集（数百量级）；若调用方传入动态拼出的键
     * （如含序号），超过上限后只读不存，保证内存占用有界。
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> READ_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, List<String>> LIST_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object READ_MISSING = new Object();
    private static final int READ_CACHE_MAX = 8192;
    /** 近似条目计数（仅用于上界保护，非精确值；clear 时随缓存一起复位）。 */
    private static final java.util.concurrent.atomic.AtomicInteger READ_CACHE_COUNT = new java.util.concurrent.atomic.AtomicInteger();

    private ConfigManager() {}

    public record State(
            String configYaml,
            String langYaml,
            String defaultConfigYaml,
            List<HeatSourceDef> heatSources
    ) {
        public State {
            heatSources = List.copyOf(heatSources == null ? List.of() : heatSources);
        }
    }

    public static synchronized State captureState() {
        return new State(serialize(config), serialize(lang), serialize(defaultConfig), heatSources);
    }

    public static synchronized void restoreState(State state) {
        State captured = java.util.Objects.requireNonNull(state, "state");
        config = deserialize(captured.configYaml());
        lang = deserialize(captured.langYaml());
        defaultConfig = deserialize(captured.defaultConfigYaml());
        heatSources = captured.heatSources();
        READ_CACHE.clear();
        LIST_CACHE.clear();
        READ_CACHE_COUNT.set(0);
    }

    private static String serialize(YamlConfiguration yaml) {
        return yaml == null ? null : yaml.saveToString();
    }

    private static YamlConfiguration deserialize(String yaml) {
        if (yaml == null) return null;
        YamlConfiguration restored = new YamlConfiguration();
        try {
            restored.loadFromString(yaml);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Captured ConfigManager state is invalid", exception);
        }
        return restored;
    }

    public static synchronized void load(Plugin plugin) {
        File dataFolder = plugin.getDataFolder();
        dataFolder.mkdirs();

        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) {
                defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {

            plugin.getLogger().severe("Cannot read bundled config.yml from JAR");
        }

        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        } else {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
            if (!existing.contains("config-version")) {

                normalizeUnversionedConfig(plugin, existing);
                existing.set("config-version", CURRENT_CONFIG_VERSION);
                try {
                    existing.save(configFile);
                } catch (IOException e) {
                    plugin.getLogger().severe(ConfigManager.getOr("config_normalized_save_failed",
                            "无法保存归一化后的 config.yml: %error%").replace("%error%", describeError(e)));
                }
            } else {
                migrateConfig(plugin, configFile, existing);
            }
        }
        mergeFromDefault(plugin, "config.yml", configFile, MERGE_EXCLUDES);
        config = YamlConfiguration.loadConfiguration(configFile);

        String langName = config.getString("lang", "zh_cn");
        File langDir = new File(dataFolder, "lang");
        langDir.mkdirs();

        for (String builtin : BUILTIN_LANGS) {
            File builtinFile = new File(langDir, builtin + ".yml");
            mergeFromDefault(plugin, "lang/" + builtin + ".yml", builtinFile, Set.of());
        }
        releaseBuiltinLangFilesIfAvailable(plugin, langDir);

        File langFile = resolveLangFile(plugin, langDir, langName);
        lang = YamlConfiguration.loadConfiguration(langFile);

        File guiFile = new File(dataFolder, "gui.yml");
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        mergeFromDefault(plugin, "gui.yml", guiFile, Set.of());
        YamlConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        for (String key : guiConfig.getKeys(true)) {
            if (!guiConfig.isConfigurationSection(key)) {
                config.set(key, guiConfig.get(key));
            }
        }

        heatSources = parseHeatSources();

        plugin.getLogger().info(ConfigManager.getOr("cfg_loaded", "已加载配置，语言: %lang%").replace("%lang%", langName));
        READ_CACHE.clear();
        LIST_CACHE.clear();
        READ_CACHE_COUNT.set(0);
    }

    public static synchronized void reload(Plugin plugin) {
        load(plugin);
        plugin.getLogger().info(ConfigManager.getOr("cfg_reloaded", "配置和语言文件已重载。"));
    }

    private static void normalizeUnversionedConfig(Plugin plugin, YamlConfiguration cfg) {
        plugin.getLogger().warning("config.yml missing config-version; only recognizable legacy structures will be normalized, conservatively.");

        List<?> legacyHeatSources = cfg.getList("cooking_pot.heat_sources");
        List<?> topLevelHeatSources = cfg.getList("heat_sources");
        boolean hasTopLevelHeatSources = topLevelHeatSources != null && !topLevelHeatSources.isEmpty();
        boolean hasLegacyHeatSources = legacyHeatSources != null && !legacyHeatSources.isEmpty();
        if (hasLegacyHeatSources && !hasTopLevelHeatSources) {
            cfg.set("heat_sources", deepCopyYamlValue(legacyHeatSources));
            cfg.set("cooking_pot.heat_sources", null);
            plugin.getLogger().info(ConfigManager.getOr("config_legacy_heat_sources_normalized",
                    "已将旧 cooking_pot.heat_sources 归一化到顶级 heat_sources，并清除旧路径以避免双份真相。"));
        } else if (hasLegacyHeatSources) {
            plugin.getLogger().warning(ConfigManager.getOr("config_legacy_heat_sources_preserved",
                    "顶级 heat_sources 已存在且不为空，旧 cooking_pot.heat_sources 保留未覆盖；请人工核对热源配置。"));
        }

        plugin.getLogger().warning(ConfigManager.getOr("config_unversioned_legacy_format_warning",
                "无版本配置可能携带旧格式的 gui.yml（v9 之前布局）或 cutting_board.default_tools（v7 之前列表格式）。"
                        + "若插件运行后 GUI 或砧板工具出现异常，请删除对应文件后重载以获取新默认值。"));
    }

    private static Object deepCopyYamlValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), deepCopyYamlValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object entry : list) {
                copy.add(deepCopyYamlValue(entry));
            }
            return copy;
        }
        return value;
    }

    private static void migrateConfig(Plugin plugin, File configFile, YamlConfiguration cfg) {
        int version = cfg.getInt("config-version", 1);
        boolean changed = false;

        if (version == 1) {
            if (cfg.getDouble("cutting_board.display.stack_xz_offset", 0.06) == 0.06) {
                cfg.set("cutting_board.display.stack_xz_offset", 0.075);
            }
            if (cfg.getDouble("cutting_board.display_block.stack_xz_offset", 0.06) == 0.06) {
                cfg.set("cutting_board.display_block.stack_xz_offset", 0.075);
            }
            cfg.set("config-version", 2);
            version = 2;
            changed = true;
            plugin.getLogger().info("config.yml migrated from v1 to v2");
        }

        if (version == 2) {
            File guiFile = new File(plugin.getDataFolder(), "gui.yml");
            guiFile.delete();
            plugin.saveResource("gui.yml", false);
            cfg.set("config-version", 3);
            version = 3;
            changed = true;
            plugin.getLogger().info("config.yml migrated from v2 to v3, gui.yml regenerated");
        }

        if (version == 3) {
            cfg.set("config-version", 4);
            version = 4;
            changed = true;
            plugin.getLogger().info("config.yml 已从 v3 迁移到 v4，保留现有语言文件并继续补齐内置语言");
        }

        if (version == 4) {
            cfg.set("particle_throttle.stove_threshold", 8);
            cfg.set("particle_throttle.stove_max_rate", 0.1);
            cfg.set("particle_throttle.cooking_pot_threshold", 4);
            cfg.set("particle_throttle.cooking_pot_max_rate", 0.05);
            cfg.set("particle_throttle.ambient_sound_threshold", 4);
            cfg.set("particle_throttle.ambient_sound_max_rate", 0.001);
            File guiFile = new File(plugin.getDataFolder(), "gui.yml");
            guiFile.delete();
            plugin.saveResource("gui.yml", false);

            File recipesDir = new File(plugin.getDataFolder(), "recipes");
            if (recipesDir.isDirectory()) {
                File[] files = recipesDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
            }
            File tagsFile = new File(plugin.getDataFolder(), "tags.yml");
            tagsFile.delete();
            cfg.set("config-version", 5);
            version = 5;
            changed = true;
            plugin.getLogger().info("config.yml 已从 v4 迁移到 v5，发包节流参数已更新，gui.yml / recipes / tags.yml 已重新生成，lang 将保留并补齐默认键");
        }

        if (version == 5) {
            cfg.set("cutting_board.block", java.util.List.of("farmersdelight:cutting_board"));
            cfg.set("skillet.block", java.util.List.of("farmersdelight:skillet"));
            cfg.set("stove.block", java.util.List.of("farmersdelight:stove"));
            File guiFile = new File(plugin.getDataFolder(), "gui.yml");
            guiFile.delete();
            plugin.saveResource("gui.yml", false);
            cfg.set("config-version", 6);
            version = 6;
            changed = true;
            plugin.getLogger().info("config.yml 已从 v5 迁移到 v6，block 字段已改为列表格式，gui.yml 已重新生成");
        }

        if (version == 6) {
            cfg.set("cutting_board.default_tools", "#farmersdelight:tools/knives");
            cfg.set("cutting_board.insertable_tools", null);
            cfg.set("resource_directory", null);
            File toolsFile = new File(plugin.getDataFolder(), "insertable_tools.yml");
            toolsFile.delete();
            plugin.saveResource("insertable_tools.yml", false);

            File oldRecipes = new File(plugin.getDataFolder(), "recipes");
            File oldCutting = new File(plugin.getDataFolder(), "cutting_recipes");
            deleteFilesInDir(oldRecipes);
            deleteFilesInDir(oldCutting);
            oldRecipes.delete();
            oldCutting.delete();
            File oldTags = new File(plugin.getDataFolder(), "tags.yml");
            if (oldTags.isFile()) oldTags.delete();

            File guiFile = new File(plugin.getDataFolder(), "gui.yml");
            if (guiFile.isFile()) guiFile.delete();
            plugin.saveResource("gui.yml", false);

            cfg.set("config-version", 7);
            version = 7;
            changed = true;
            plugin.getLogger().info("config.yml 已从 v6 迁移到 v7，" +
                    "default_tools 已改为 CE 物品标签，insertable_tools 已移除，" +
                    "resource_directory 已移除，旧 recipes/cutting_recipes/tags.yml 已删除，" +
                    "insertable_tools.yml / gui.yml 已重新释放，lang 将保留并补齐默认键");
        }

        if (version < 8) {

            if (cfg.contains("enchantment.rules")) {
                cfg.set("enchantment.rules", null);
                plugin.getLogger().info("v7→v8: 已移除 config.yml 中的 enchantment.rules（迁移到 enchantment.yml）");
            }
            cfg.set("config-version", 8);
            version = 8;
            changed = true;
        }

        if (version < 9) {

            File guiFile = new File(plugin.getDataFolder(), "gui.yml");
            if (guiFile.isFile()) guiFile.delete();
            plugin.saveResource("gui.yml", false);

            plugin.getLogger().info("v8→v9: gui.yml 已重新生成，语言文件将保留并补齐内置默认键");

            cfg.set("config-version", 9);
            version = 9;
            changed = true;
        }

        if (version < 10) {

            cfg.set("cooking_pot.heat_sources", null);

            cfg.set("config-version", 10);
            version = 10;
            changed = true;
            plugin.getLogger().info("v9→v10: heat_sources 提升到顶级");
        }

        if (version < 12) {

            File langDir = new File(plugin.getDataFolder(), "lang");
            for (String builtin : BUILTIN_LANGS) {
                File builtinLang = new File(langDir, builtin + ".yml");
                if (builtinLang.exists() && !builtinLang.delete()) {
                    plugin.getLogger().warning("v11→v12: unable to delete "
                            + builtinLang.getName() + "; stale keys may remain");
                }
            }
            cfg.set("config-version", 12);
            version = 12;
            changed = true;
            plugin.getLogger().info("config.yml migrated from v11 to v12, "
                    + "built-in lang files (zh_cn / en_us) regenerated from JAR");
        }

        if (version < CURRENT_CONFIG_VERSION) {
            cfg.set("config-version", CURRENT_CONFIG_VERSION);
            version = CURRENT_CONFIG_VERSION;
            changed = true;
        }

        if (changed) {
            try {
                cfg.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save migrated config.yml: " + e.getMessage());
            }
        }
    }

    private static void releaseBuiltinLangFilesIfAvailable(Plugin plugin, File langDir) {
        String prefix = "lang/";
        try {
            java.security.ProtectionDomain protectionDomain = plugin.getClass().getProtectionDomain();
            if (protectionDomain == null || protectionDomain.getCodeSource() == null) return;
            java.net.URL url = protectionDomain.getCodeSource().getLocation();
            if (url == null) return;
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(new File(url.toURI()))) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".yml")) continue;

                    File target = new File(langDir, name.substring(prefix.length()));
                    if (target.exists()) continue;

                    target.getParentFile().mkdirs();
                    try (InputStream in = plugin.getResource(name)) {
                        if (in != null) {
                            Files.copy(in, target.toPath());
                            plugin.getLogger().info(ConfigManager.getOr("lang_file_released",
                                    "已释放语言文件: %file%").replace("%file%", target.getName()));
                        }
                    }
                }
            }
        } catch (Exception e) {

            if (!(e instanceof java.io.FileNotFoundException)
                    && !(e instanceof IllegalStateException)) {
                plugin.getLogger().warning(ConfigManager.getOr("lang_file_release_failed",
                        "释放语言文件失败: %error%").replace("%error%", describeError(e)));
            }
        }
    }

    private static File resolveLangFile(Plugin plugin, File langDir, String langName) {
        File target = new File(langDir, langName + ".yml");
        if (target.exists()) return target;

        for (String fallback : List.of("en_us", "zh_cn")) {
            if (fallback.equals(langName)) continue;
            File file = new File(langDir, fallback + ".yml");
            if (file.exists()) {
                plugin.getLogger().warning(ConfigManager.getOr("lang_file_fallback",
                        "语言 '%lang%' 不存在，回退到 %fallback%")
                        .replace("%lang%", langName)
                        .replace("%fallback%", fallback));
                return file;
            }
        }
        plugin.getLogger().warning(ConfigManager.getOr("lang_file_missing",
                "找不到任何可用语言文件，将使用空配置（lang=%lang%）")
                .replace("%lang%", langName));
        return target;
    }

    private static void mergeFromDefault(Plugin plugin, String jarPath, File userFile, Set<String> excludes) {
        try (InputStream in = plugin.getResource(jarPath)) {
            if (in == null) {
                plugin.getLogger().warning("Missing default file in JAR: " + jarPath);
                return;
            }

            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration userConfig = userFile.exists()
                    ? YamlConfiguration.loadConfiguration(userFile)
                    : new YamlConfiguration();

            boolean changed = copyMissing(userConfig, defaultConfig, "", excludes);

            if (changed || !userFile.exists()) {
                userFile.getParentFile().mkdirs();
                userConfig.save(userFile);
                plugin.getLogger().info((changed ? "Updated" : "Created") + " config file: " + userFile.getName());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to merge config file " + jarPath + ": " + e.getMessage());
        }
    }

    private static boolean copyMissing(YamlConfiguration target, ConfigurationSection defaultConfig, String prefix, Set<String> excludes) {
        boolean changed = false;
        for (String key : defaultConfig.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (excludes.contains(fullKey)) continue;

            if (defaultConfig.isConfigurationSection(key)) {
                if (!target.isConfigurationSection(fullKey)) {
                    if (!target.contains(fullKey, true)) {
                        target.createSection(fullKey);
                        changed = true;
                    } else {
                        continue;
                    }
                }
                changed |= copyMissing(target, defaultConfig.getConfigurationSection(key), fullKey, excludes);
                continue;
            }

            if (!target.contains(fullKey, true)) {
                target.set(fullKey, defaultConfig.get(key));
                changed = true;
            }
        }
        return changed;
    }

    public static String get(String key) {
        return lang.getString(key);
    }

    public static String getOr(String key, String defaultVal) {

        if (key == null || lang == null || config == null) return defaultVal;
        Object cached = READ_CACHE.get(key);
        if (cached == null) {
            String val = lang.getString(key);
            if (val == null) {

                val = config.getString(key);
            }
            if (val == null && defaultConfig != null) {

                val = defaultConfig.getString(key);
            }
            cached = val == null ? READ_MISSING : val;
            if (READ_CACHE_COUNT.get() < READ_CACHE_MAX && READ_CACHE.putIfAbsent(key, cached) == null) {
                READ_CACHE_COUNT.incrementAndGet();
            }
        }
        String val = cached == READ_MISSING ? null : (String) cached;
        if (val == null) return defaultVal;
        if (val.isEmpty()) return null;
        return val;
    }

    public static List<String> getList(String key) {
        if (key == null) return Collections.emptyList();
        List<String> cached = LIST_CACHE.get(key);
        if (cached == null) {
            List<String> list = lang.getStringList(key);
            // 缓存实例跨调用共享：不可变包装使潜在的未来篡改调用快速失败，而非静默污染缓存
            cached = list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
            if (READ_CACHE_COUNT.get() < READ_CACHE_MAX && LIST_CACHE.putIfAbsent(key, cached) == null) {
                READ_CACHE_COUNT.incrementAndGet();
            }
        }
        return cached;
    }

    public static String describeError(Throwable error) {
        if (error == null) return "unknown error";
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return error.getClass().getName();
    }

    public static Material getMaterial(String key, Material defaultMat) {
        String name = config.getString(key);
        if (name == null || name.isEmpty()) return defaultMat;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultMat;
        }
    }

    public static int getInt(String key, int defaultVal) {
        return config.getInt(key, defaultVal);
    }

    public static List<Integer> getIntegerList(String key) {
        return config.getIntegerList(key);
    }

    public static String getConfigString(String key, String defaultVal) {
        return config.getString(key, defaultVal);
    }

    public static boolean getConfigBoolean(String key, boolean defaultVal) {
        return config.getBoolean(key, defaultVal);
    }

    public static boolean getBoolean(String key, boolean defaultVal) {
        return config.getBoolean(key, defaultVal);
    }

    public static boolean hasStaleLegacyDelightsOptIn() {
        return config != null && config.getBoolean("compatibility_legacy_delights", false);
    }

    public static double getDouble(String key, double defaultVal) {
        return config.getDouble(key, defaultVal);
    }

    public static List<String> getStringList(String key) {
        List<String> list = config.getStringList(key);
        return list != null ? list : Collections.emptyList();
    }

    public static List<String> getStringOrStringList(String key) {
        if (config.isList(key)) {
            List<String> list = config.getStringList(key);
            return list != null ? list : Collections.emptyList();
        }
        String single = config.getString(key);
        return single != null && !single.isBlank() ? List.of(single) : Collections.emptyList();
    }

    public static List<Map<?, ?>> getMapList(String key) {
        List<Map<?, ?>> list = config.getMapList(key);
        return list != null ? list : Collections.emptyList();
    }

    public static ItemStack buildGuiItem(String configPath, String langPath,
                                         String defaultName, List<String> defaultLore) {
        ItemStack item = buildIconFromConfig(configPath);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemMetaUtil.setDisplayName(meta, getOr(langPath != null ? langPath + "_name" : null, defaultName));
            List<String> lore = langPath != null ? getList(langPath + "_lore") : List.of();
            List<String> effectiveLore = lore.isEmpty() ? defaultLore : lore;

            if (!effectiveLore.isEmpty()) {
                ItemMetaUtil.setLore(meta, effectiveLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack buildIconFromConfig(String configPath) {

        String value = config.getString(configPath);
        if (value != null && !value.isBlank()) {
            return parseIconString(value);
        }

        Material mat = getMaterial(configPath + ".material", Material.BARRIER);
        int cmd = getInt(configPath + ".custom_model_data", 0);
        ItemStack item = new ItemStack(mat);
        if (cmd != 0) {
            ItemMeta meta = item.getItemMeta();
            ItemMetaUtil.applyCustomModelData(meta, cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack parseIconString(String value) {
        if (value.startsWith("ce:")) {
            String ceId = value.substring(3);
            try {
                var def = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(ceId);
                if (def != null) {
                    return def.buildBukkitItem();
                }
            } catch (Throwable ignored) {}

            return new ItemStack(Material.BARRIER);
        }

        Material mat = Material.matchMaterial(value);
        if (mat == null) mat = Material.BARRIER;
        return new ItemStack(mat);
    }

    public record HeatSourceDef(Material material, Map<String, String> states,
                                String ceBlock, String ceBlockTag, boolean conductor, boolean tray,
                                boolean heatSource) {}

    @SuppressWarnings("unchecked")
    public static List<HeatSourceDef> getHeatSources() {
        return heatSources;
    }

    @SuppressWarnings("unchecked")
    private static List<HeatSourceDef> parseHeatSources() {
        List<Map<?, ?>> list = config.getMapList("heat_sources");

        if (list.isEmpty()) {
            org.bukkit.Bukkit.getLogger().warning(ConfigManager.getOr("heat_empty", "[FDE] heat_sources 为空，请检查 config.yml"));
        }
        List<HeatSourceDef> result = new ArrayList<>();
        for (Map<?, ?> map : list) {
            String materialStr = (String) map.get("material");
            Material mat = null;
            if (materialStr != null) {
                try { mat = Material.valueOf(materialStr.toUpperCase()); }
                catch (IllegalArgumentException ignored) {}
            }

            Map<String, String> states = new HashMap<>();
            Object statesObj = map.get("states");
            if (statesObj instanceof Map<?, ?> statesMap) {
                for (Map.Entry<?, ?> e : statesMap.entrySet()) {
                    states.put(String.valueOf(e.getKey()).toLowerCase(),
                            String.valueOf(e.getValue()).toLowerCase());
                }
            }

            String ceBlock = (String) map.get("ce_block");
            String ceBlockTag = (String) map.get("ce_block_tag");
            boolean conductor = Boolean.parseBoolean(
                    String.valueOf(map.containsKey("conductor") ? map.get("conductor") : "false"));
            boolean tray = Boolean.parseBoolean(
                    String.valueOf(map.containsKey("tray") ? map.get("tray") : "false"));
            boolean heatSource = Boolean.parseBoolean(
                    String.valueOf(map.containsKey("heat_source") ? map.get("heat_source") : "true"));

            result.add(new HeatSourceDef(mat, Map.copyOf(states), ceBlock, ceBlockTag, conductor, tray, heatSource));
        }
        return List.copyOf(result);
    }

    public record SoundConfig(String sound, float volume, float pitchMin, float pitchMax) {

        public SoundConfig(String sound) {
            this(sound, 0.8f, 1.0f, 1.0f);
        }

        public SoundConfig(String sound, float volume, float pitch) {
            this(sound, volume, pitch, pitch);
        }

        public float rollPitch() {
            return pitchMin != pitchMax
                    ? pitchMin + (float) Math.random() * (pitchMax - pitchMin)
                    : pitchMin;
        }
    }

    public static SoundConfig readSoundConfig(String path, String defaultSound,
                                              float defaultVolume, float defaultPitch) {
        return readSoundConfig(path, defaultSound, defaultVolume, defaultPitch, defaultPitch);
    }

    public static SoundConfig readSoundConfig(String path, String defaultSound,
                                              float defaultVolume, float defaultPitchMin, float defaultPitchMax) {
        String sound = config.getString(path + ".sound", defaultSound);
        float volume = (float) config.getDouble(path + ".volume", defaultVolume);
        float pMin = (float) config.getDouble(path + ".pitch_min",
                config.getDouble(path + ".pitch", defaultPitchMin));
        float pMax = (float) config.getDouble(path + ".pitch_max", pMin);
        return new SoundConfig(sound, volume, pMin, pMax);
    }

    public static SoundConfig readSoundOrSimple(ConfigurationSection parent, String path) {
        if (parent.isString(path)) {
            return new SoundConfig(parent.getString(path));
        }
        ConfigurationSection sec = parent.getConfigurationSection(path);
        if (sec != null) {
            String snd = sec.getString("sound", "minecraft:block.wood.break");
            float vol = (float) sec.getDouble("volume", 0.8f);
            float pMin = (float) sec.getDouble("pitch_min",
                    sec.getDouble("pitch", 1.0f));
            float pMax = (float) sec.getDouble("pitch_max", pMin);
            return new SoundConfig(snd, vol, pMin, pMax);
        }
        return null;
    }

    public static SoundConfig readConfigSoundOrSimple(String path) {
        return readSoundOrSimple(config, path);
    }

    public static YamlConfiguration getConfig() {
        return config;
    }

    public static void playSound(World world, double x, double y, double z, SoundConfig cfg) {
        if (cfg == null) return;
        Location loc = new Location(world, x, y, z);
        world.playSound(loc, cfg.sound(), cfg.volume(), cfg.rollPitch());
    }

    public static void playSound(World world, double x, double y, double z,
                                 String soundName, float volume, float pitch) {
        Location loc = new Location(world, x, y, z);
        world.playSound(loc, soundName, volume, pitch);
    }

    private static void deleteFilesInDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }
}
