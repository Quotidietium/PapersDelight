package dev.tako.papersdelight.stats;

import org.bukkit.Bukkit;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatsLifecycleListener implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    final JavaPlugin plugin;

    private StatsLifecycleListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new StatsLifecycleListener(plugin), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        StatsManager stats = StatsManager.getInstance();
        if (stats == null) return;
        Player player = event.getPlayer();
        SCHEDULER.getAsyncScheduler().runTask(plugin, () -> stats.warmUp(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        StatsManager stats = StatsManager.getInstance();
        if (stats == null) return;
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        stats.submitQuitWrite(() -> stats.onQuit(uuid));
    }
}
