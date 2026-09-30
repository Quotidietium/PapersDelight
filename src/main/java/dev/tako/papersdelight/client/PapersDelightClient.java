package dev.tako.papersdelight.client;

import dev.tako.papersdelight.PapersDelight;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class PapersDelightClient extends JavaPlugin {

    private static PapersDelightClient instance;

    boolean initialized;

    boolean needToDisable;

    public PapersDelightClient() {
        instance = this;
    }

    public static PapersDelightClient getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        instance = this;
        saveDefaultConfig();
        if (!PapersDelight.INSTANCE.earlyInit()) {
            needToDisable = true;
            getLogger().severe(dev.tako.papersdelight.config.ConfigManager.getOr(
                    "startup_early_init_failed",
                    "Plugin initialization failed: CraftEngine early registration did not complete."));
            return;
        }
        initialized = true;
    }

    @Override
    public void onEnable() {
        if (needToDisable || !initialized || !PapersDelight.INSTANCE.enablePhase()) {

            if (isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(this);
            }
            return;
        }
    }

    @Override
    public void onDisable() {
        if (initialized) {
            PapersDelight.INSTANCE.stop();
        }
        instance = null;
        HandlerList.unregisterAll(this);
    }

    public void console(final String info) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', info));
    }
}
