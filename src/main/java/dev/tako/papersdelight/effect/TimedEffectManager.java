package dev.tako.papersdelight.effect;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public abstract class TimedEffectManager implements Listener {

    private static final long RESTORE_DELAY_TICKS = 20L;

    private static final Map<String, TimedEffectManager> REGISTRY = new ConcurrentHashMap<>();

    protected final JavaPlugin plugin;
    protected final String effectId;
    private final String qualifiedId;
    private final String nameKey;
    private final EffectPdcStore pdcStore;

    private final Map<UUID, TimedEffectSession> sessions = new ConcurrentHashMap<>();


    private boolean enabled = true;
    private BossBar.Color color = BossBar.Color.YELLOW;
    private BossBar.Overlay overlay = BossBar.Overlay.NOTCHED_20;

    private CCTask tickTask;
    private int internalTick;


    protected TimedEffectManager(JavaPlugin plugin, String effectId, String qualifiedId, String nameKey) {
        this.plugin = plugin;
        this.effectId = effectId;
        this.qualifiedId = qualifiedId.toLowerCase(Locale.ROOT);
        this.nameKey = nameKey;
        this.pdcStore = new EffectPdcStore(plugin, effectId);


        REGISTRY.put(this.qualifiedId, this);
    }


    public final String qualifiedId() {
        return qualifiedId;
    }


    public static Collection<TimedEffectManager> registered() {
        return List.copyOf(REGISTRY.values());
    }


    public static TimedEffectManager byQualifiedId(String key) {
        return key == null ? null : REGISTRY.get(key.toLowerCase(Locale.ROOT));
    }


    public boolean isHarmful() {
        return false;
    }


    public boolean isLowPriority() {
        return false;
    }


    protected void configure(boolean enabled, String colorName, String styleName) {
        this.enabled = enabled;
        if (colorName != null) {
            try { this.color = BossBar.Color.valueOf(colorName.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) {}
        }
        if (styleName != null) {
            BossBar.Overlay mapped = mapOverlay(styleName);
            if (mapped != null) this.overlay = mapped;
        }

        if (tickTask == null || tickTask.isCancelled()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            tickTask = CCScheduler.getInstance().getGlobalRegionScheduler().runTaskTimer(plugin, 1L, 2L, this::tick);
        }
    }

    private static BossBar.Overlay mapOverlay(String styleName) {
        return switch (styleName.toUpperCase(Locale.ROOT)) {
            case "SOLID", "PROGRESS" -> BossBar.Overlay.PROGRESS;
            case "SEGMENTED_6", "NOTCHED_6" -> BossBar.Overlay.NOTCHED_6;
            case "SEGMENTED_10", "NOTCHED_10" -> BossBar.Overlay.NOTCHED_10;
            case "SEGMENTED_12", "NOTCHED_12" -> BossBar.Overlay.NOTCHED_12;
            case "SEGMENTED_20", "NOTCHED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> null;
        };
    }


    protected Component buildTitle(int remainingTicks, int amplifier) {
        Component title = Component.translatable(nameKey);
        String roman = romanKey(amplifier);
        if (roman != null) {
            title = title.append(Component.text(" ")).append(Component.translatable(roman));
        }
        return title.append(Component.text(" " + formatDuration(remainingTicks)));
    }

    static String romanKey(int amplifier) {
        if (amplifier <= 0) return null;
        if (amplifier <= 5) return "potion.potency." + amplifier;
        return "enchantment.level." + (amplifier + 1);
    }

    public boolean isEnabled() {
        return enabled;
    }


    public void stopAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            persist(player);
        }
        if (tickTask != null) tickTask.cancel();
        for (Map.Entry<UUID, TimedEffectSession> e : sessions.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue().bossBar());
        }
        sessions.clear();

        REGISTRY.remove(qualifiedId(), this);
    }


    public void applyEffect(Player player, int durationTicks) {
        applyEffect(player, durationTicks, 0);
    }


    public void applyEffect(Player player, int durationTicks, int amplifier) {
        if (!enabled || player == null || durationTicks <= 0) return;

        TimedEffectSession old = sessions.remove(player.getUniqueId());
        if (old != null) player.hideBossBar(old.bossBar());

        int amp = Math.max(0, amplifier);
        int endTick = internalTick + durationTicks;
        BossBar bar = BossBar.bossBar(buildTitle(durationTicks, amp), 1.0f, color, overlay);
        player.showBossBar(bar);
        sessions.put(player.getUniqueId(), new TimedEffectSession(bar, endTick, durationTicks, amp));

        onApply(player, durationTicks, amp);
    }


    public void applyEffectMerging(Player player, int durationTicks, int amplifier) {
        if (player == null) return;
        int mergedDuration = Math.max(durationTicks, getRemainingTicks(player));

        int mergedAmplifier = Math.max(Math.max(0, amplifier), getAmplifier(player));
        applyEffect(player, mergedDuration, mergedAmplifier);
    }


    public enum RemovalCause {

        CONSUMED,

        DEATH
    }


    public void removeEffect(Player player) {
        removeEffect(player, RemovalCause.CONSUMED);
    }


    public void removeEffect(Player player, RemovalCause cause) {
        if (player == null) return;
        TimedEffectSession session = sessions.remove(player.getUniqueId());
        if (session != null) player.hideBossBar(session.bossBar());
        pdcStore.clear(player);
        onRemove(player, cause == null ? RemovalCause.CONSUMED : cause);
    }


    public int getRemainingTicks(Player player) {
        if (player == null) return 0;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        if (session == null) return 0;
        return Math.max(0, session.endTick() - internalTick);
    }


    public boolean isActive(Player player) {
        return player != null && getRemainingTicks(player) > 0;
    }


    public int getTotalTicks(Player player) {
        if (player == null) return 0;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        return session == null ? 0 : session.totalDurationTicks();
    }


    public int getAmplifier(Player player) {
        if (player == null) return -1;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        return session == null ? -1 : session.amplifier();
    }


    public void restoreSession(Player player, int remainingTicks, int totalTicks, int amplifier) {
        if (!enabled || player == null || remainingTicks <= 0) return;

        TimedEffectSession old = sessions.remove(player.getUniqueId());
        if (old != null) player.hideBossBar(old.bossBar());

        int amp = Math.max(0, amplifier);
        int total = Math.max(totalTicks, remainingTicks);
        float progress = (float) Math.min(1.0, (double) remainingTicks / total);
        BossBar bar = BossBar.bossBar(buildTitle(remainingTicks, amp), progress, color, overlay);
        player.showBossBar(bar);
        sessions.put(player.getUniqueId(),
                new TimedEffectSession(bar, internalTick + remainingTicks, total, amp));

        onRestore(player, amp);
    }


    protected void persist(Player player) {
        if (player == null) return;
        int remaining = getRemainingTicks(player);
        if (remaining <= 0) {
            pdcStore.clear(player);
            return;
        }
        pdcStore.save(player, remaining, getTotalTicks(player), getAmplifier(player), serializeExtra(player));
    }


    private void tick() {
        internalTick += 2;
        if (sessions.isEmpty()) return;

        int now = internalTick;
        for (var it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, TimedEffectSession> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            TimedEffectSession session = entry.getValue();

            if (player == null || !player.isOnline()) {

                it.remove();
                continue;
            }

            int remaining = session.endTick() - now;
            if (remaining <= 0) {
                Player expired = player;
                runOnPlayer(player, () -> {
                    expired.hideBossBar(session.bossBar());
                    onExpire(expired);
                });
                it.remove();
                continue;
            }

            UUID playerId = entry.getKey();
            runOnPlayer(player, () -> tickPlayer(player, playerId, session, now));
        }
    }


    private void runOnPlayer(Player player, Runnable task) {
        if (!plugin.isEnabled()) {
            try {
                task.run();
            } catch (Throwable ignored) {
            }
            return;
        }
        CCScheduler.getInstance().getEntityScheduler().runTask(plugin, player, task);
    }


    private void runOnPlayerLater(Player player, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) return;
        CCScheduler.getInstance().getEntityScheduler().runTaskLater(plugin, player, Math.max(1L, delayTicks), task);
    }

    private void tickPlayer(Player player, UUID playerId, TimedEffectSession session, int now) {
        if (sessions.get(playerId) != session) return;
        if (!player.isOnline()) {
            sessions.remove(playerId, session);
            return;
        }

        int remaining = session.endTick() - now;
        if (remaining <= 0) {
            player.hideBossBar(session.bossBar());
            sessions.remove(playerId, session);
            onExpire(player);
            return;
        }

        // R10：标题按秒桶缓存（formatDuration 秒级粒度）——每受效果玩家 20 次组件构建/秒 → 1 次
        Component title = session.cachedTitle(remaining, () -> buildTitle(remaining, session.amplifier()));
        double progress = (double) remaining / Math.max(session.totalDurationTicks(), 1);
        session.bossBar().progress((float) Math.max(0.0, Math.min(1.0, progress)));
        if (!title.equals(session.bossBar().name())) {
            session.bossBar().name(title);
        }

        onEffectTick(player, session.amplifier(), now);
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        persist(player);
        TimedEffectSession session = sessions.remove(player.getUniqueId());
        if (session != null) player.hideBossBar(session.bossBar());
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        runOnPlayerLater(player, () -> {
            if (!player.isOnline()) return;
            EffectPdcRecord record = pdcStore.read(player);
            if (record == null || record.remainingTicks() <= 0) return;
            pdcStore.clear(player);
            restoreSession(player, record.remainingTicks(), record.totalTicks(), record.amplifier());
            deserializeExtra(player, record.extra());
        }, RESTORE_DELAY_TICKS);
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        removeEffect(event.getEntity(), RemovalCause.DEATH);
    }


    protected void onApply(Player player, int durationTicks, int amplifier) {
    }


    protected void onRemove(Player player, RemovalCause cause) {
    }


    protected void onRestore(Player player, int amplifier) {
    }


    protected void onExpire(Player player) {
    }


    protected void onEffectTick(Player player, int amplifier, int now) {
    }


    protected byte[] serializeExtra(Player player) {
        return new byte[0];
    }


    protected void deserializeExtra(Player player, byte[] extra) {
    }


    public static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int rest = seconds % 60;
        return (seconds / 60) + ":" + (rest < 10 ? "0" : "") + rest;
    }
}
