package dev.tako.papersdelight.recipe;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.registration.config.AdvancedTagSnapshot;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品标识解析热路径基准（R6）。
 *
 * 可离线度量的部分（两侧 jar 上同名同签名 API，直接可比）：
 *  - idResolve.materialFromId：isItem/createItem/getCraftRemainderId 共用的
 *    原版 id → Material 解析。基线每次调用做 toLowerCase/substring/toUpperCase
 *    分配 + Material.valueOf；候选为进程级 CHM 命中（纯函数，永不失效）。
 *  - idResolve.containsItem：matchesAdvancedTag 尾段的标签成员集查询，
 *    两侧代码相同，作为看守行记录残余成本。
 *  - idResolve.keyParse：Key.of(tagId) 直算成本，即 matchesAdvancedTag
 *    每次调用被 ceKey CHM 替换掉的解析类成本（基准代码两侧相同，预期持平；
 *    与新增行 idResolve.cachedKey 的差值即每次省下的解析）。
 *  - idResolve.cachedKey（新增行，反射探测）：仅候选 jar 存在
 *    DefaultItemMatcherResolver.cachedKeyForBenchmark，度量缓存命中成本；
 *    旧 jar 跳过该行（compare.py 以「新增」呈现）。
 *
 * 不可离线度量的 R6 改动（JugManager.syncInputSlot 先比后克隆，需 Inventory
 * 运行时）以调用消除方式论证，见 note/report/perf/06 分析节。
 */
public final class IdResolutionBench {

    private static final String ID_VANILLA = "iron_ingot";
    private static final String ID_MC_PREFIXED = "minecraft:carrot";
    private static final String ID_MISS_NAMESPACED = "farmersdelight:not_a_thing";
    private static final String ID_MISS_BARE = "not_a_material";

    private static final String TAG_ID = "farmersdelight:healthy_food";
    private static final String MEMBER_ONION = "farmersdelight:onion";
    private static final String MEMBER_TOMATO = "farmersdelight:tomato";
    private static final String MEMBER_RICE = "farmersdelight:rice";
    private static final String NON_MEMBER = "farmersdelight:raw_beef";
    private static final String UNKNOWN_TAG = "farmersdelight:no_such_tag";

    /** R10 isItem CE 分支改写的等价性测试与对比基准所用的 id 集（覆盖命中/裸值/错命名空间/多冒号等形态） */
    private static final String[] IDS_FOR_COMPARE = {
            "farmersdelight:onion", "farmersdelight:tomato", "onion", "farmersdelight:no_such_item",
            "other_ns:onion", "farmersdelight:sub:onion", "not_a_material", "Farmersdelight:Onion",
    };

