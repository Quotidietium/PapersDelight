package dev.tako.papersdelight.heat;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;
import dev.tako.papersdelight.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * HeatSourceService.matchesBlockDef / checkLit 基准。
 * 用 JDK 动态代理伪造 Block/BlockData/Lightable（getType、getAsString、isLit 受控返回），
 * 覆盖：纯 material 快路径、单状态、双状态、材质不符早退、checkLit 的 Lightable 分支。
 * 代理调用开销在基线/候选两侧完全一致，差值只反映被测实现本身。
 */
public final class HeatSourceBench {

    static Block blockOf(Material type, String blockDataString, Boolean lit) {
        BlockData data = (BlockData) Proxy.newProxyInstance(
                HeatSourceBench.class.getClassLoader(),
                new Class<?>[]{BlockData.class, Lightable.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getAsString": return blockDataString;
                        case "isLit": return lit;
                        case "getMaterial": return type;
                        default: return null;
                    }
                });
        return (Block) Proxy.newProxyInstance(
                HeatSourceBench.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getType": return type;
                        case "getBlockData": return data;
                        default: return null;
                    }
                });
    }

    static final String CAMPFIRE_DATA = "minecraft:campfire[lit=true,facing=north,waterlogged=false]";
    static final String COLD_CAMPFIRE_DATA = "minecraft:campfire[lit=false,facing=north,waterlogged=false]";

    public static void run(List<Result> results) {
        selfCheck();

        Block campfire = blockOf(Material.CAMPFIRE, CAMPFIRE_DATA, true);
        ConfigManager.HeatSourceDef plain = new ConfigManager.HeatSourceDef(
                Material.CAMPFIRE, Map.of(), null, null, false, false, true);
        ConfigManager.HeatSourceDef litOnly = new ConfigManager.HeatSourceDef(
                Material.CAMPFIRE, Map.of("lit", "true"), null, null, false, false, true);
        ConfigManager.HeatSourceDef litFacing = new ConfigManager.HeatSourceDef(
                Material.CAMPFIRE, Map.of("lit", "true", "facing", "north"), null, null, false, false, true);
        ConfigManager.HeatSourceDef wrongMat = new ConfigManager.HeatSourceDef(
                Material.STONE, Map.of("lit", "true"), null, null, false, false, true);

        results.add(Bench.measure("heat.matchesBlockDef[material-only]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(HeatSourceService.matchesBlockDef(campfire, plain));
            }
        }));
        results.add(Bench.measure("heat.matchesBlockDef[1-state]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(HeatSourceService.matchesBlockDef(campfire, litOnly));
            }
        }));
        results.add(Bench.measure("heat.matchesBlockDef[2-states]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(HeatSourceService.matchesBlockDef(campfire, litFacing));
            }
        }));
        results.add(Bench.measure("heat.matchesBlockDef[material-miss]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(HeatSourceService.matchesBlockDef(campfire, wrongMat));
            }
        }));
        results.add(Bench.measure("heat.checkLit[lightable]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(HeatSourceService.checkLit(campfire));
            }
        }));
    }

    private static void selfCheck() {
        Block lit = blockOf(Material.CAMPFIRE, CAMPFIRE_DATA, true);
        Block cold = blockOf(Material.CAMPFIRE, COLD_CAMPFIRE_DATA, false);

        ConfigManager.HeatSourceDef plain = new ConfigManager.HeatSourceDef(
                Material.CAMPFIRE, Map.of(), null, null, false, false, true);
        ConfigManager.HeatSourceDef litOnly = new ConfigManager.HeatSourceDef(
                Material.CAMPFIRE, Map.of("lit", "true"), null, null, false, false, true);
        ConfigManager.HeatSourceDef litFacing = new ConfigManager.HeatSourceDef(
                Material.CAMPFIRE, Map.of("lit", "true", "facing", "north"), null, null, false, false, true);
        ConfigManager.HeatSourceDef facingEast = new ConfigManager.HeatSourceDef(
                Material.CAMPFIRE, Map.of("facing", "east"), null, null, false, false, true);
        ConfigManager.HeatSourceDef stoneLit = new ConfigManager.HeatSourceDef(
                Material.STONE, Map.of("lit", "true"), null, null, false, false, true);

        Bench.check(HeatSourceService.matchesBlockDef(lit, plain), "material-only hit");
        Bench.check(HeatSourceService.matchesBlockDef(lit, litOnly), "1-state hit");
        Bench.check(HeatSourceService.matchesBlockDef(lit, litFacing), "2-states hit");
        Bench.check(!HeatSourceService.matchesBlockDef(lit, facingEast), "state value miss");
        Bench.check(!HeatSourceService.matchesBlockDef(lit, stoneLit), "material miss");
        // 未点亮营火：lit=false 数据串下 lit=true 谓词必须失配
        Bench.check(!HeatSourceService.matchesBlockDef(cold, litOnly), "unlit miss by states");
        Bench.check(HeatSourceService.checkLit(lit), "checkLit lit");
        Bench.check(!HeatSourceService.checkLit(cold), "checkLit unlit");
    }

    private HeatSourceBench() {}
}
