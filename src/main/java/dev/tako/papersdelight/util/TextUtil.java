package dev.tako.papersdelight.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * 解析结果缓存：输入字符串 → 已解析 Component。
     * 仅在本次调用未发生 PAPI 替换（resolved == text）时启用——此时输出是
     * 输入字符串的纯函数；adventure Component 不可变，共享实例安全。
     * 解析不依赖任何配置（无需失效），条目上限 8192 防动态字符串无界增长。
     */
    private static final ConcurrentHashMap<String, Component> PARSE_CACHE = new ConcurrentHashMap<>();
    private static final int PARSE_CACHE_MAX = 8192;
    private static final AtomicInteger PARSE_CACHE_COUNT = new AtomicInteger();

    private static volatile BooleanSupplier papiAvailable = () -> false;

    public static void setPapiAvailability(BooleanSupplier supplier) {
        papiAvailable = supplier != null ? supplier : () -> false;
    }

    private static boolean isPapiAvailable() {
        try {
            return papiAvailable.getAsBoolean();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private TextUtil() {
    }

    /** 基准隔离钩子：清空解析缓存（仅 benchmark 源集调用；生产无失效需求） */
    static void clearParseCacheForBenchmark() {
        PARSE_CACHE.clear();
        PARSE_CACHE_COUNT.set(0);
    }

    public static Component parse(String text) {
        return parse(null, text);
    }

    public static Component parse(Player player, String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        String resolved = text;
        if (player != null && isPapiAvailable() && text.indexOf('%') >= 0) {
            resolved = PlaceholderAPI.setPlaceholders(player, text);
        }

        if (resolved == text) {
            Component cached = PARSE_CACHE.get(resolved);
            if (cached != null) return cached;
            Component parsed = parseUncached(resolved);
            if (PARSE_CACHE_COUNT.get() < PARSE_CACHE_MAX
                    && PARSE_CACHE.putIfAbsent(resolved, parsed) == null) {
                PARSE_CACHE_COUNT.incrementAndGet();
            }
            return parsed;
        }
        return parseUncached(resolved);
    }

    private static Component parseUncached(String resolved) {
        if (hasMiniMessageTag(resolved)) {
            try {
                return MINI_MESSAGE.deserialize(resolved);
            } catch (Exception ignored) {
            }
        }

        return LEGACY.deserialize(resolved.replace("&", "§"));
    }

    /** 与 MINI_MESSAGE_DETECT（&lt;[a-zA-Z#][^&gt;]*&gt;）语义一致的零分配扫描 */
    static boolean hasMiniMessageTag(String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (s.charAt(i) != '<') continue;
            int j = i + 1;
            if (j >= len) return false;
            char n = s.charAt(j);
            if (!((n >= 'a' && n <= 'z') || (n >= 'A' && n <= 'Z') || n == '#')) continue;
            for (int k = j + 1; k < len; k++) {
                if (s.charAt(k) == '>') return true;
            }
            return false;
        }
        return false;
    }

    public static Component parse(CommandSender sender, String text) {
        if (sender instanceof Player player) return parse(player, text);
        return parse(text);
    }

    public static List<Component> parseList(List<String> lines) {
        return parseList(null, lines);
    }

    public static List<Component> parseList(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        return lines.stream().map(line -> parse(player, line)).toList();
    }

    @Deprecated
    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    @Deprecated
    public static List<Component> legacyList(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        return lines.stream().map(TextUtil::legacy).toList();
    }
}
