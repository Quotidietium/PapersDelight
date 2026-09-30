package dev.tako.papersdelight.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Pattern MINI_MESSAGE_DETECT = Pattern.compile("<[a-zA-Z#][^>]*>");

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

    public static Component parse(String text) {
        return parse(null, text);
    }

    public static Component parse(Player player, String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        String resolved = text;
        if (player != null && isPapiAvailable() && text.indexOf('%') >= 0) {
            resolved = PlaceholderAPI.setPlaceholders(player, text);
        }

        if (MINI_MESSAGE_DETECT.matcher(resolved).find()) {
            try {
                return MINI_MESSAGE.deserialize(resolved);
            } catch (Exception ignored) {
            }
        }

        return LEGACY.deserialize(resolved.replace("&", "§"));
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
