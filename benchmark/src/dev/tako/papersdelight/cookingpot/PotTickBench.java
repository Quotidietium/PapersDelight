package dev.tako.papersdelight.cookingpot;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;

import dev.tako.papersdelight.recipe.CookingRecipe;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.List;

/**
 * 容器 tick 决策路径基准（R3）。
 *
 * 可离线度量的部分：
 *  - potCache.*：烹饪锅每 tick 配方缓存门（lookup/insert，经包私有重载直接驱动；
 *    生产指纹循环只含字段读与整数运算，本基准以等价数值合成指纹）。
 *    与 recipe 组的 recipeTrie.findMatch 对照，即得该缓存门「以 ~10ns 级成本
 *    避免一次 334~525ns 的全量匹配」的性价比。
 *  - pattern.sideFaces：R3 改动点 processHoppers 侧面扫描的两种代码形态
 *    （每次调用 List.of 组包 vs 静态数组迭代）在同一 JVM 内直接对比。
 *    两行均为基准本地代码，跨标签预期持平；其差值即 R3 消除的每次分配成本。
 *
 * 不可离线度量的 R3 改动（产物原型缓存 / 支撑属性节流，需 CE 与服务端运行时）
 * 以调用消除方式论证，见 note/report/perf/03 分析节。
 */
public final class PotTickBench {

    private static final int POTS = 256;
    private static final long EPOCH = 7L;

    /** lookup/insert 为 R3 新增包私有 API：旧 jar（R2 及以前）上跳过缓存门基准，仅保留基准本地的模式对比行 */
    private static boolean cacheApiAvailable() {
        try {
            CookingPotRecipeCache.class.getDeclaredMethod(
                    "lookup", Location.class, long.class, long.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static void run(List<Result> results) {
        if (cacheApiAvailable()) {
            selfCheckCache();
            registerCacheBenches(results);
        }
        registerPatternBenches(results);
    }

    private static void registerCacheBenches(List<Result> results) {
        CookingRecipe[] recipes = new CookingRecipe[POTS];
        for (int i = 0; i < POTS; i++) {
            recipes[i] = new CookingRecipe("bench:r" + i, "bench:result" + i, 1, null,
                    200, 1f, List.of(), "bench");
        }
        Location[] locs = new Location[POTS];
        long[] fps = new long[POTS];
        for (int i = 0; i < POTS; i++) {
            locs[i] = new Location(null, i & 15, 64, i >> 4).toBlockLocation();
            // 与生产指纹同构：材料序数×127 + 数量 + 槽位×8191 的滚动 31 进制组合
            fps[i] = 31L * ((i % 80) * 127 + 3) + (i & 7) * 8191L;
        }
        CookingPotRecipeCache cache = new CookingPotRecipeCache();
        for (int i = 0; i < POTS; i++) {
            cache.insert(locs[i], fps[i], EPOCH, recipes[i]);
        }

        results.add(Bench.measure("potCache.lookup[hit]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                int idx = i & 0xFF;
                bh.consume(cache.lookup(locs[idx], fps[idx], EPOCH));
            }
        }));
        results.add(Bench.measure("potCache.lookup[stale-epoch]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                int idx = i & 0xFF;
                bh.consume(cache.lookup(locs[idx], fps[idx], EPOCH + 1));
            }
        }));
        results.add(Bench.measure("potCache.lookup[input-changed]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                int idx = i & 0xFF;
                bh.consume(cache.lookup(locs[idx], fps[idx] + 1, EPOCH));
            }
        }));
        results.add(Bench.measure("potCache.insert[put]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                int idx = i & 0xFF;
                cache.insert(locs[idx], fps[idx], EPOCH, recipes[idx]);
                bh.consume(idx);
            }
        }));
    }

    private static void registerPatternBenches(List<Result> results) {
        results.add(Bench.measure("pattern.sideFaces[list-of]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                long acc = 0;
                for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH,
                        BlockFace.EAST, BlockFace.WEST)) {
                    acc += face.getModX() + face.getModZ();
                }
                bh.consume(acc);
            }
        }));
        BlockFace[] staticFaces = {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        results.add(Bench.measure("pattern.sideFaces[static-array]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                long acc = 0;
                for (BlockFace face : staticFaces) {
                    acc += face.getModX() + face.getModZ();
                }
                bh.consume(acc);
            }
        }));
    }

    private static void selfCheckCache() {
        CookingPotRecipeCache cache = new CookingPotRecipeCache();
        Location loc = new Location(null, 1, 2, 3).toBlockLocation();
        CookingRecipe recipe = new CookingRecipe("id", "bench:result", 2, null, 100, 1f, List.of(), "bench");

        Bench.check(cache.lookup(loc, 42L, 1L) == CookingPotRecipeCache.CacheResult.MISS,
                "empty cache must miss");
        cache.insert(loc, 42L, 1L, recipe);
        Bench.check(cache.lookup(loc, 42L, 1L) instanceof CookingPotRecipeCache.CacheResult.Hit hit
                        && hit.recipe() == recipe,
                "hit returns same recipe instance");

        cache.insert(loc, 43L, 1L, null);
        Bench.check(cache.lookup(loc, 43L, 1L)
                        instanceof CookingPotRecipeCache.CacheResult.Hit h2 && h2.recipe() == null,
                "sentinel yields hit(null) negative cache");

        Bench.check(cache.lookup(loc, 42L, 2L) == CookingPotRecipeCache.CacheResult.MISS,
                "stale epoch must miss");
        Bench.check(cache.lookup(loc, 44L, 1L) == CookingPotRecipeCache.CacheResult.MISS,
                "changed fingerprint must miss");

        cache.remove(loc);
        Bench.check(cache.lookup(loc, 42L, 1L) == CookingPotRecipeCache.CacheResult.MISS,
                "removed entry must miss");
        cache.insert(loc, 42L, 1L, recipe);
        cache.clear();
        Bench.check(cache.lookup(loc, 42L, 1L) == CookingPotRecipeCache.CacheResult.MISS,
                "cleared cache must miss");

        // 公共 get/put 与 lookup/insert 语义一致（空输入数组 → 空指纹常量 0）
        org.bukkit.inventory.ItemStack[] inputs = new org.bukkit.inventory.ItemStack[6];
        cache.put(loc, inputs, 5L, null);
        Bench.check(cache.get(loc, inputs, 5L) instanceof CookingPotRecipeCache.CacheResult.Hit
                        && cache.get(loc, inputs, 6L) == CookingPotRecipeCache.CacheResult.MISS,
                "public get/put agree with lookup/insert");
    }

    private PotTickBench() {}
}
