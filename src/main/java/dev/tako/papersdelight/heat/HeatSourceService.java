package dev.tako.papersdelight.heat;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import dev.tako.papersdelight.config.ConfigManager;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HeatSourceService {

    private HeatSourceService() {}

    public static boolean isActiveHeatSource(Block block) {
        if (block == null) return false;
        List<ConfigManager.HeatSourceDef> heatSources = ConfigManager.getHeatSources();
        for (ConfigManager.HeatSourceDef def : heatSources) {
            if (!def.heatSource() || def.conductor()) continue;
            if (matchesBlockDef(block, def) && checkLit(block)) return true;
        }
        return false;
    }

    public static boolean isHeated(Block block) {
        if (block == null) return false;
        List<ConfigManager.HeatSourceDef> heatSources = ConfigManager.getHeatSources();
        Block below = block.getRelative(BlockFace.DOWN);
        for (ConfigManager.HeatSourceDef definition : heatSources) {
            if (!definition.heatSource()) continue;
            if (!definition.conductor() && isMatchingHeatSource(below, definition)) return true;
        }
        for (ConfigManager.HeatSourceDef definition : heatSources) {
            if (!definition.heatSource()) continue;
            if (!definition.conductor() || !matchesBlockDef(below, definition)) continue;
            Block twoBelow = block.getRelative(BlockFace.DOWN, 2);
            for (ConfigManager.HeatSourceDef heat : heatSources) {
                if (!heat.heatSource()) continue;
                if (!heat.conductor() && isMatchingHeatSource(twoBelow, heat)) return true;
            }
            break;
        }
        return false;
    }

    private static boolean isMatchingHeatSource(Block block, ConfigManager.HeatSourceDef definition) {
        return matchesBlockDef(block, definition) && checkLit(block);
    }

    public static boolean matchesBlockDef(Block block, ConfigManager.HeatSourceDef definition) {
        if (definition.ceBlock() != null) return CraftEngineUtil.isCustomBlock(block, definition.ceBlock());
        if (definition.ceBlockTag() != null) return isCustomBlockTagged(block, definition.ceBlockTag());
        if (definition.material() == null || block.getType() != definition.material()) return false;
        if (definition.states().isEmpty()) return true;
        String data = block.getBlockData().getAsString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> state : definition.states().entrySet()) {
            if (!data.contains(state.getKey().toLowerCase(Locale.ROOT) + "=" + state.getValue().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCustomBlockTagged(Block block, String tagName) {
        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) return false;
            return state.settings().tags().contains(Key.of(tagName));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean checkLit(Block block) {
        if (block.getBlockData() instanceof Lightable lightable) return lightable.isLit();
        String customLit = CraftEngineUtil.getCustomBlockProperty(block, "lit");
        if (customLit != null) return Boolean.parseBoolean(customLit);
        return true;
    }
}
