package dev.tako.papersdelight.mechanic.stove;

import dev.tako.papersdelight.damage.DamageTypes;
import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import java.util.Optional;

public final class HighTemperatureBlockBehavior extends BukkitBlockBehavior {

    public static final BlockBehaviorFactory<HighTemperatureBlockBehavior> FACTORY = new Factory();

    private final float damageAmount;
    private final String damageType;

    private final net.kyori.adventure.key.Key fallbackKey;

    private final Property<Boolean> stateProperty;

    private final StoveBurnArea burnArea;

    private HighTemperatureBlockBehavior(BlockDefinition blockDefinition,
                                         float damageAmount,
                                         String damageType,
                                         net.kyori.adventure.key.Key fallbackKey,
                                         Property<Boolean> stateProperty,
                                         StoveBurnArea burnArea) {
        super(blockDefinition);
        this.damageAmount = damageAmount;
        this.damageType = damageType;
        this.fallbackKey = fallbackKey;
        this.stateProperty = stateProperty;
        this.burnArea = burnArea;
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:high_temperature"), FACTORY);
    }

    @Override
    public void stepOn(Object thisBlock, Object[] args) {

        if (stateProperty != null) {
            Object nmsState = args[2];
            Optional<ImmutableBlockState> optState = BlockStateUtils.getOptionalCustomBlockState(nmsState);
            if (optState.isEmpty()) return;
            Boolean value = optState.get().get(stateProperty);
            if (value == null || !value) return;
        }

        Object level = args[0];
        Object entityNMS = args[3];

        if (!NMSHelper.isLivingEntity(entityNMS)) return;
        if (isSteppingCarefully(entityNMS)) return;
        if (hasFrostWalker(entityNMS)) return;

        if (burnArea != null && !isWithinBurnArea(args[1], entityNMS)) return;

        applyDamage(level, entityNMS);
    }

    private boolean isWithinBurnArea(Object nmsPos, Object nmsEntity) {
        org.bukkit.entity.LivingEntity bukkit = NMSHelper.getBukkitLivingEntity(nmsEntity);
        if (bukkit == null) return true;
        org.bukkit.Location loc = bukkit.getLocation();
        int blockX = NMSHelper.blockPosX(nmsPos);
        int blockY = NMSHelper.blockPosY(nmsPos);
        int blockZ = NMSHelper.blockPosZ(nmsPos);
        return burnArea.intersects(
                loc.getX(), loc.getY(), loc.getZ(),
                bukkit.getWidth(), bukkit.getHeight(),
                blockX, blockY, blockZ);
    }

    private static boolean isSteppingCarefully(Object nmsEntity) {
        return NMSHelper.isSteppingCarefully(nmsEntity);
    }

    private static boolean hasFrostWalker(Object nmsEntity) {
        return NMSHelper.hasFrostWalkerBoots(nmsEntity);
    }

    private void applyDamage(Object level, Object nmsEntity) {
        net.kyori.adventure.key.Key customKey = HighTemperatureDamageKeys.customDamageKey(this.damageType);
        if (customKey != null) {
            org.bukkit.entity.LivingEntity bukkit = NMSHelper.getBukkitLivingEntity(nmsEntity);
            if (bukkit != null) {
                DamageTypes.damage(bukkit, this.damageAmount, customKey, this.fallbackKey);
                return;
            }
        }
        if (!NMSHelper.damageEntity(level, nmsEntity, this.damageType, this.damageAmount)) {
            NMSHelper.damageBukkitEntity(nmsEntity, this.damageAmount);
        }
    }

    private static final String[] DAMAGE = {"damage", "damage_amount", "damageAmount"};
    private static final String[] DAMAGE_TYPE = {"damage_type", "damage-type", "damageType"};
    private static final String[] FALLBACK_TYPE = {"fallback_type", "fallback-type", "fallbackType", "damage_fallback_type"};
    private static final String[] PROPERTY = {"property"};
    private static final String[] BURN_AREA = {"burn_area", "burn-area", "burnArea"};

    private static class Factory implements BlockBehaviorFactory<HighTemperatureBlockBehavior> {

        @Override
        public HighTemperatureBlockBehavior create(BlockDefinition block, ConfigSection section) {
            float damage = section.getFloat(DAMAGE, 1.0f);
            String type = section.getString(DAMAGE_TYPE, "minecraft:on_fire");
            net.kyori.adventure.key.Key fallbackKey =
                    HighTemperatureDamageKeys.parseFallbackKey(section.getString(FALLBACK_TYPE));
            Property<Boolean> property = null;
            String propName = section.getString(PROPERTY);
            if (propName != null && !propName.isEmpty()) {
                property = BlockBehaviorFactory.getProperty(section.path(), block, propName, Boolean.class);
            }
            StoveBurnArea burnArea = null;
            java.util.List<Object> burnAreaRaw = section.getList(BURN_AREA);
            if (burnAreaRaw != null && !burnAreaRaw.isEmpty()) {
                try {
                    burnArea = StoveBurnArea.fromPixels(burnAreaRaw);
                } catch (IllegalArgumentException invalid) {
                    net.momirealms.craftengine.core.plugin.CraftEngine.instance().logger().warn(
                            dev.tako.papersdelight.config.ConfigManager.getOr(
                                    "high_temperature_burn_area_invalid", "high_temperature 方块的 burn_area 配置非法，已降级为整个上表面：%error%")
                                    .replace("%error%", dev.tako.papersdelight.config.ConfigManager.describeError(invalid)));
                }
            }
            return new HighTemperatureBlockBehavior(block, damage, type, fallbackKey, property, burnArea);
        }
    }
}
