package dev.tako.papersdelight.bridge.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Villager;

public interface VillagerCropRules {
    boolean isHarvestable(Block block);

    boolean tryHarvest(Block block, Villager villager);

    boolean canPlant(Block block, Villager villager);

    boolean tryPlant(Block block, Villager villager);
}