    /** cachedKeyForBenchmark 为 R6 新增包私有 API：旧 jar 上跳过缓存命中行 */
    private static boolean cachedKeyApiAvailable() {
        try {
            DefaultItemMatcherResolver.class.getDeclaredMethod("cachedKeyForBenchmark", String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static void run(List<Result> results) {
        selfCheck();
        selfCheckIdCompare();

        Key tagKey = Key.of(TAG_ID);
        AdvancedTagSnapshot snapshot = buildSnapshot();

        results.add(Bench.measure("idResolve.materialFromId[hit-vanilla]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(CraftEngineUtil.materialFromId(ID_VANILLA) == Material.IRON_INGOT);
            }
        }));
        results.add(Bench.measure("idResolve.materialFromId[hit-mc-prefixed]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(CraftEngineUtil.materialFromId(ID_MC_PREFIXED) == Material.CARROT);
            }
        }));
        results.add(Bench.measure("idResolve.materialFromId[miss-namespaced]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(CraftEngineUtil.materialFromId(ID_MISS_NAMESPACED) == null);
            }
        }));
        results.add(Bench.measure("idResolve.materialFromId[miss-bare]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(CraftEngineUtil.materialFromId(ID_MISS_BARE) == null);
            }
        }));

        results.add(Bench.measure("idResolve.containsItem[tag-hit]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(snapshot.containsItem(tagKey, MEMBER_ONION));
            }
        }));
        results.add(Bench.measure("idResolve.containsItem[tag-miss]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(snapshot.containsItem(tagKey, NON_MEMBER));
            }
        }));

        results.add(Bench.measure("idResolve.keyParse[ce-key]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(Key.of(TAG_ID));
            }
        }));

        Key customKey = Key.of("farmersdelight:onion");
        results.add(Bench.measure("idResolve.idCompare[toString-equals]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(idCompareToString(IDS_FOR_COMPARE[i & 7], customKey));
            }
        }));
        results.add(Bench.measure("idResolve.idCompare[split-compare]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(idCompareSplit(IDS_FOR_COMPARE[i & 7], customKey));
            }
        }));

        if (cachedKeyApiAvailable()) {
            selfCheckCachedKey();
            results.add(Bench.measure("idResolve.cachedKey[hit]", (bh, ops) -> {
                for (int i = 0; i < ops; i++) {
                    bh.consume(cachedKey(TAG_ID));
                }
            }));
        }
    }

    private static AdvancedTagSnapshot buildSnapshot() {
        Map<Key, List<Key>> tags = new LinkedHashMap<>();
        tags.put(Key.of(TAG_ID), List.of(Key.of(MEMBER_ONION), Key.of(MEMBER_TOMATO), Key.of(MEMBER_RICE)));
        return new AdvancedTagSnapshot(tags);
    }

    /** 反射调用包私有 cachedKeyForBenchmark（与 PotTickBench 的探测模式一致，仅探测通过后使用） */
    private static Key cachedKey(String tagId) {
        try {
            Method m = DefaultItemMatcherResolver.class.getDeclaredMethod("cachedKeyForBenchmark", String.class);
            m.setAccessible(true);
            return (Key) m.invoke(null, tagId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cachedKeyForBenchmark probe lost", e);
        }
    }

    /** 旧实现形态（R10 前）：key.toString() 每次拼接分配 */
    static boolean idCompareToString(String id, Key key) {
        return id.equals(key.toString()) || id.equals(key.value());
    }

    /** 新实现形态（R10）：零分配等价改写（与 CraftEngineUtil.isItem 生产代码同构） */
    static boolean idCompareSplit(String id, Key key) {
        if (id.equals(key.value())) return true;
        int split = id.indexOf(':');
        return split == key.namespace.length()
                && id.length() == split + 1 + key.value.length()
                && id.regionMatches(0, key.namespace, 0, split)
                && id.regionMatches(split + 1, key.value, 0, key.value.length());
    }

    private static void selfCheckIdCompare() {
        Key onion = Key.of("farmersdelight:onion");
        for (String id : IDS_FOR_COMPARE) {
            Bench.check(idCompareToString(id, onion) == idCompareSplit(id, onion),
                    "idCompare equivalence for " + id);
        }
        Bench.check(idCompareToString("farmersdelight:onion", onion)
                && idCompareSplit("farmersdelight:onion", onion), "full id hit both forms");
        Bench.check(idCompareToString("onion", onion) && idCompareSplit("onion", onion), "bare value hit both forms");
        Bench.check(!idCompareSplit("farmersdelight:sub:onion", onion), "multi-colon miss");
        Bench.check(!idCompareSplit("", onion) && !idCompareToString("", onion), "empty id miss");
        // 随机等价性模糊（种子固定可复现）
        java.util.Random rng = new java.util.Random(20261003L);
        String alphabet = "abcdeilnorstu_:ABC";
        for (int i = 0; i < 10_000; i++) {
            int len = 1 + rng.nextInt(28);
            StringBuilder sb = new StringBuilder(len);
            for (int c = 0; c < len; c++) sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
            String probe = sb.toString();
            Bench.check(idCompareToString(probe, onion) == idCompareSplit(probe, onion),
                    "idCompare fuzz mismatch for " + probe);
        }
    }

    private static void selfCheck() {
        // materialFromId 语义（含 minecraft: 前缀剥离与混合大小写行为保持）
        Bench.check(CraftEngineUtil.materialFromId(ID_VANILLA) == Material.IRON_INGOT, "vanilla hit");
        Bench.check(CraftEngineUtil.materialFromId(ID_MC_PREFIXED) == Material.CARROT, "mc-prefixed hit");
        Bench.check(CraftEngineUtil.materialFromId("Minecraft:Carrot") == Material.CARROT, "mixed-case prefix strip");
        Bench.check(CraftEngineUtil.materialFromId("minecraft:air") == Material.AIR, "air is a real material, not a miss");
        Bench.check(CraftEngineUtil.materialFromId(ID_MISS_NAMESPACED) == null, "namespaced miss");
        Bench.check(CraftEngineUtil.materialFromId(ID_MISS_BARE) == null, "bare miss");
        Bench.check(CraftEngineUtil.materialFromId("") == null && CraftEngineUtil.materialFromId(null) == null,
                "null/empty guard");
        Bench.check(CraftEngineUtil.materialFromId(ID_VANILLA) == Material.IRON_INGOT,
                "repeat call stable (cache identity class)");

        // containsItem 语义
        AdvancedTagSnapshot snapshot = buildSnapshot();
        Key tagKey = Key.of(TAG_ID);
        Bench.check(snapshot.containsItem(tagKey, MEMBER_ONION), "containsItem hit");
        Bench.check(!snapshot.containsItem(tagKey, NON_MEMBER), "containsItem non-member miss");
        Bench.check(!snapshot.containsItem(Key.of(UNKNOWN_TAG), MEMBER_ONION), "containsItem unknown tag");
        Bench.check(!snapshot.containsItem(null, MEMBER_ONION) && !snapshot.containsItem(tagKey, null)
                && !snapshot.containsItem(tagKey, ""), "containsItem null/empty guards");
        Bench.check(snapshot.containsItem(tagKey, "FARMERSDELIGHT:ONION"),
                "containsItem folds uppercase item id (toLowerCase contract)");

        // Key.of 纯解析（离线可用性守卫：CE core 静态初始化不依赖服务端）
        Bench.check(TAG_ID.equals(Key.of(TAG_ID).asString()), "Key.of round-trip");
    }

    private static void selfCheckCachedKey() {
        Key first = cachedKey(TAG_ID);
        Key second = cachedKey(TAG_ID);
        Bench.check(first != null && first.equals(Key.of(TAG_ID)), "cachedKey equals Key.of");
        Bench.check(first == second, "cachedKey returns same instance (CHM hit)");
    }

    private IdResolutionBench() {}
}
