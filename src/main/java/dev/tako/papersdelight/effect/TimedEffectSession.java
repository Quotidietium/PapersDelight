package dev.tako.papersdelight.effect;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;


/**
 * R10 起由 record 转为可变类（构造器与访问器形状不变，调用点零改动）：
 * 携带标题缓存——formatDuration 为秒级粒度（ticks/20），同一秒内标题不变，
 * 免去每 tick 重建组件树与 equals 树遍历（20 次/秒 → 1 次/秒）。
 * 缓存不变式：标题构建对同一 (秒桶, amplifier) 必须返回等值组件
 * （基类 buildTitle 仅依赖 formatDuration(秒) 与 amplifier，满足；覆写者须保持该性质）。
 */
public final class TimedEffectSession {
    private final BossBar bossBar;
    private final int endTick;
    private final int totalDurationTicks;
    private final int amplifier;

    private @Nullable Component cachedTitle;
    private int cachedTitleSeconds = -1;

    public TimedEffectSession(BossBar bossBar, int endTick, int totalDurationTicks, int amplifier) {
        this.bossBar = bossBar;
        this.endTick = endTick;
        this.totalDurationTicks = totalDurationTicks;
        this.amplifier = amplifier;
    }

    public BossBar bossBar() {
        return bossBar;
    }

    public int endTick() {
        return endTick;
    }

    public int totalDurationTicks() {
        return totalDurationTicks;
    }

    public int amplifier() {
        return amplifier;
    }

    /** 秒桶未变时复用上一次构建的标题；构建经 supplier 调用管理器的（可覆写）buildTitle */
    Component cachedTitle(int remainingTicks, Supplier<Component> builder) {
        int seconds = Math.max(0, remainingTicks / 20);
        if (cachedTitle == null || cachedTitleSeconds != seconds) {
            cachedTitle = builder.get();
            cachedTitleSeconds = seconds;
        }
        return cachedTitle;
    }
}
