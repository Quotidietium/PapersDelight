package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.util.TextUtil;
import dev.tako.papersdelight.config.ConfigManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JugUnavailableNotice {

    static final long COOLDOWN_MILLIS = 10_000L;

    private static final String LANG_KEY = "jug_requires_libuid";
    private static final String FALLBACK = "Libuid is not available; the jug mechanic is disabled.";

    private static final Map<UUID, Long> lastNotified = new ConcurrentHashMap<>();

    private JugUnavailableNotice() {
    }

    public static void notifyUnavailable(@Nullable Player player) {
        if (player == null) return;
        if (!shouldNotify(player.getUniqueId(), System.currentTimeMillis())) return;
        player.sendMessage(TextUtil.parse(player, ConfigManager.getOr(LANG_KEY, FALLBACK)));
    }

    static boolean shouldNotify(@Nullable UUID playerId, long now) {
        if (playerId == null) return false;
        Long previous = lastNotified.get(playerId);

        if (previous != null && now >= previous && now - previous < COOLDOWN_MILLIS) return false;
        prune(now);
        lastNotified.put(playerId, now);
        return true;
    }

    private static void prune(long now) {
        lastNotified.entrySet().removeIf(entry -> {
            long recorded = entry.getValue();
            return now < recorded || now - recorded >= COOLDOWN_MILLIS;
        });
    }

    static void reset() {
        lastNotified.clear();
    }

    static int trackedPlayers() {
        return lastNotified.size();
    }
}
