package dev.tako.papersdelight.stats;

import dev.tako.papersdelight.config.ConfigManager;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StatsDatabase implements StatsManager.StatsStore {

    private static final String CREATE_STATS = """
        CREATE TABLE IF NOT EXISTS stats (
            player TEXT    NOT NULL,
            stat   TEXT    NOT NULL,
            detail TEXT    NOT NULL,
            count  INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (player, stat, detail)
        )
        """;

    private static final String UPSERT_STAT = """
        INSERT INTO stats (player, stat, detail, count) VALUES (?, ?, ?, ?)
        ON CONFLICT(player, stat, detail) DO UPDATE SET count = count + excluded.count
        """;

    private static final String SELECT_PLAYER_STATS =
        "SELECT stat, detail, count FROM stats WHERE player = ?";

    private final Plugin plugin;
    private final String url;
    private Connection connection;
    public StatsDatabase(Plugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "stats.db");
        file.getParentFile().mkdirs();
        this.url = "jdbc:sqlite:" + file.getAbsolutePath();
    }

    public boolean open() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(ConfigManager.getOr("stats.sqlite-driver-unavailable", "SQLite 驱动不可用，统计功能已禁用: %error%")
                    .replace("%error%", ConfigManager.describeError(e)));
            return false;
        }

        try {
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {

                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute(CREATE_STATS);
            }
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning(ConfigManager.getOr("stats.database-open-failed", "无法打开 stats.db，统计功能已禁用: %error%")
                    .replace("%error%", ConfigManager.describeError(e)));
            closeQuietly();
            return false;
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {

        } finally {
            connection = null;
        }
    }

    private boolean unavailable() {
        return connection == null;
    }

    @Override
    public void flushStats(Map<StatKey, Long> deltas) {
        if (unavailable() || deltas.isEmpty()) return;

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(UPSERT_STAT)) {
                for (Map.Entry<StatKey, Long> entry : deltas.entrySet()) {
                    StatKey key = entry.getKey();
                    ps.setString(1, key.player().toString());
                    ps.setString(2, key.stat());
                    ps.setString(3, key.detail());
                    ps.setLong(4, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning(ConfigManager.getOr("stats.write-failed", "写入统计数据失败: %error%")
                    .replace("%error%", ConfigManager.describeError(e)));
            try {
                connection.rollback();
            } catch (SQLException ignored) {

            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public Map<StatKey, Long> queryAllStats(UUID player) {
        Map<StatKey, Long> result = new HashMap<>();
        if (unavailable()) return result;

        try (PreparedStatement ps = connection.prepareStatement(SELECT_PLAYER_STATS)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(new StatKey(player, rs.getString(1), rs.getString(2)), rs.getLong(3));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().fine(ConfigManager.getOr("stats.warm-up-failed", "预热玩家统计失败 %player%: %error%")
                    .replace("%player%", player.toString()).replace("%error%", ConfigManager.describeError(e)));
        }
        return result;
    }

    public record StatKey(UUID player, String stat, String detail) {}
}
