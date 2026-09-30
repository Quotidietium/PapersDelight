package dev.tako.papersdelight.mechanic.farm;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.block.behavior.CropBlockBehavior;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.*;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.BonemealableBlock;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.parser.BlockStateParser;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemKeys;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.SimpleContext;
import net.momirealms.craftengine.core.plugin.context.number.NumberProvider;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.Vec3i;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;

@SuppressWarnings("DuplicatedCode")
public final class AdvancedCropBlockBehavior extends BukkitBlockBehavior
        implements BonemealableBlock, RandomTickBlock {

    public static final BlockBehaviorFactory<AdvancedCropBlockBehavior> FACTORY = new Factory();

    public final IntegerProperty ageProperty;
    public final float growSpeed;
    public final int minGrowLight;
    public final int minSpawnLight;
    public final boolean isBoneMealTarget;
    public final NumberProvider boneMealBonus;

    public final boolean canHarvestByVillagers;

    public final boolean canReplantByVillagers;

    private final List<SoilEntry> soils;

    record SoilEntry(Set<Object> vanillaStates, Set<Key> customBlockIds, Set<Key> blockTags,
                     float growthModifier, float bonemealChance) {
    }

    private AdvancedCropBlockBehavior(
            BlockDefinition block,
            Property<Integer> ageProperty,
            float growSpeed,
            int minGrowLight,
            int minSpawnLight,
            boolean isBoneMealTarget,
            NumberProvider boneMealBonus,
            boolean canHarvestByVillagers,
            boolean canReplantByVillagers,
            List<SoilEntry> soils
    ) {
        super(block);
        this.ageProperty = (IntegerProperty) ageProperty;
        this.growSpeed = growSpeed;
        this.minGrowLight = minGrowLight;
        this.minSpawnLight = minSpawnLight;
        this.isBoneMealTarget = isBoneMealTarget;
        this.boneMealBonus = boneMealBonus;
        this.canHarvestByVillagers = canHarvestByVillagers;
        this.canReplantByVillagers = canReplantByVillagers;
        this.soils = List.copyOf(soils);
    }

    public int getAge(ImmutableBlockState state) {
        return state.get(ageProperty);
    }

    public boolean isMaxAge(ImmutableBlockState state) {
        return state.get(ageProperty) == ageProperty.max;
    }

    private static int getRawBrightness(Object level, Object pos) {
        return CropBlockBehavior.getRawBrightness(level, pos);
    }

    private boolean hasSufficientLightForGrow(Object level, Object pos) {
        return getRawBrightness(level, pos) >= this.minGrowLight;
    }

    private boolean hasSufficientLightForSpawn(Object level, Object pos) {
        return getRawBrightness(level, pos) >= this.minSpawnLight;
    }

    private static boolean isWorldGenRegion(Object level) {
        return NMSHelper.isWorldGenRegion(level);
    }

    private static Object getBlockStateNMS(Object level, Object pos) {
        return NMSHelper.getBlockState(level, pos);
    }

    private static World getBukkitWorld(Object level) {
        World world = NMSHelper.bukkitWorldOf(level);
        if (world == null) throw new IllegalStateException("NMS level is not a ServerLevel");
        return world;
    }

    private static boolean fireBlockGrowEvent(Object level, Object pos, Object newState) {
        return NMSHelper.fireBlockGrowEventObj(level, pos, newState, UpdateFlags.UPDATE_CLIENTS);
    }

    private Object getBelowStateNMS(Object level, Object pos) {
        Object belowPos = LocationUtils.below(pos);
        return getBlockStateNMS(level, belowPos);
    }

    private float findGrowthModifier(Object level, Object pos) {
        SoilEntry entry = findSoilEntryFor(level, pos);
        return entry != null ? entry.growthModifier : -1f;
    }

    private SoilEntry findSoilEntryFor(Object level, Object pos) {
        Object belowState = getBelowStateNMS(level, pos);
        return findSoilEntryForState(belowState);
    }

    public boolean isSupportedSoil(org.bukkit.block.Block soil) {
        return soil != null && isSupportedSoilState(BlockStateUtils.getBlockState(soil));
    }

    public boolean isSupportedSoilState(Object nmsState) {
        return findSoilEntryForState(nmsState) != null;
    }

    private SoilEntry findSoilEntryForState(Object nmsState) {

        for (SoilEntry soil : this.soils) {
            for (Key tag : soil.blockTags) {
                if (isInTag(nmsState, tag)) {
                    return soil;
                }
            }
        }

        for (SoilEntry soil : this.soils) {
            if (soil.vanillaStates.contains(nmsState)) {
                return soil;
            }
        }

        Optional<ImmutableBlockState> customState = BlockStateUtils.getOptionalCustomBlockState(nmsState);
        if (customState.isPresent()) {
            Key ceOwnerKey = customState.get().owner().keyOptional()
                    .map(h -> h.location())
                    .orElse(null);
            if (ceOwnerKey != null) {
                for (SoilEntry soil : this.soils) {
                    if (soil.customBlockIds.contains(ceOwnerKey)) {
                        return soil;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isInTag(Object nmsState, Key tag) {
        return nmsState != null && BlockStateUtils.isTag(nmsState, tag);
    }

    private float getGrowthSpeed(Object thisBlock, Object level, BlockPos cePos) {
        int cx = cePos.x;
        int cz = cePos.z;
        int soilY = cePos.y - 1;

        Object centerNMS = LocationUtils.toBlockPos(cx, soilY, cz);
        SoilEntry centerSoil = findSoilEntryForState(getBlockStateNMS(level, centerNMS));
        if (centerSoil == null) return -1f;

        float modifier = centerSoil.growthModifier;
        if (modifier < 0f) return -1f;
        float speed = 1.0F + modifier;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Object neighborNMS = LocationUtils.toBlockPos(cx + dx, soilY, cz + dz);
                Object neighborState = getBlockStateNMS(level, neighborNMS);
                SoilEntry neighborSoil = findSoilEntryForState(neighborState);
                if (neighborSoil != null) {
                    speed += neighborSoil.growthModifier / 4.0F;
                }
            }
        }

        int y = cePos.y;
        Object northState  = getBlockStateNMS(level, LocationUtils.toBlockPos(cx,     y, cz - 1));
        Object southState  = getBlockStateNMS(level, LocationUtils.toBlockPos(cx,     y, cz + 1));
        Object westState   = getBlockStateNMS(level, LocationUtils.toBlockPos(cx - 1, y, cz));
        Object eastState   = getBlockStateNMS(level, LocationUtils.toBlockPos(cx + 1, y, cz));

        boolean westEast   = isSameCropType(westState, thisBlock) || isSameCropType(eastState, thisBlock);
        boolean northSouth = isSameCropType(northState, thisBlock) || isSameCropType(southState, thisBlock);

        if (westEast && northSouth) {
            speed /= 2.0F;
        } else {

            Object nwState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx - 1, y, cz - 1));
            Object neState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx + 1, y, cz - 1));
            Object seState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx + 1, y, cz + 1));
            Object swState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx - 1, y, cz + 1));

            if (isSameCropType(nwState, thisBlock) || isSameCropType(neState, thisBlock)
             || isSameCropType(seState, thisBlock) || isSameCropType(swState, thisBlock)) {
                speed /= 2.0F;
            }
        }

        return speed;
    }

    private static boolean isSameCropType(Object nmsState, Object thisBlock) {
        return nmsState != null && BlockStateUtils.getBlockOwner(nmsState) == thisBlock;
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {

        Object level = args[1];
        Object pos = args[2];

        if (isWorldGenRegion(level)) {
            return hasSufficientLightForSpawn(level, pos);
        }

        if (!hasSufficientLightForGrow(level, pos)) {
            return false;
        }
        return findSoilEntryFor(level, pos) != null;
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {

        Direction direction = DirectionUtils.fromNMSDirection(args[updateShape$direction]);
        if (direction != Direction.DOWN) {
            return args[0];
        }

        Object[] survivalArgs = new Object[3];
        survivalArgs[0] = args[0];
        survivalArgs[1] = args[updateShape$level];
        survivalArgs[2] = args[updateShape$blockPos];

        if (!canSurvive(thisBlock, survivalArgs)) {
            return getAirNMSState();
        }

        return args[0];
    }

    private static Object getAirNMSState() {
        Object air = AIR_NMS_STATE;
        if (air != null) return air;

        synchronized (AdvancedCropBlockBehavior.class) {
            if (AIR_NMS_STATE != null) return AIR_NMS_STATE;
            AIR_NMS_STATE = BlockStateUtils.blockDataToBlockState(
                    Bukkit.createBlockData(org.bukkit.Material.AIR)
            );
            return AIR_NMS_STATE;
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static volatile Object AIR_NMS_STATE;

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return !isMaxAge(state);
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {

        Object state = args[0];
        Object level = args[1];
        Object pos = args[2];
        Object random = args[3];

        if (getRawBrightness(level, pos) < this.minGrowLight) {
            return;
        }

        BlockPos cePos = LocationUtils.fromBlockPos(pos);
        float speed = getGrowthSpeed(thisBlock, level, cePos);
        if (speed < 0f) {
            return;
        }

        int threshold = (int) (25.0F / speed) + 1;
        if (NMSHelper.randomNextInt(random, threshold) != 0) {
            return;
        }

        Optional<ImmutableBlockState> optionalState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optionalState.isEmpty()) return;

        ImmutableBlockState customState = optionalState.get();
        int before = this.getAge(customState);
        if (before >= this.ageProperty.max) return;

        int after = before + 1;
        fireBlockGrowEvent(level, pos,
                customState.with(this.ageProperty, after).customBlockState().minecraftState());

        SoilEntry centerSoil = findSoilEntryFor(level, pos);
        if (centerSoil != null && centerSoil.bonemealChance > 0f
                && NMSHelper.randomNextFloat(random) < centerSoil.bonemealChance) {
            performBoneMeal(level, pos, state);
        }
    }

    @Override
    public boolean isBonemealSuccess(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public boolean isValidBonemealTarget(Object thisBlock, Object[] args) {
        if (!this.isBoneMealTarget) return false;
        Object state = args[2];
        Optional<ImmutableBlockState> optionalState = BlockStateUtils.getOptionalCustomBlockState(state);
        return optionalState.map(s -> getAge(s) != this.ageProperty.max).orElse(false);
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {

        this.performBoneMeal(args[0], args[2], args[3]);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Item item = context.getItem();
        Player player = context.getPlayer();
        if (ItemUtils.isEmpty(item)
                || !item.vanillaId().equals(ItemKeys.BONE_MEAL)
                || player == null
                || player.isAdventureMode()
                || player.isSneaking())
            return InteractionResult.PASS;

        if (isMaxAge(state))
            return InteractionResult.PASS;

        player.swingHand(context.getHand());
        return InteractionResult.SUCCESS;
    }

    private void performBoneMeal(Object level, Object pos, Object state) {
        Optional<ImmutableBlockState> optionalState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optionalState.isEmpty()) return;

        ImmutableBlockState customState = optionalState.get();
        World world = getBukkitWorld(level);
        BlockPos cePos = LocationUtils.fromBlockPos(pos);

        int before = this.getAge(customState);
        int after = before + this.boneMealBonus.getInt(
                SimpleContext.of(ContextHolder.builder()
                        .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, customState)
                        .withParameter(DirectContextParameters.POSITION,
                                new WorldPosition(
                                        net.momirealms.craftengine.bukkit.api.BukkitAdaptor.adapt(world),
                                        Vec3d.atCenterOf(new Vec3i(cePos.x, cePos.y, cePos.z))))
                        .build())
        );
        if (after > this.ageProperty.max) {
            after = this.ageProperty.max;
        }

        if (after > before) {
            boolean success = fireBlockGrowEvent(level, pos,
                    customState.with(this.ageProperty, after).customBlockState().minecraftState());
            if (success) {
                world.spawnParticle(ParticleUtils.HAPPY_VILLAGER,
                        cePos.x + 0.5, cePos.y + 0.5, cePos.z + 0.5,
                        15, 0.25, 0.25, 0.25);
            }
        }
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:advanced_crop"), FACTORY);
    }

    private static class Factory implements BlockBehaviorFactory<AdvancedCropBlockBehavior> {

        private static final String[] GROW_SPEED = {"grow_speed", "grow-speed"};
        private static final String[] LIGHT_REQUIREMENT = {"light_requirement", "light-requirement"};
        private static final String[] SPAWN_LIGHT_REQUIREMENT = {"spawn_light_requirement", "spawn-light-requirement"};
        private static final String[] IS_BONE_MEAL_TARGET = {"is_bone_meal_target", "is-bone-meal-target"};
        private static final String[] AGE_BONUS = {"bone_meal_age_bonus", "bone-meal-age-bonus"};
        private static final String[] GROWTH_MODIFIER = {"growth_modifier", "growth-modifier"};
        private static final String[] BONEMEAL_CHANCE = {"bonemeal_chance", "bonemeal-chance"};
        private static final String[] CAN_HARVEST = {"can_harvest_by_villagers", "can-harvest-by-villagers"};
        private static final String[] CAN_REPLANT = {"can_replant_by_villagers", "can-replant-by-villagers"};

        @Override
        public AdvancedCropBlockBehavior create(BlockDefinition block, ConfigSection section) {
            dev.tako.papersdelight.mechanic.farm.CropBonemealFix.registerCropBlockId(block.id().toString());
            List<SoilEntry> soils = parseSoils(section);
            if (soils.isEmpty()) {
                throw new IllegalArgumentException(
                        section.assemblePath("soils") + ": at least one soil entry is required");
            }

            return new AdvancedCropBlockBehavior(
                    block,
                    BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class),
                    section.getFloat(GROW_SPEED, 0.125f),
                    section.getInt(LIGHT_REQUIREMENT),
                    section.getInt(SPAWN_LIGHT_REQUIREMENT, section.getInt(LIGHT_REQUIREMENT)),
                    section.getBoolean(IS_BONE_MEAL_TARGET, true),
                    section.getNumber(AGE_BONUS, ConfigConstants.CONSTANT_ONE),
                    section.getBoolean(CAN_HARVEST, false),
                    section.getBoolean(CAN_REPLANT, false),
                    soils
            );
        }

        private static List<SoilEntry> parseSoils(ConfigSection section) {
            List<ConfigSection> soilSections = section.getSectionList("soils", s -> s);
            List<SoilEntry> result = new ArrayList<>();

            for (ConfigSection ss : soilSections) {
                String blockStr = ss.getNonEmptyString("block");
                float modifier = ss.getFloat(GROWTH_MODIFIER, 1.0f);
                float bonemealChance = ss.getFloat(BONEMEAL_CHANCE, 0.0f);

                if (modifier < -1f) {
                    throw new IllegalArgumentException(
                            ss.assemblePath(GROWTH_MODIFIER[0])
                                    + ": growth_modifier must be >= -1, got " + modifier);
                }

                if (bonemealChance < 0f || bonemealChance > 1f) {
                    throw new IllegalArgumentException(
                            ss.assemblePath(BONEMEAL_CHANCE[0])
                                    + ": bonemeal_chance must be in [0, 1], got " + bonemealChance);
                }

                Set<Object> vanillaStates = new HashSet<>();
                Set<Key> customIds = new HashSet<>();
                Set<Key> blockTags = new HashSet<>();
                parseBlockDescriptor(blockStr, vanillaStates, customIds, blockTags);
                result.add(new SoilEntry(vanillaStates, customIds, blockTags, modifier, bonemealChance));
            }
            return result;
        }

        private static void parseBlockDescriptor(
                String raw,
                Set<Object> vanillaStates,
                Set<Key> customIds,
                Set<Key> blockTags
        ) {

            if (raw.startsWith("#")) {
                String tagId = raw.substring(1);
                blockTags.add(Key.of(tagId));
                return;
            }

            int bracketIdx = raw.indexOf('[');
            String blockIdStr = bracketIdx != -1 ? raw.substring(0, bracketIdx) : raw;
            boolean hasProperties = bracketIdx != -1;
            Key blockKey = Key.of(blockIdStr);

            List<Object> possibleStates;
            try {
                possibleStates = BlockStateUtils.getPossibleBlockStates(blockKey);
            } catch (Exception e) {
                possibleStates = null;
            }

            if (possibleStates != null && !possibleStates.isEmpty()) {
                Key actualOwner = BlockStateUtils.getBlockOwnerIdFromState(possibleStates.get(0));
                if (actualOwner != null && !actualOwner.equals(blockKey)) {
                    possibleStates = null;
                }
            }

            if (possibleStates != null && !possibleStates.isEmpty()) {

                if (!hasProperties) {

                    vanillaStates.addAll(possibleStates);
                } else {

                    try {
                        org.bukkit.block.data.BlockData bd = Bukkit.createBlockData(raw);
                        vanillaStates.add(BlockStateUtils.blockDataToBlockState(bd));
                    } catch (IllegalArgumentException e) {

                        vanillaStates.addAll(possibleStates);
                    }
                }
            } else {

                if (!hasProperties) {
                    customIds.add(blockKey);
                } else {

                    ImmutableBlockState parsed = BlockStateParser.deserialize(raw);
                    if (parsed != null) {
                        vanillaStates.add(parsed.customBlockState().minecraftState());
                    } else {
                        customIds.add(blockKey);
                    }
                }
            }
        }
    }
}
