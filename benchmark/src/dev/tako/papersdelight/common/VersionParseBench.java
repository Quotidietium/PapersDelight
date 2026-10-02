package dev.tako.papersdelight.common;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;

import java.util.List;

/**
 * 版本门解析成本基准（R7）。
 *
 * MealLoreUtil.supportsTooltipDisplay 原实现每次上菜都执行
 * Bukkit.getMinecraftVersion().split("\\.") + 3 次 parseInt + 比较；R7 起结果
 * 缓存进 volatile Boolean（版本号运行期不变）。ItemStack/Bukkit 路径无法离线
 * 度量，此处以基准本地复刻件量化两侧的差值，即每次调用被消除的解析成本
 * （与 R3 pattern.sideFaces 相同的「基准本地代码、跨标签相同、差值即消除成本」模式）。
 */
public final class VersionParseBench {

    private static final String VERSION = "1.21.4";
    private static final boolean[] CACHED = {true};

    public static void run(List<Result> results) {
        selfCheck();

        results.add(Bench.measure("versionParse[per-call]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(parseEveryCall(VERSION));
            }
        }));
        results.add(Bench.measure("versionParse[cached-read]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(CACHED[0]);
            }
        }));
    }

    /** 原实现复刻：每次调用 split + 3×parseInt + 阈值比较 */
    private static boolean parseEveryCall(String version) {
        String[] parts = version.split("\\.");
        int major = versionPart(parts, 0);
        int minor = versionPart(parts, 1);
        int patch = versionPart(parts, 2);
        return major > 1 || (major == 1 && (minor > 21 || (minor == 21 && patch >= 2)));
    }

    private static int versionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void selfCheck() {
        Bench.check(parseEveryCall("1.21.4"), "1.21.4 supported");
        Bench.check(parseEveryCall("1.21.2"), "1.21.2 supported (boundary)");
        Bench.check(!parseEveryCall("1.21.1"), "1.21.1 not supported (boundary)");
        Bench.check(parseEveryCall("1.22"), "1.22 supported (short string)");
        Bench.check(parseEveryCall("2.0"), "2.0 supported");
        Bench.check(!parseEveryCall("1.20.6"), "1.20.6 not supported");
        Bench.check(!parseEveryCall("garbage"), "non-numeric parts -> false");
    }

    private VersionParseBench() {}
}
