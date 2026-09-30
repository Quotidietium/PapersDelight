package dev.tako.papersdelight.bridge.v1_21_10;

import dev.tako.papersdelight.bridge.BackstabbingEnchantmentRegistrar;
import dev.tako.papersdelight.bridge.api.Bridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BridgeV1_21_10 implements Bridge {
    public BridgeV1_21_10() {
    }

    private static net.minecraft.world.entity.npc.Villager villagerHandle(org.bukkit.entity.Villager villager) {
        return villager instanceof org.bukkit.craftbukkit.entity.CraftVillager craft ? craft.getHandle() : null;
    }

    private Object toNms(World world) {
        return ((CraftWorld) world).getHandle();
    }

    @Override
    public Object getBlockState(Object level, Object pos) {
        return ((Level) level).getBlockState((BlockPos) pos);
    }

    @Override
    public boolean setBlockState(Object level, Object pos, Object state, int flags) {
        return ((Level) level).setBlock((BlockPos) pos, (BlockState) state, flags);
    }

    @Override
    public Object airStateObj() {
        return Blocks.AIR.defaultBlockState();
    }

            @SuppressWarnings("deprecation")
    @Override
    public boolean isStateSolid(Object state) {
        return ((BlockState) state).isSolid();
    }

    @SuppressWarnings("unchecked")
            @Override
    public Object defaultStateFromId(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) return Blocks.AIR.defaultBlockState();
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR).defaultBlockState();
    }

                @Override
    public Object offsetPos(Object pos, int dx, int dy, int dz) {
        return ((BlockPos) pos).offset(dx, dy, dz);
    }

    @Override
    public int blockPosX(Object pos) {
        return ((BlockPos) pos).getX();
    }

    @Override
    public int blockPosY(Object pos) {
        return ((BlockPos) pos).getY();
    }

    @Override
    public int blockPosZ(Object pos) {
        return ((BlockPos) pos).getZ();
    }

    @Override
    public boolean fireBlockGrowEventObj(Object level, Object pos, Object state, int flags) {
        return CraftEventFactory.handleBlockGrowEvent((Level) level, (BlockPos) pos, (BlockState) state, flags);
    }

    @Override
    public void levelEventObj(Object accessor, Object pos, int eventId, int data) {
        ((LevelAccessor) accessor).levelEvent(null, eventId, (BlockPos) pos, data);
    }

    @Override
    public boolean playSoundByKey(Object level, Object pos, String soundKey, float volume, float pitch) {
        ResourceLocation id = ResourceLocation.tryParse(soundKey);
        if (id == null) return false;
        net.minecraft.sounds.SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) return false;
        ((Level) level).playSound(null, (BlockPos) pos, sound, SoundSource.BLOCKS, volume, pitch);
        return true;
    }

    @Override
    public boolean isServerLevel(Object level) {
        return level instanceof ServerLevel;
    }

    @Override
    public boolean isWorldGenRegion(Object level) {
        return level != null && level.getClass().getSimpleName().equals("WorldGenRegion");
    }

    @Override
    public boolean isMobGriefing(Object level) {
        return level instanceof ServerLevel && ((ServerLevel) level).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    @Override
    public boolean isFluidWater(Object level, Object pos) {
        return ((Level) level).getFluidState((BlockPos) pos).is(FluidTags.WATER);
    }

    @Override
    public boolean isFullWaterSource(Object level, Object pos) {
        FluidState f = ((Level) level).getFluidState((BlockPos) pos);
        return f.is(FluidTags.WATER) && f.getAmount() == 8;
    }

    @Override
    public boolean hasWaterFluidAt(Object level, Object pos) {
        FluidState f = ((Level) level).getFluidState((BlockPos) pos);
        return !f.isEmpty() && (f.getType() == Fluids.WATER || f.getType() == Fluids.FLOWING_WATER);
    }

    @Override
    public boolean isRainingAtPos(Object level, Object pos) {
        return level instanceof ServerLevel ? ((ServerLevel) level).isRainingAt((BlockPos) pos) : ((Level) level).isRaining();
    }

        @Override
    public int getSkyBrightnessAt(Object level, Object pos) {
        return ((LevelReader) level).getBrightness(LightLayer.SKY, (BlockPos) pos);
    }

    @Override
    public boolean canSeeSkyAt(Object level, Object pos) {
        return ((LevelReader) level).canSeeSky((BlockPos) pos);
    }

    @Override
    public boolean isLivingEntity(Object entity) {
        return entity instanceof LivingEntity;
    }

    @Override
    public boolean isPlayerEntity(Object entity) {
        return entity instanceof net.minecraft.world.entity.player.Player;
    }

    @Override
    public boolean isSteppingCarefully(Object entity) {
        return ((Entity) entity).isSteppingCarefully();
    }

    @Override
    public boolean isShiftKeyDown(Object entity) {
        return entity instanceof Entity && ((Entity) entity).isShiftKeyDown();
    }

    @Override
    public boolean hasFrostWalkerBoots(Object entity) {
        if (!(entity instanceof LivingEntity)) return false;
        org.bukkit.entity.Entity bk = ((LivingEntity) entity).getBukkitEntity();
        if (!(bk instanceof org.bukkit.entity.LivingEntity)) return false;
        org.bukkit.inventory.EntityEquipment eq = ((org.bukkit.entity.LivingEntity) bk).getEquipment();
        if (eq == null) return false;
        ItemStack boots = eq.getBoots();
        return boots != null && boots.containsEnchantment(Enchantment.FROST_WALKER);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void damageBukkitEntity(Object entity, float amount) {
        if (entity instanceof LivingEntity le) {
            le.hurt(le.damageSources().generic(), amount);
        }
    }

    @Override
    public boolean damageEntity(Object level, Object entity, String damageType, float amount) {
        if (!(level instanceof Level world) || !(entity instanceof Entity target) || damageType == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(damageType.indexOf(':') >= 0 ? damageType : "minecraft:" + damageType);
        if (id == null) return false;
        var holder = world.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .get(ResourceKey.create(Registries.DAMAGE_TYPE, id));
        if (holder.isEmpty()) return false;
        target.hurt(new DamageSource(holder.get()), amount);
        return true;
    }

    @Override
    public float entityNextFloat(Object entity) {
        return ((Entity) entity).getRandom().nextFloat();
    }

    @Override
    public float entityBbWidth(Object entity) {
        return ((Entity) entity).getBbWidth();
    }

    @Override
    public float entityBbHeight(Object entity) {
        return ((Entity) entity).getBbHeight();
    }

    @Override
    public void zeroEntityDeltaY(Object entity) {
        Entity e = (Entity) entity;
        net.minecraft.world.phys.Vec3 m = e.getDeltaMovement();
        e.setDeltaMovement(m.x, 0.0, m.z);
    }

        @Override
    public int randomNextInt(Object random, int bound) {
        return ((RandomSource) random).nextInt(bound);
    }

    @Override
    public float randomNextFloat(Object random) {
        return ((RandomSource) random).nextFloat();
    }

    @Override
    public int tryBonemeal(Object level, Object pos, Object state, Object random, boolean isClientSide) {
        if (!(state instanceof BlockState bs)) return -1;
        if (!(bs.getBlock() instanceof BonemealableBlock bmb)) return -1;
        if (!(level instanceof LevelReader)) return -1;
        BlockPos bp = (BlockPos) pos;
        if (!bmb.isValidBonemealTarget((LevelReader) level, bp, bs)) return 0;
        if (!(level instanceof ServerLevel)) return 0;
        RandomSource rs = (RandomSource) random;
        if (!bmb.isBonemealSuccess((ServerLevel) level, rs, bp, bs)) return 0;
        bmb.performBonemeal((ServerLevel) level, rs, bp, bs);
        return 1;
    }

    @Override
    public World bukkitWorldOf(Object level) {
        return level instanceof ServerLevel ? ((ServerLevel) level).getWorld() : null;
    }

    @Override
    public Object nmsLevelOf(World world) {
        return toNms(world);
    }

    @Override
    public Player getBukkitPlayer(Object player) {
        return player instanceof ServerPlayer ? ((ServerPlayer) player).getBukkitEntity() : null;
    }

    @Override
    public org.bukkit.entity.LivingEntity getBukkitLivingEntity(Object entity) {
        return entity instanceof LivingEntity ? (org.bukkit.entity.LivingEntity) ((LivingEntity) entity).getBukkitEntity() : null;
    }

                        @Override
    public int getRawBrightness(World world, int x, int y, int z) {
        return world == null ? 0 : ((Level) toNms(world)).getRawBrightness(new BlockPos(x, y, z), 0);
    }

        @Override
    public int getVillagerFoodLevel(org.bukkit.entity.Villager villager) {
        return dev.tako.papersdelight.bridge.VillagerFoodBridge.get(villagerHandle(villager));
    }

    @Override
    public boolean addVillagerFoodLevel(org.bukkit.entity.Villager villager, int amount) {
        return dev.tako.papersdelight.bridge.VillagerFoodBridge.add(villagerHandle(villager), amount);
    }

    @Override
    public boolean villagerPickUpItemEntity(org.bukkit.entity.Villager villager, org.bukkit.entity.Item item) {
        if (!(villager instanceof org.bukkit.craftbukkit.entity.CraftVillager craftVillager)
                || !(item instanceof org.bukkit.craftbukkit.entity.CraftItem craftItem)
                || !item.isValid()) return false;
        var handle = craftVillager.getHandle();
        var itemEntity = craftItem.getHandle();
        var stack = itemEntity.getItem();
        var inventory = handle.getInventory();
        if (stack.isEmpty() || !inventory.canAddItem(stack)) return false;
        var remaining = new net.minecraft.world.SimpleContainer(inventory).addItem(stack);
        if (org.bukkit.craftbukkit.event.CraftEventFactory
                .callEntityPickupItemEvent(handle, itemEntity, remaining.getCount(), false).isCancelled()) return false;
        handle.onItemPickup(itemEntity);
        int originalCount = stack.getCount();
        var leftover = inventory.addItem(stack);
        int pickedUp = originalCount - leftover.getCount();
        if (pickedUp <= 0) return false;
        handle.take(itemEntity, pickedUp);
        if (leftover.isEmpty()) itemEntity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP);
        else stack.setCount(leftover.getCount());
        return true;
    }

    @Override
    public boolean installVillagerCropHarvestBehavior(org.bukkit.entity.Villager villager, dev.tako.papersdelight.bridge.api.VillagerCropRules rules) {
        var handle = villagerHandle(villager);
        if (handle == null || handle.isBaby() || !(handle.level() instanceof ServerLevel level)) return false;

        handle.refreshBrain(level);
        if (rules != null) {
            handle.getBrain().addActivityWithConditions(
                    net.minecraft.world.entity.schedule.Activity.WORK,
                    com.google.common.collect.ImmutableList.of(com.mojang.datafixers.util.Pair.of(5, new CeHarvestFarmland(rules))),
                    com.google.common.collect.ImmutableSet.of(com.mojang.datafixers.util.Pair.of(
                            net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE,
                            net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT
                    ))
            );
        }
        return installVillagerItemSharingFix(villager);
    }

    @Override
    public boolean installVillagerTradePool(dev.tako.papersdelight.bridge.api.VillagerTradePool pool) {
        return VillagerTradePoolInjector.install(pool);
    }

    @Override
    public boolean installVillagerItemSharingFix(org.bukkit.entity.Villager villager) {
        var handle = villagerHandle(villager);
        if (handle == null || handle.isBaby()) return false;

        int removed = dev.tako.papersdelight.bridge.VillagerBehaviorSurgery.removeBehaviors(
                handle.getBrain(), net.minecraft.world.entity.ai.behavior.TradeWithVillager.class);
        if (removed < 0) return false;
        if (dev.tako.papersdelight.bridge.VillagerBehaviorSurgery.removeBehaviors(
                handle.getBrain(), CeTradeWithVillager.class) < 0) return false;

        dev.tako.papersdelight.bridge.VillagerBehaviorSurgery.removeEmptyContainers(handle.getBrain());

        for (var activity : java.util.List.of(
                net.minecraft.world.entity.schedule.Activity.MEET,
                net.minecraft.world.entity.schedule.Activity.IDLE)) {
            handle.getBrain().addActivityWithConditions(
                    activity,
                    com.google.common.collect.ImmutableList.of(com.mojang.datafixers.util.Pair.of(3,
                            new net.minecraft.world.entity.ai.behavior.GateBehavior<net.minecraft.world.entity.npc.Villager>(
                                    com.google.common.collect.ImmutableMap.of(),
                                    com.google.common.collect.ImmutableSet.of(
                                            net.minecraft.world.entity.ai.memory.MemoryModuleType.INTERACTION_TARGET),
                                    net.minecraft.world.entity.ai.behavior.GateBehavior.OrderPolicy.ORDERED,
                                    net.minecraft.world.entity.ai.behavior.GateBehavior.RunningPolicy.RUN_ONE,
                                    com.google.common.collect.ImmutableList.of(
                                            com.mojang.datafixers.util.Pair.of(new CeTradeWithVillager(), 1))
                            ))),
                    com.google.common.collect.ImmutableSet.of(com.mojang.datafixers.util.Pair.of(
                            net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                            net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT
                    ))
            );
        }
        return true;
    }

    @Override
    public boolean villagerNeedsItemSharingFix(org.bukkit.entity.Villager villager) {
        var handle = villagerHandle(villager);
        if (handle == null || handle.isBaby()) return false;
        return dev.tako.papersdelight.bridge.VillagerBehaviorSurgery.hasBehavior(
                handle.getBrain(), net.minecraft.world.entity.ai.behavior.TradeWithVillager.class);
    }

    @Override
    public Enchantment registerBackstabbingEnchantment(String namespace, String key, int weight, int maxLevel, int minCostBase, int minCostPerLevel, int maxCostBase, int maxCostPerLevel, int anvilCost) {
        return BackstabbingEnchantmentRegistrar.register(namespace, key, weight, maxLevel, minCostBase, minCostPerLevel, maxCostBase, maxCostPerLevel, anvilCost);
    }
}
