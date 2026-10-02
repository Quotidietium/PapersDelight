package dev.tako.papersdelight.config;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import java.util.List;

/**
 * ConfigManager.getOr / getList 读穿缓存基准。
 * 通过反射安装离线 YamlConfiguration（lang 200 键 / config 300 键 / defaultConfig 150 键），
 * 覆盖四级回退（lang → config → defaultConfig → 默认值）与空串返回 null 的边界。
 * 运行期配置只读已审计（详见 ConfigManager.READ_CACHE 注释），缓存语义与逐次查找完全一致。
 */
public final class ConfigGetOrBench {

    static void install(String langYaml, String configYaml, String defaultYaml) throws Exception {
        set("lang", load(langYaml));
        set("config", load(configYaml));
        set("defaultConfig", load(defaultYaml));
        clearCache();
    }

    private static YamlConfiguration load(String yaml) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return cfg;
    }

    private static void set(String field, Object value) throws Exception {
        Field f = ConfigManager.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }

    /** 反射清空读缓存：模拟 load() 末尾的失效，供自检使用。基线版本无该字段时静默跳过。 */
    static void clearCache() {
        try {
            for (String name : new String[]{"READ_CACHE", "LIST_CACHE"}) {
                Field f = ConfigManager.class.getDeclaredField(name);
                f.setAccessible(true);
                ((java.util.concurrent.ConcurrentHashMap<?, ?>) f.get(null)).clear();
            }
            Field c = ConfigManager.class.getDeclaredField("READ_CACHE_COUNT");
            c.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger) c.get(null)).set(0);
        } catch (NoSuchFieldException ignored) {
            // 基线版本没有读缓存 —— 无需清理
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static String buildLangYaml() {
        StringBuilder sb = new StringBuilder(8192);
        for (int i = 0; i < 200; i++) sb.append("msg.i").append(i).append(": \"语言值").append(i).append("\"\n");
        sb.append("msg.empty: \"\"\n");
        sb.append("msg.list:\n");
        for (int i = 0; i < 6; i++) sb.append("  - \"条目").append(i).append("\"\n");
        return sb.toString();
    }

    static String buildConfigYaml() {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("cooking_pot:\n  display:\n");
        for (int i = 0; i < 150; i++) sb.append("    icon_").append(i).append(": \"cfg:").append(i).append("\"\n");
        sb.append("skillet:\n  msg:\n");
        for (int i = 0; i < 150; i++) sb.append("    m_").append(i).append(": \"锅").append(i).append("\"\n");
        return sb.toString();
    }

    static String buildDefaultYaml() {
        StringBuilder sb = new StringBuilder(8192);
        for (int i = 0; i < 150; i++) sb.append("fallback.k").append(i).append(": \"默认").append(i).append("\"\n");
        return sb.toString();
    }

    public static void run(List<Result> results) throws Exception {
        selfCheck();
        install(buildLangYaml(), buildConfigYaml(), buildDefaultYaml());

        results.add(Bench.measure("configManager.getOr[lang-hit,depth1]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(ConfigManager.getOr("msg.i" + (i % 200), "dft"));
            }
        }));
        results.add(Bench.measure("configManager.getOr[config-hit,depth3]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(ConfigManager.getOr("cooking_pot.display.icon_" + (i % 150), "dft"));
            }
        }));
        results.add(Bench.measure("configManager.getOr[default-hit,depth3]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(ConfigManager.getOr("fallback.k" + (i % 150), "dft"));
            }
        }));
        results.add(Bench.measure("configManager.getOr[miss->default,dynamic-key]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(ConfigManager.getOr("no.such.key_" + i, "dft"));
            }
        }));
        // 动态键已把缓存打满（上限保护生效）；此处重置以反映生产环境 load() 后的干净缓存，
        // 静态 miss 命中 MISSING 哨兵走缓存路径
        clearCache();
        results.add(Bench.measure("configManager.getOr[miss->default,static-key]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(ConfigManager.getOr("no.such.key", "dft"));
            }
        }));
        results.add(Bench.measure("configManager.getList[lang-hit]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(ConfigManager.getList("msg.list").size());
            }
        }));
    }

    private static void selfCheck() throws Exception {
        install(buildLangYaml(), buildConfigYaml(), buildDefaultYaml());

        Bench.check("语言值7".equals(ConfigManager.getOr("msg.i7", "dft")), "lang hit");
        Bench.check("cfg:42".equals(ConfigManager.getOr("cooking_pot.display.icon_42", "dft")), "config hit");
        Bench.check("默认13".equals(ConfigManager.getOr("fallback.k13", "dft")), "default hit");
        Bench.check("dft".equals(ConfigManager.getOr("no.such.key", "dft")), "miss -> default");
        Bench.check(ConfigManager.getOr("msg.empty", "dft") == null, "empty -> null");
        Bench.check(ConfigManager.getList("msg.list").size() == 6, "list hit");
        Bench.check(ConfigManager.getList("no.list").isEmpty(), "list miss empty");
        Bench.check(ConfigManager.getOr(null, "dft") == null ? false : "dft".equals(ConfigManager.getOr(null, "dft")),
                "null key -> default");

        // captureState / restoreState 往返：读缓存必须随之失效，值与往返前一致
        ConfigManager.State state = ConfigManager.captureState();
        set("lang", load("msg.i7: \"被改写了\"\n"));
        clearCache();
        Bench.check("被改写了".equals(ConfigManager.getOr("msg.i7", "dft")), "post-mutation value visible");
        ConfigManager.restoreState(state);
        Bench.check("语言值7".equals(ConfigManager.getOr("msg.i7", "dft")), "restoreState invalidates cache");

        // 上界保护：大量动态键不得令缓存无界增长
        for (int i = 0; i < 20_000; i++) {
            ConfigManager.getOr("dynamic.burst." + i, "dft");
        }
        try {
            Field f = ConfigManager.class.getDeclaredField("READ_CACHE");
            f.setAccessible(true);
            int size = ((java.util.concurrent.ConcurrentHashMap<?, ?>) f.get(null)).size();
            Bench.check(size <= 8192, "read cache bounded: " + size);
        } catch (NoSuchFieldException ignored) {
            // 基线无缓存
        }
    }

    private ConfigGetOrBench() {}
}
