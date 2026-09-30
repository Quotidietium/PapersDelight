package dev.tako.papersdelight.stats;

import dev.tako.papersdelight.config.ConfigManager;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.stats.StatsDatabase.StatKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class StatsManager {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    public static final String COOKING_POT_COOK   = "cooking_pot_cook";
    public static final String SKILLET_COOK       = "skillet_cook";
    public static final String CUTTING_BOARD_CUT  = "cutting_board_cut";

    public static final String EFFECT_TIMES_PREFIX = "effect_";
    public static final String EFFECT_TIMES_SUFFIX = "_times";

    private static final String TOTAL = "";

    private static final long DEFAULT_SHUTDOWN_WAIT_MILLIS = 5_000L;

    private static final long DEFAULT_IO_WAIT_MILLIS = 100L;

    private static final long MIN_IO_WAIT_MILLIS = 20L;
    private static final long MAX_IO_WAIT_MILLIS = 500L;

    private static StatsManager instance;

    public interface StatsStore {
        void flushStats(Map<StatKey, Long> deltas);

        Map<StatKey, Long> queryAllStats(UUID player);

        void close();
    }

    @FunctionalInterface
    public interface AsyncRunner {
        void submit(Runnable task);
    }

    private record StoreResult<T>(boolean executed, T value) {
        static <T> StoreResult<T> executed(T value) {
            return new StoreResult<>(true, value);
        }

        static <T> StoreResult<T> refused(T fallback) {
            return new StoreResult<>(false, fallback);
        }
    }

    final JavaPlugin plugin;
    private final StatsStore database;
    private final AsyncRunner asyncRunner;

    private final long shutdownWaitMillis;

    private final long ioWaitMillis;

    private final ReentrantLock ioLock = new ReentrantLock();

    private final Object writeBarrier = new Object();
    private int pendingWrites;

    private final Object closeGate = new Object();
    private boolean storeClosed;
    private boolean closeRequested;

    private final Map<StatKey, Long> totals = new ConcurrentHashMap<>();

    private final Map<StatKey, Long> pending = new ConcurrentHashMap<>();

    private final Map<UUID, Boolean> warmed = new ConcurrentHashMap<>();

    private CCTask flushTask;

    private final AtomicBoolean closing = new AtomicBoolean();

    private StatsManager(JavaPlugin plugin, StatsStore database,
                         AsyncRunner asyncRunner, long shutdownWaitMillis, long ioWaitMillis) {
        this.plugin = plugin;
        this.database = database;
        this.asyncRunner = asyncRunner;
        this.shutdownWaitMillis = shutdownWaitMillis;
        this.ioWaitMillis = clampIoWait(ioWaitMillis);
    }

    private static long clampIoWait(long millis) {
        return Math.max(MIN_IO_WAIT_MILLIS, Math.min(MAX_IO_WAIT_MILLIS, millis));
    }

    public static StatsManager start(JavaPlugin plugin) {
        stop();

        if (!ConfigManager.getConfigBoolean("stats.enable", true)) {
            plugin.getLogger().info(ConfigManager.getOr("stats.disabled-in-config", "统计功能已在配置中关闭"));
            return null;
        }

        StatsDatabase database = new StatsDatabase(plugin);
        if (!database.open()) return null;

        long shutdownWait = Math.max(200L, ConfigManager.getInt(
                "stats.shutdown_wait_millis", (int) DEFAULT_SHUTDOWN_WAIT_MILLIS));
        long ioWait = ConfigManager.getInt(
                "stats.io_wait_millis", (int) DEFAULT_IO_WAIT_MILLIS);
        StatsManager manager = new StatsManager(plugin, database, task -> {
            if (!plugin.isEnabled()) {
                try { task.run(); } catch (Throwable ignored) { }
                return;
            }
            SCHEDULER.getAsyncScheduler().runTask(plugin, task);
        }, shutdownWait, ioWait);

        int interval = Math.max(5, ConfigManager.getInt("stats.flush_interval", 30));
        manager.flushTask = SCHEDULER.getAsyncScheduler().runTaskTimer(
            plugin, manager::flush, interval * 20L, interval * 20L);

        instance = manager;
        plugin.getLogger().info(ConfigManager.getOr("stats.enabled", "统计功能已启用（flush 间隔 %interval% 秒）")
                .replace("%interval%", String.valueOf(interval)));
        return manager;
    }

    public static void stop() {
        StatsManager manager = instance;
        instance = null;
        if (manager == null) return;

        if (manager.flushTask != null) {
            manager.flushTask.cancel();
            manager.flushTask = null;
        }

        long deadline = manager.beginShutdown();
        try {
            manager.awaitPendingWrites(deadline);
            manager.flush();
        } catch (Throwable throwable) {

            manager.warn(ConfigManager.getOr("stats.shutdown-flush-failed", "关服前落盘统计数据失败: %error%")
                    .replace("%error%", ConfigManager.describeError(throwable)));
        } finally {
            manager.closeStore(deadline);
        }
    }

    private long beginShutdown() {
        long existing = shutdownDeadlineNanos;
        if (existing != 0L) return existing;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownWaitMillis);
        shutdownDeadlineNanos = deadline;
        return deadline;
    }

    public static void beginShutdownWindow() {
        StatsManager manager = instance;
        if (manager != null) manager.beginShutdown();
    }

    private volatile long shutdownDeadlineNanos;

    private long budgetedWaitMillis() {
        long deadline = shutdownDeadlineNanos;
        if (deadline == 0L) return ioWaitMillis;
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        return Math.max(0L, Math.min(shutdownWaitMillis, remaining));
    }

    private void awaitPendingWrites(long deadline) {
        synchronized (writeBarrier) {
            closing.set(true);
            while (pendingWrites > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    warn(ConfigManager.getOr("stats.pending-writes-timeout", "等待统计写入完成超时（%wait% ms 总预算），仍有 %count% 个在途写入，可能丢失部分统计数据")
                            .replace("%wait%", String.valueOf(shutdownWaitMillis)).replace("%count%", String.valueOf(pendingWrites)));
                    return;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(writeBarrier, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    warn(ConfigManager.getOr("stats.pending-writes-interrupted", "等待统计写入完成时被中断，可能丢失部分统计数据"));
                    return;
                }
            }
        }
    }

    private void closeStore(long deadline) {
        long waitMillis = Math.max(0L,
                Math.min(shutdownWaitMillis, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
        boolean locked = false;
        boolean interrupted = false;
        try {
            locked = ioLock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interrupted = true;
        }

        if (locked) {
            try {
                closeStoreLocked();
            } finally {
                ioLock.unlock();
            }
            return;
        }

        boolean needsHandoff;
        synchronized (closeGate) {
            closeRequested = true;
            needsHandoff = !storeClosed;
        }
        if (!needsHandoff) return;

        warn(interrupted
                ? ConfigManager.getOr("stats.close-lock-interrupted", "关闭统计数据库前等待 IO 锁被中断，已安排后台线程在写入完成后关闭连接")
                : ConfigManager.getOr("stats.close-lock-timeout", "关闭统计数据库前未能取得 IO 锁（超时 %wait% ms），已安排在写入完成后关闭连接")
                        .replace("%wait%", String.valueOf(waitMillis)));
        startCloseWatchdog();
    }

    private void startCloseWatchdog() {
        Thread watchdog = new Thread(() -> {
            ioLock.lock();
            try {
                closeStoreLocked();
            } finally {
                ioLock.unlock();
            }
        }, "PapersDelight-stats-close");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void closeStoreLocked() {
        synchronized (closeGate) {
            if (storeClosed) return;
            storeClosed = true;
            closeRequested = false;
        }
        database.close();
    }

    private <T> StoreResult<T> withStore(String action, java.util.function.Supplier<T> io, T refusedValue) {
        long waitMillis = budgetedWaitMillis();
        boolean locked;
        try {
            locked = ioLock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warn(ConfigManager.getOr("stats.store-lock-interrupted", "等待统计数据库锁时被中断，跳过%action%")
                    .replace("%action%", action));
            return StoreResult.refused(refusedValue);
        }
        if (!locked) {
            warn(ConfigManager.getOr("stats.store-lock-timeout", "等待统计数据库锁超时（%wait% ms），跳过%action%")
                    .replace("%wait%", String.valueOf(waitMillis)).replace("%action%", action));
            return StoreResult.refused(refusedValue);
        }

        try {
            synchronized (closeGate) {
                if (storeClosed) {
                    warn(ConfigManager.getOr("stats.store-closed", "统计数据库已关闭，跳过%action%")
                            .replace("%action%", action));
                    return StoreResult.refused(refusedValue);
                }
            }
            return StoreResult.executed(io.get());
        } finally {
            try {
                boolean takeOver;
                synchronized (closeGate) {
                    takeOver = closeRequested && !storeClosed;
                }
                if (takeOver) closeStoreLocked();
            } finally {
                ioLock.unlock();
            }
        }
    }

    private boolean withStore(String action, Runnable io) {
        return withStore(action, () -> {
            io.run();
            return null;
        }, null).executed();
    }

    public void submitQuitWrite(Runnable write) {
        if (write == null) return;

        boolean registered;
        synchronized (writeBarrier) {
            registered = !closing.get();
            if (registered) pendingWrites++;
        }
        if (!registered) {
            runGuarded(write);
            return;
        }

        boolean submitted = false;
        try {
            asyncRunner.submit(() -> {
                try {
                    runGuarded(write);
                } finally {
                    releaseWrite();
                }
            });
            submitted = true;
        } catch (Throwable throwable) {
            warn(ConfigManager.getOr("stats.submit-write-failed", "提交统计写入任务失败，改为当前线程执行: %error%")
                    .replace("%error%", ConfigManager.describeError(throwable)));
        }
        if (!submitted) {
            try {
                runGuarded(write);
            } finally {
                releaseWrite();
            }
        }
    }

    private void releaseWrite() {
        synchronized (writeBarrier) {
            pendingWrites--;
            if (pendingWrites <= 0) writeBarrier.notifyAll();
        }
    }

    private void runGuarded(Runnable write) {
        try {
            write.run();
        } catch (Throwable throwable) {
            warn(ConfigManager.getOr("stats.write-task-failed", "统计写入任务执行失败: %error%")
                    .replace("%error%", ConfigManager.describeError(throwable)));
        }
    }

    private void warn(String message) {
        if (plugin != null) plugin.getLogger().warning(message);
    }

    public static StatsManager getInstance() {
        return instance;
    }

    public void record(Player player, String stat, String itemId, long amount) {
        if (player == null || stat == null || amount <= 0) return;
        record(player.getUniqueId(), stat, itemId, amount);
    }

    public void record(UUID uuid, String stat, String itemId, long amount) {
        if (uuid == null || stat == null || amount <= 0) return;

        add(new StatKey(uuid, stat, TOTAL), amount);
        if (itemId != null && !itemId.isEmpty()) {
            add(new StatKey(uuid, stat, itemId), amount);
        }
    }

    public void recordEffectApply(Player player, String effect) {
        if (player == null || effect == null) return;
        add(new StatKey(player.getUniqueId(), effectStat(effect), TOTAL), 1L);
    }

    private void add(StatKey key, long amount) {
        totals.merge(key, amount, Long::sum);
        pending.merge(key, amount, Long::sum);
    }

    public long query(UUID player, String stat, String detail) {
        if (player == null || stat == null) return 0L;

        StatKey key = new StatKey(player, stat, detail == null ? TOTAL : detail);
        Long cached = totals.get(key);
        if (cached != null) return cached;

        scheduleWarmUp(player);
        return 0L;
    }

    private void scheduleWarmUp(UUID player) {
        if (player == null || warmed.putIfAbsent(player, Boolean.TRUE) != null) return;
        try {
            asyncRunner.submit(() -> warmUpLoaded(player));
        } catch (Throwable throwable) {
            warmed.remove(player);
            warn(ConfigManager.getOr("stats.submit-warm-up-failed", "提交统计预热任务失败: %error%")
                    .replace("%error%", ConfigManager.describeError(throwable)));
        }
    }

    public long queryEffectTimes(UUID player, String effect) {
        return query(player, effectStat(effect), TOTAL);
    }

    private static String effectStat(String effect) {
        return EFFECT_TIMES_PREFIX + effect + EFFECT_TIMES_SUFFIX;
    }

    public void flush() {
        if (pending.isEmpty()) return;

        Map<StatKey, Long> batch = new HashMap<>();
        for (StatKey key : List.copyOf(pending.keySet())) {
            Long delta = pending.remove(key);
            if (delta != null && delta > 0) batch.put(key, delta);
        }
        if (batch.isEmpty()) return;

        if (!withStore(ConfigManager.getOr("stats.action-flush", "统计落盘"), () -> database.flushStats(batch))) {

            batch.forEach((key, delta) -> pending.merge(key, delta, Long::sum));
            warn(ConfigManager.getOr("stats.flush-skipped", "统计落盘被跳过，已将 %count% 项增量合回待落盘队列等待重试")
                    .replace("%count%", String.valueOf(batch.size())));
        }
    }

    public void warmUp(UUID player) {
        if (player == null || warmed.putIfAbsent(player, Boolean.TRUE) != null) return;
        warmUpLoaded(player);
    }

    private void warmUpLoaded(UUID player) {
        StoreResult<Map<StatKey, Long>> stored = withStore(ConfigManager.getOr("stats.action-warm-up", "统计预热"),
                () -> database.queryAllStats(player), Map.of());
        if (!stored.executed()) {

            warmed.remove(player);
            return;
        }
        totals.putAll(stored.value());
    }

    public void onQuit(UUID player) {
        flush();
        if (player == null) return;
        warmed.remove(player);
        totals.keySet().removeIf(key -> key.player().equals(player));
    }

}
