package dev.tako.papersdelight.bridge.api;

import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

public interface Bridge {

    Object getBlockState(Object level, Object pos);
    boolean setBlockState(Object level, Object pos, Object state, int flags);
    Object airStateObj();
    boolean isStateSolid(Object state);
    Object defaultStateFromId(String blockId);
    Object offsetPos(Object pos, int dx, int dy, int dz);
    int blockPosX(Object pos);
    int blockPosY(Object pos);
    int blockPosZ(Object pos);

    boolean fireBlockGrowEventObj(Object level, Object pos, Object state, int flags);
    void levelEventObj(Object accessor, Object pos, int eventId, int data);
    boolean playSoundByKey(Object level, Object pos, String soundKey, float volume, float pitch);

    boolean isServerLevel(Object level);
    boolean isWorldGenRegion(Object level);
    boolean isMobGriefing(Object level);
    boolean isFluidWater(Object level, Object pos);
    boolean isFullWaterSource(Object level, Object pos);
    boolean hasWaterFluidAt(Object level, Object pos);
    boolean isRainingAtPos(Object level, Object pos);
    int getSkyBrightnessAt(Object level, Object pos);
    boolean canSeeSkyAt(Object level, Object pos);

    boolean isLivingEntity(Object entity);
    boolean isPlayerEntity(Object entity);
    boolean isSteppingCarefully(Object entity);
    boolean isShiftKeyDown(Object entity);
    boolean hasFrostWalkerBoots(Object entity);
    void damageBukkitEntity(Object entity, float amount);
    boolean damageEntity(Object level, Object entity, String damageType, float amount);
    float entityNextFloat(Object entity);
    float entityBbWidth(Object entity);
    float entityBbHeight(Object entity);
    void zeroEntityDeltaY(Object entity);

    int randomNextInt(Object random, int bound);
    float randomNextFloat(Object random);
    int tryBonemeal(Object level, Object pos, Object state, Object random, boolean isClientSide);

    World bukkitWorldOf(Object level);
    Object nmsLevelOf(World world);
    Player getBukkitPlayer(Object player);

    org.bukkit.entity.LivingEntity getBukkitLivingEntity(Object entity);
    int getRawBrightness(World world, int x, int y, int z);

    int getVillagerFoodLevel(org.bukkit.entity.Villager villager);

    boolean addVillagerFoodLevel(org.bukkit.entity.Villager villager, int amount);

    boolean villagerPickUpItemEntity(org.bukkit.entity.Villager villager, org.bukkit.entity.Item item);

    boolean installVillagerCropHarvestBehavior(
            org.bukkit.entity.Villager villager,
            VillagerCropRules rules
    );

    boolean installVillagerTradePool(VillagerTradePool pool);

    boolean installVillagerItemSharingFix(org.bukkit.entity.Villager villager);

    boolean villagerNeedsItemSharingFix(org.bukkit.entity.Villager villager);

    Enchantment registerBackstabbingEnchantment(
            String namespace,
            String key,
            int weight,
            int maxLevel,
            int minCostBase,
            int minCostPerLevel,
            int maxCostBase,
            int maxCostPerLevel,
            int anvilCost
    );
}
