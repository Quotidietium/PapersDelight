package dev.tako.papersdelight.bridge;

import dev.tako.papersdelight.bridge.api.Bridge;
import dev.tako.papersdelight.bridge.api.BridgeProvider;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class NMSHelper {

    public NMSHelper() {
    }

    private static Bridge bridge() {
        return BridgeProvider.get();
    }

    public static Object getBlockState(Object level, Object pos) {
        return bridge().getBlockState(level, pos);
    }

    public static boolean setBlockState(Object level, Object pos, Object state, int flags) {
        return bridge().setBlockState(level, pos, state, flags);
    }

    public static Object airStateObj() {
        return bridge().airStateObj();
    }

            public static boolean isStateSolid(Object state) {
        return bridge().isStateSolid(state);
    }

            public static Object defaultStateFromId(String blockId) {
        return bridge().defaultStateFromId(blockId);
    }

                public static Object offsetPos(Object pos, int dx, int dy, int dz) {
        return bridge().offsetPos(pos, dx, dy, dz);
    }

    public static int blockPosX(Object pos) {
        return bridge().blockPosX(pos);
    }

    public static int blockPosY(Object pos) {
        return bridge().blockPosY(pos);
    }

    public static int blockPosZ(Object pos) {
        return bridge().blockPosZ(pos);
    }

    public static boolean fireBlockGrowEventObj(Object level, Object pos, Object state, int flags) {
        return bridge().fireBlockGrowEventObj(level, pos, state, flags);
    }

    public static void levelEventObj(Object accessor, Object pos, int eventId, int data) {
        bridge().levelEventObj(accessor, pos, eventId, data);
    }

    public static boolean playSoundByKey(Object level, Object pos, String soundKey, float volume, float pitch) {
        return bridge().playSoundByKey(level, pos, soundKey, volume, pitch);
    }

    public static boolean isServerLevel(Object level) {
        return bridge().isServerLevel(level);
    }

    public static boolean isWorldGenRegion(Object level) {
        return bridge().isWorldGenRegion(level);
    }

    public static boolean isMobGriefing(Object level) {
        return bridge().isMobGriefing(level);
    }

    public static boolean isFluidWater(Object level, Object pos) {
        return bridge().isFluidWater(level, pos);
    }

    public static boolean isFullWaterSource(Object level, Object pos) {
        return bridge().isFullWaterSource(level, pos);
    }

    public static boolean hasWaterFluidAt(Object level, Object pos) {
        return bridge().hasWaterFluidAt(level, pos);
    }

    public static boolean isRainingAtPos(Object level, Object pos) {
        return bridge().isRainingAtPos(level, pos);
    }

        public static int getSkyBrightnessAt(Object level, Object pos) {
        return bridge().getSkyBrightnessAt(level, pos);
    }

    public static boolean canSeeSkyAt(Object level, Object pos) {
        return bridge().canSeeSkyAt(level, pos);
    }

    public static boolean isLivingEntity(Object entity) {
        return bridge().isLivingEntity(entity);
    }

    public static boolean isPlayerEntity(Object entity) {
        return bridge().isPlayerEntity(entity);
    }

    public static boolean isSteppingCarefully(Object entity) {
        return bridge().isSteppingCarefully(entity);
    }

    public static boolean isShiftKeyDown(Object entity) {
        return bridge().isShiftKeyDown(entity);
    }

    public static boolean hasFrostWalkerBoots(Object entity) {
        return bridge().hasFrostWalkerBoots(entity);
    }

    public static void damageBukkitEntity(Object entity, float amount) {
        bridge().damageBukkitEntity(entity, amount);
    }

    public static boolean damageEntity(Object level, Object entity, String damageType, float amount) {
        return bridge().damageEntity(level, entity, damageType, amount);
    }

    public static float entityNextFloat(Object entity) {
        return bridge().entityNextFloat(entity);
    }

    public static float entityBbWidth(Object entity) {
        return bridge().entityBbWidth(entity);
    }

    public static float entityBbHeight(Object entity) {
        return bridge().entityBbHeight(entity);
    }

    public static void zeroEntityDeltaY(Object entity) {
        bridge().zeroEntityDeltaY(entity);
    }

        public static int randomNextInt(Object random, int bound) {
        return bridge().randomNextInt(random, bound);
    }

    public static float randomNextFloat(Object random) {
        return bridge().randomNextFloat(random);
    }

    public static int tryBonemeal(Object level, Object pos, Object state, Object random, boolean isClientSide) {
        return bridge().tryBonemeal(level, pos, state, random, isClientSide);
    }

    public static World bukkitWorldOf(Object level) {
        return bridge().bukkitWorldOf(level);
    }

    public static Object nmsLevelOf(World world) {
        return bridge().nmsLevelOf(world);
    }

    public static Player getBukkitPlayer(Object player) {
        return bridge().getBukkitPlayer(player);
    }

    public static org.bukkit.entity.LivingEntity getBukkitLivingEntity(Object entity) {
        return bridge().getBukkitLivingEntity(entity);
    }

                        public static int getRawBrightness(World world, int x, int y, int z) {
        return bridge().getRawBrightness(world, x, y, z);
    }

        public static int getItemEnchantability(ItemStack item, int fallback) {
        if (item == null) return fallback;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return fallback;
        try {
            Object value = meta.getClass().getMethod("getEnchantable").invoke(meta);
            if (value instanceof Number) {
                int v = ((Number) value).intValue();
                if (v > 0) return v;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return fallback;
    }

    public static int getVillagerFoodLevel(org.bukkit.entity.Villager villager) {
        return bridge().getVillagerFoodLevel(villager);
    }

    public static boolean addVillagerFoodLevel(org.bukkit.entity.Villager villager, int amount) {
        return bridge().addVillagerFoodLevel(villager, amount);
    }

    public static boolean villagerPickUpItemEntity(org.bukkit.entity.Villager villager, org.bukkit.entity.Item item) {
        return bridge().villagerPickUpItemEntity(villager, item);
    }

    public static boolean installVillagerCropHarvestBehavior(
            org.bukkit.entity.Villager villager,
            dev.tako.papersdelight.bridge.api.VillagerCropRules rules
    ) {
        return bridge().installVillagerCropHarvestBehavior(villager, rules);
    }

    public static boolean installVillagerTradePool(
            dev.tako.papersdelight.bridge.api.VillagerTradePool pool
    ) {
        return bridge().installVillagerTradePool(pool);
    }

    public static boolean installVillagerItemSharingFix(org.bukkit.entity.Villager villager) {
        return bridge().installVillagerItemSharingFix(villager);
    }

    public static boolean villagerNeedsItemSharingFix(org.bukkit.entity.Villager villager) {
        return bridge().villagerNeedsItemSharingFix(villager);
    }

    public static Enchantment registerBackstabbingEnchantment(
            String namespace, String key, int weight, int maxLevel,
            int minCostBase, int minCostPerLevel, int maxCostBase, int maxCostPerLevel, int anvilCost) {
        return bridge().registerBackstabbingEnchantment(
                namespace, key, weight, maxLevel,
                minCostBase, minCostPerLevel, maxCostBase, maxCostPerLevel, anvilCost);
    }
}
