package dev.tako.papersdelight.bridge.v1_21_10;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

final class CeTradeWithVillager extends Behavior<Villager> {
    private static final int MAX_KEEP = 24;

    private Set<Item> trades = Set.of();

    CeTradeWithVillager() {
        super(ImmutableMap.of(
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
        ));
    }

    private static Villager partner(Villager owner) {
        Brain<Villager> brain = owner.getBrain();
        return brain.getMemory(MemoryModuleType.INTERACTION_TARGET)
                .filter(Villager.class::isInstance)
                .map(Villager.class::cast)
                .orElse(null);
    }

    private static Set<Item> figureOutWhatIAmWillingToTrade(Villager owner, Villager partner) {
        var wanted = partner.getVillagerData().profession().value().requestedItems();
        var mine = owner.getVillagerData().profession().value().requestedItems();
        return wanted.stream().filter(item -> !mine.contains(item))
                .collect(java.util.stream.Collectors.toSet());
    }

    private static void throwHalfStack(Villager owner, Set<Item> wanted, LivingEntity target) {
        SimpleContainer inventory = owner.getInventory();
        ItemStack toThrow = ItemStack.EMPTY;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!wanted.contains(stack.getItem())) continue;

            int count;
            if (stack.getCount() > stack.getMaxStackSize() / 2) {
                count = stack.getCount() / 2;
            } else if (stack.getCount() > MAX_KEEP) {
                count = stack.getCount() - MAX_KEEP;
            } else {
                continue;
            }

            toThrow = stack.copyWithCount(count);
            stack.shrink(count);
            break;
        }

        if (!toThrow.isEmpty()) {
            BehaviorUtils.throwItem(owner, toThrow, target.position());
        }
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        return BehaviorUtils.targetIsValid(
                owner.getBrain(), MemoryModuleType.INTERACTION_TARGET, EntityType.VILLAGER);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager owner, long gameTime) {
        return checkExtraStartConditions(level, owner);
    }

    @Override
    protected void start(ServerLevel level, Villager owner, long gameTime) {
        Villager partner = partner(owner);
        if (partner == null) return;
        BehaviorUtils.lockGazeAndWalkToEachOther(owner, partner, 0.5F, 2);
        trades = figureOutWhatIAmWillingToTrade(owner, partner);
    }

    @Override
    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        Villager partner = partner(owner);
        if (partner == null || owner.distanceToSqr(partner) > 5.0) return;
        BehaviorUtils.lockGazeAndWalkToEachOther(owner, partner, 0.5F, 2);
        owner.gossip(level, partner, gameTime);

        boolean isFarmer = owner.getVillagerData().profession().is(VillagerProfession.FARMER);

        if (owner.hasExcessFood() && (isFarmer || partner.wantsMoreFood())) {
            throwHalfStack(owner, Villager.FOOD_POINTS.keySet(), partner);
        }
        if (isFarmer && owner.getInventory().countItem(Items.WHEAT)
                > Items.WHEAT.getDefaultMaxStackSize() / 2) {
            throwHalfStack(owner, com.google.common.collect.ImmutableSet.of(Items.WHEAT), partner);
        }
        if (!trades.isEmpty() && owner.getInventory().hasAnyOf(trades)) {
            throwHalfStack(owner, trades, partner);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager owner, long gameTime) {
        owner.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }
}
