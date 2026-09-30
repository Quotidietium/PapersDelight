package dev.tako.papersdelight.stats;

import dev.tako.papersdelight.mechanic.nourishment.NourishmentManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PapersDelightExpansion extends PlaceholderExpansion {

    private static final String EFFECT_MARKER = "effect_";
    private static final String OF_MARKER = "_of_";

    private static final String SUFFIX_TIME = "_time_remaining";
    private static final String SUFFIX_TIME_FORMATTED = "_time_remaining_formatted";
    private static final String SUFFIX_TIMES = "_times";

    private final Plugin plugin;

    public PapersDelightExpansion(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "papersdelight";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }
    @Override
    public String onRequest(OfflinePlayer requester, String params) {
        StatsManager stats = StatsManager.getInstance();
        if (stats == null || params == null || params.isEmpty()) return null;

        String query = params.toLowerCase();

        int effectAt = query.indexOf(EFFECT_MARKER);
        if (effectAt >= 0) {
            OfflinePlayer target = requester;
            if (effectAt > 0) {

                String name = params.substring(0, effectAt);
                if (!name.endsWith("_")) return null;
                target = resolvePlayer(name.substring(0, name.length() - 1));
                if (target == null) return null;
            }
            return handleEffect(stats, target, query.substring(effectAt + EFFECT_MARKER.length()));
        }

        int ofAt = query.lastIndexOf(OF_MARKER);
        if (ofAt >= 0) {
            OfflinePlayer target = resolvePlayer(params.substring(ofAt + OF_MARKER.length()));
            if (target != null) {
                String body = params.substring(0, ofAt);
                return handleMechanic(stats, target, body.toLowerCase(), body);
            }
        }
        return handleMechanic(stats, requester, query, params);
    }

    private String handleEffect(StatsManager stats, OfflinePlayer target, String rest) {

        if (rest.endsWith(SUFFIX_TIME_FORMATTED)) {
            String effect = strip(rest, SUFFIX_TIME_FORMATTED);
            if (!isKnownEffect(effect)) return null;
            return formatDuration(effect, remainingTicks(target, effect));
        }
        if (rest.endsWith(SUFFIX_TIME)) {
            String effect = strip(rest, SUFFIX_TIME);
            if (!isKnownEffect(effect)) return null;
            return String.valueOf(remainingTicks(target, effect) / 20);
        }
        if (rest.endsWith(SUFFIX_TIMES)) {
            String effect = strip(rest, SUFFIX_TIMES);
            if (!isKnownEffect(effect)) return null;
            return String.valueOf(stats.queryEffectTimes(target.getUniqueId(), effect));
        }
        if (isKnownEffect(rest)) {
            return String.valueOf(remainingTicks(target, rest) > 0);
        }
        return null;
    }

    private static int remainingTicks(OfflinePlayer target, String effect) {
        Player online = target.getPlayer();
        if (online == null) return 0;

        return switch (effect) {
            case NourishmentManager.EFFECT_ID -> {
                NourishmentManager manager = NourishmentManager.getInstance();
                yield manager == null ? 0 : manager.getRemainingTicks(online);
            }
            default -> 0;
        };
    }

    private static String formatDuration(String effect, int ticks) {
        return switch (effect) {
            case NourishmentManager.EFFECT_ID -> NourishmentManager.formatDuration(ticks);
            default -> "0:00";
        };
    }

    private static boolean isKnownEffect(String effect) {
        return NourishmentManager.EFFECT_ID.equals(effect);
    }

    private String handleMechanic(StatsManager stats, OfflinePlayer target,
                                  String lower, String original) {
        for (String stat : new String[]{
                StatsManager.COOKING_POT_COOK,
                StatsManager.SKILLET_COOK,
                StatsManager.CUTTING_BOARD_CUT}) {

            if (lower.equals(stat)) {
                return String.valueOf(stats.query(target.getUniqueId(), stat, ""));
            }
            String prefix = stat + "_";
            if (lower.startsWith(prefix)) {
                String itemId = original.substring(prefix.length());
                if (itemId.isEmpty()) return null;
                return String.valueOf(stats.query(target.getUniqueId(), stat, itemId));
            }
        }
        return null;
    }

    private static String strip(String value, String suffix) {
        return value.substring(0, value.length() - suffix.length());
    }

    @SuppressWarnings("deprecation")
    private static OfflinePlayer resolvePlayer(String name) {
        if (name == null || name.isEmpty()) return null;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

}
