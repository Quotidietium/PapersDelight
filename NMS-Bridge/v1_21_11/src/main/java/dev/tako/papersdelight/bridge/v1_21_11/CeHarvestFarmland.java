package dev.tako.papersdelight.bridge.v1_21_11;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import dev.tako.papersdelight.bridge.api.VillagerCropRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

import java.util.List;

final class CeHarvestFarmland extends Behavior<Villager> {
    private static final int HARVEST_DURATION = 200;
    private static final float SPEED_MODIFIER = 0.5F;

    private final VillagerCropRules rules;
    private final List<BlockPos> validPositions = Lists.newArrayList();
    private @Nullable BlockPos target;
    private long nextOkStartTime;
    private int timeWorked;

    CeHarvestFarmland(VillagerCropRules rules) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.SECONDARY_JOB_SITE, MemoryStatus.VALUE_PRESENT
        ));
        this.rules = rules;
    }

    private static org.bukkit.block.Block bukkitBlock(ServerLevel level, BlockPos pos) {
        return level.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)
                || !owner.getVillagerData().profession().is(VillagerProfession.FARMER)) return false;

        BlockPos.MutableBlockPos pos = owner.blockPosition().mutable();
        validPositions.clear();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    pos.set(owner.getX() + x, owner.getY() + y, owner.getZ() + z);
                    if (isValidPosition(level, owner, pos)) validPositions.add(new BlockPos(pos));
                }
            }
        }
        target = randomTarget(level);
        return target != null;
    }

    private boolean isValidPosition(ServerLevel level, Villager owner, BlockPos pos) {
        org.bukkit.block.Block block = bukkitBlock(level, pos);
        org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) owner.getBukkitEntity();
        return rules.isHarvestable(block)
                || level.getBlockState(pos).isAir() && rules.canPlant(block, villager);
    }

    private @Nullable BlockPos randomTarget(ServerLevel level) {
        return validPositions.isEmpty() ? null : validPositions.get(level.getRandom().nextInt(validPositions.size()));
    }

    @Override
    protected void start(ServerLevel level, Villager owner, long gameTime) {
        if (gameTime > nextOkStartTime && target != null) {
            owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
            owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(target), SPEED_MODIFIER, 1));
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager owner, long gameTime) {
        owner.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        timeWorked = 0;
        nextOkStartTime = gameTime + 40L;
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        if (target != null && !target.closerToCenterThan(owner.position(), 1.0)) return;
        if (target != null && gameTime > nextOkStartTime) {
            BlockState state = level.getBlockState(target);
            org.bukkit.block.Block block = bukkitBlock(level, target);
            org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) owner.getBukkitEntity();
            boolean harvestable = rules.isHarvestable(block);
            boolean plantable = state.isAir() && rules.canPlant(block, villager);

            if (harvestable && org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(
                    owner, target, state.getFluidState().createLegacyBlock())) {
                rules.tryHarvest(block, villager);
            } else if (plantable && rules.tryPlant(block, villager)) {
                BlockState planted = level.getBlockState(target);
                level.gameEvent(GameEvent.BLOCK_PLACE, target, GameEvent.Context.of(owner, planted));
            }

            if (!harvestable && !plantable) selectNextTarget(level, owner, gameTime);
        }
        timeWorked++;
    }

    private void selectNextTarget(ServerLevel level, Villager owner, long gameTime) {
        validPositions.remove(target);
        target = randomTarget(level);
        if (target == null) return;
        nextOkStartTime = gameTime + 20L;
        owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), SPEED_MODIFIER, 1));
        owner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager owner, long gameTime) {
        return timeWorked < HARVEST_DURATION;
    }
}
