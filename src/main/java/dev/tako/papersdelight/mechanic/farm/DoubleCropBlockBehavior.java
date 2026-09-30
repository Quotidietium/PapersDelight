package dev.tako.papersdelight.mechanic.farm;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.block.behavior.CropBlockBehavior;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelAccessorProxy;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
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
import net.momirealms.craftengine.core.block.property.type.DoubleBlockHalf;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemKeys;
import net.momirealms.craftengine.core.util.ItemUtils;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.SimpleContext;
import net.momirealms.craftengine.core.plugin.context.number.NumberProvider;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.*;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;

import java.util.*;

@SuppressWarnings("DuplicatedCode")
public final class DoubleCropBlockBehavior extends BukkitBlockBehavior
        implements BonemealableBlock, RandomTickBlock {

    public static final BlockBehaviorFactory<DoubleCropBlockBehavior> FACTORY = new Factory();

    public final Property<DoubleBlockHalf> halfProperty;
    public final IntegerProperty ageProperty;
    public final Property<Boolean> supportingProperty;
    public final int maxAge;
    public final int upperMaxAge;
    public final boolean plantInWater;
    public final boolean upperIndependent;
    public final boolean syncAges;
    public final int upperMinAge;

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

    private DoubleCropBlockBehavior(
            BlockDefinition blockDefinition,
            Property<DoubleBlockHalf> halfProperty,
            Property<Integer> ageProperty,
            Property<Boolean> supportingProperty,
            int maxAge,
            int upperMaxAge,
            boolean plantInWater,
            boolean upperIndependent,
            boolean syncAges,
            int upperMinAge,
            float growSpeed,
            int minGrowLight,
            int minSpawnLight,
            boolean isBoneMealTarget,
            NumberProvider boneMealBonus,
            boolean canHarvestByVillagers,
            boolean canReplantByVillagers,
            List<SoilEntry> soils
    ) {
        super(blockDefinition);
        this.halfProperty = halfProperty;
        this.ageProperty = (IntegerProperty) ageProperty;
        this.supportingProperty = supportingProperty;
        this.maxAge = maxAge;
        this.upperMaxAge = upperMaxAge;
        this.plantInWater = plantInWater;
        this.upperIndependent = upperIndependent;
        this.syncAges = syncAges;
        this.upperMinAge = upperMinAge;
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
        return state.get(ageProperty) == this.ageProperty.max;
    }

    private static Object getBlockStateNMS(Object level, Object pos) {
        return NMSHelper.getBlockState(level, pos);
    }

    private static void setBlockNMS(Object level, Object pos, Object state) {
        NMSHelper.setBlockState(level, pos, state, 3);
    }

    private static Object airState() {
        return NMSHelper.airStateObj();
    }

    private static Object waterState() {
        try {
            return BlockStateUtils.blockDataToBlockState(
                    org.bukkit.Bukkit.createBlockData(org.bukkit.Material.WATER));
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean isFullWaterSource(Object nmsLevel, Object nmsPos) {
        return NMSHelper.isFullWaterSource(nmsLevel, nmsPos);
    }

    private static org.bukkit.entity.Player getBukkitPlayer(Object nmsPlayer) {
        return NMSHelper.getBukkitPlayer(nmsPlayer);
    }

    private static boolean hasEnoughLight(Object nmsLevel, Object nmsPos) {
        return CropBlockBehavior.getRawBrightness(nmsLevel, nmsPos) >= 8
                || NMSHelper.canSeeSkyAt(nmsLevel, nmsPos);
    }

    private static boolean isYAxis(Object nmsDirection) {
        return DirectionUtils.fromNMSDirection(nmsDirection).axis() == Direction.Axis.Y;
    }

    private static boolean isUp(Object nmsDirection) {
        return DirectionUtils.fromNMSDirection(nmsDirection) == Direction.UP;
    }

    private static boolean isDown(Object nmsDirection) {
        return DirectionUtils.fromNMSDirection(nmsDirection) == Direction.DOWN;
    }

    private static final int US$BLOCKSTATE = 0;
    private static final int US$LEVEL = 1;
    private static final int US$DIRECTION = 4;
    private static final int US$BLOCKPOS = 3;
    private static final int US$NEIGHBOR_STATE = 6;

    private static final int PWD$LEVEL = 0;
    private static final int PWD$POS = 1;
    private static final int PWD$STATE = 2;
    private static final int PWD$PLAYER = 3;

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (pos.y() >= world.worldHeight().getMaxBuildHeight() - 1) return null;

        if (this.plantInWater) {

            if ((context.isWaterSource() || context.getLevel().getBlock(pos.below()).isWaterSource(context))
                    && world.getBlockState(pos.above()).isAir()) {
                return withPlacementDefaults(state);
            }
            return null;
        }

        if (world.getBlockState(pos.above()).isAir()) {
            return withPlacementDefaults(state);
        }
        return null;
    }

    private ImmutableBlockState withPlacementDefaults(ImmutableBlockState state) {
        state = state.with(this.halfProperty, DoubleBlockHalf.LOWER)
                .with(this.ageProperty, 0);
        if (this.supportingProperty != null) {
            state = state.with(this.supportingProperty, false);
        }
        return state;
    }

    @Override
    public boolean canPlaceMultiState(WorldAccessor accessor, BlockPos pos, ImmutableBlockState state) {
        return false;
    }

    @Override
    public boolean hasMultiState(ImmutableBlockState baseState) {
        return false;
    }

    @Override
    public void placeMultiState(Object thisBlock, Object[] args) {

    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        return canSurviveNMS(thisBlock, args[0], args[1], args[2]);
    }

    private boolean canSurviveNMS(Object thisBlock, Object state, Object level, Object blockPos) {
        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(state).orElse(null);
        if (customState == null || customState.isEmpty()) return false;

        DoubleBlockHalf half = customState.get(this.halfProperty);

        if (half == DoubleBlockHalf.UPPER) {
            return canUpperSurvive(customState, level, blockPos);
        }
        return canLowerSurvive(customState, level, blockPos);
    }

    private boolean canUpperSurvive(ImmutableBlockState state, Object level, Object blockPos) {
        if (this.syncAges) return true;
        if (!hasEnoughLight(level, blockPos)) return false;

        Object belowPos = LocationUtils.below(blockPos);
        Object belowState = getBlockStateNMS(level, belowPos);
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom == null || belowCustom.isEmpty()) return false;

        DoubleCropBlockBehavior belowBehavior = belowCustom.behavior()
                .getFirst(DoubleCropBlockBehavior.class);
        if (belowBehavior == null) return false;
        if (belowCustom.owner().value() != super.blockDefinition) return false;
        return belowCustom.get(belowBehavior.halfProperty) == DoubleBlockHalf.LOWER;
    }

    private boolean canLowerSurvive(ImmutableBlockState state, Object level, Object blockPos) {
        return findSoilEntryFor(level, blockPos) != null;
    }

    private Object getBelowStateNMS(Object level, Object pos) {
        Object belowPos = LocationUtils.below(pos);
        return getBlockStateNMS(level, belowPos);
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

    private static boolean isAirBlock(Object nmsState) {
        return nmsState != null && BlockStateUtils.toBlockStateWrapper(nmsState).isAir();
    }

    private float getGrowthSpeed(Object thisBlock, Object level, BlockPos cePos) {
        int cx = cePos.x;
        int cz = cePos.z;
        int soilY = cePos.y - 1;

        Object centerNMS = LocationUtils.toBlockPos(cx, soilY, cz);
        Object centerState = getBlockStateNMS(level, centerNMS);
        SoilEntry centerSoil = findSoilEntryForState(centerState);
        if (centerSoil == null) return -1f;

        float modifier = centerSoil.growthModifier;
        float speed = 1.0F + modifier;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Object neighborNMS = LocationUtils.toBlockPos(cx + dx, soilY, cz + dz);
                Object neighborState = getBlockStateNMS(level, neighborNMS);
                if (centerState.equals(neighborState)) {
                    speed += modifier / 4.0F;
                }
            }
        }

        int y = cePos.y;
        Object northState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx, y, cz - 1));
        Object southState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx, y, cz + 1));
        Object westState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx - 1, y, cz));
        Object eastState = getBlockStateNMS(level, LocationUtils.toBlockPos(cx + 1, y, cz));

        boolean westEast = isSameCropType(westState, thisBlock) || isSameCropType(eastState, thisBlock);
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

    private static int getRawBrightness(Object level, Object pos) {
        return CropBlockBehavior.getRawBrightness(level, pos);
    }

    private boolean hasLightAbove(Object nmsLevel, Object nmsPos, int threshold) {
        return CropBlockBehavior.getRawBrightness(nmsLevel, LocationUtils.above(nmsPos)) >= threshold;
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {

        if (state.get(this.halfProperty) == DoubleBlockHalf.LOWER) return true;

        if (this.syncAges) return false;

        return state.get(this.ageProperty) < this.upperMaxAge;
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {
        Object state = args[0];
        Object level = args[1];
        Object blockPos = args[2];
        Object random = args[3];

        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(state).orElse(null);
        if (customState == null || customState.isEmpty()) return;

        DoubleBlockHalf half = customState.get(this.halfProperty);
        int age = customState.get(this.ageProperty);

        if (half == DoubleBlockHalf.LOWER) {

            if (!hasLightAbove(level, blockPos, 6)) return;

            if (age < this.maxAge) {

                BlockPos cePos = LocationUtils.fromBlockPos(blockPos);
                float speed = getGrowthSpeed(thisBlock, level, cePos);
                if (speed < 0f) return;

                int threshold = (int) (25.0F / speed) + 1;
                if (NMSHelper.randomNextInt(random, threshold) != 0) return;

                int newAge = age + 1;
                fireBlockGrowEvent(level, blockPos,
                        customState.with(this.ageProperty, newAge)
                                .customBlockState().minecraftState());
                syncUpperIfNeeded(level, blockPos, customState, newAge);
            } else {

                tryPlaceUpper(customState, level, blockPos);
            }
        } else {

            if (age < this.upperMaxAge) {
                if (NMSHelper.randomNextInt(random, 3) != 0) return;
                fireBlockGrowEvent(level, blockPos,
                        customState.with(this.ageProperty, age + 1)
                                .customBlockState().minecraftState());
            }
        }
    }

    private boolean fireBlockGrowEvent(Object level, Object pos, Object newState) {
        return NMSHelper.fireBlockGrowEventObj(level, pos, newState, UpdateFlags.UPDATE_CLIENTS);
    }

    private void tryPlaceUpper(ImmutableBlockState lowerState, Object level, Object blockPos) {
        try {
            Object abovePos = LocationUtils.above(blockPos);
            Object aboveCheck = getBlockStateNMS(level, abovePos);

            if (!isAirBlock(aboveCheck)) return;

            int upperAge = this.syncAges
                    ? lowerState.get(this.ageProperty)
                    : 0;

            if (this.syncAges && upperAge < this.upperMinAge) return;

            ImmutableBlockState candidateUpper = lowerState
                    .with(this.halfProperty, DoubleBlockHalf.UPPER)
                    .with(this.ageProperty, upperAge);
            if (this.supportingProperty != null) {
                candidateUpper = candidateUpper.with(this.supportingProperty, false);
            }
            if (canUpperSurvive(candidateUpper, level, abovePos)) {
                setBlockNMS(level, abovePos,
                        candidateUpper.customBlockState().minecraftState());

                if (this.supportingProperty != null) {
                    setBlockNMS(level, blockPos,
                            lowerState.with(this.supportingProperty, true)
                                    .customBlockState().minecraftState());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void syncUpperIfNeeded(Object level, Object blockPos, ImmutableBlockState lowerState, int newAge) {
        if (!this.syncAges) return;
        if (newAge < this.upperMinAge) return;

        try {
            Object abovePos = LocationUtils.above(blockPos);
            Object aboveCheck = getBlockStateNMS(level, abovePos);
            ImmutableBlockState upperCustom = BlockStateUtils.getOptionalCustomBlockState(aboveCheck).orElse(null);
            boolean hasUpper = upperCustom != null && !upperCustom.isEmpty()
                    && upperCustom.owner().value() == this.blockDefinition
                    && upperCustom.get(this.halfProperty) == DoubleBlockHalf.UPPER;

            if (hasUpper) {

                int clampedAge = Math.min(newAge, this.maxAge);
                setBlockNMS(level, abovePos,
                        upperCustom.with(this.ageProperty, clampedAge)
                                .customBlockState().minecraftState());
            } else if (isAirBlock(aboveCheck)) {

                ImmutableBlockState newUpper = lowerState
                        .with(this.halfProperty, DoubleBlockHalf.UPPER)
                        .with(this.ageProperty, Math.min(newAge, this.maxAge));
                if (this.supportingProperty != null) {
                    newUpper = newUpper.with(this.supportingProperty, false);
                }
                if (canUpperSurvive(newUpper, level, abovePos)) {
                    setBlockNMS(level, abovePos, newUpper.customBlockState().minecraftState());
                    if (this.supportingProperty != null) {
                        setBlockNMS(level, blockPos,
                                lowerState.with(this.supportingProperty, true)
                                        .customBlockState().minecraftState());
                    }
                }
            }
        } catch (Exception ignored) {
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
        if (optionalState.isEmpty()) return false;

        ImmutableBlockState customState = optionalState.get();
        DoubleBlockHalf half = customState.get(this.halfProperty);
        int age = customState.get(this.ageProperty);

        if (half == DoubleBlockHalf.UPPER) {

            if (this.syncAges) return false;

            return age < this.upperMaxAge;
        } else {

            if (age < this.ageProperty.max) return true;

            Object level = args[0];
            Object pos = args[1];
            Object abovePos = LocationUtils.above(pos);
            Object aboveState = getBlockStateNMS(level, abovePos);
            Optional<ImmutableBlockState> aboveCustom =
                    BlockStateUtils.getOptionalCustomBlockState(aboveState);
            if (aboveCustom.isEmpty() || aboveCustom.get().isEmpty()
                    || aboveCustom.get().owner().value() != this.blockDefinition) {
                return true;
            }

            return aboveCustom.get().get(this.ageProperty) < this.upperMaxAge;
        }
    }

    @Override
    public void performBonemeal(Object thisBlock, Object[] args) {

        this.performBoneMeal(args[0], args[2], args[3]);
    }

    private void performBoneMeal(Object level, Object pos, Object state) {
        Optional<ImmutableBlockState> optionalState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optionalState.isEmpty()) return;

        ImmutableBlockState customState = optionalState.get();
        DoubleBlockHalf half = customState.get(this.halfProperty);

        if (half == DoubleBlockHalf.UPPER) {
            if (this.syncAges) return;

            growUpperHalf(level, pos, customState);
        } else {

            if (this.syncAges) {
                growLowerSync(level, pos, customState);
                return;
            }
            Object abovePos = LocationUtils.above(pos);
            Object aboveState = getBlockStateNMS(level, abovePos);
            Optional<ImmutableBlockState> aboveCustom =
                    BlockStateUtils.getOptionalCustomBlockState(aboveState);

            if (aboveCustom.isPresent() && !aboveCustom.get().isEmpty()
                    && aboveCustom.get().owner().value() == this.blockDefinition
                    && aboveCustom.get().get(this.halfProperty) == DoubleBlockHalf.UPPER) {

                growUpperHalf(level, abovePos, aboveCustom.get());
            } else {

                growLowerWithOverflow(level, pos, customState, abovePos);
            }
        }
    }

    private void growUpperHalf(Object level, Object abovePos, ImmutableBlockState upperState) {
        org.bukkit.World bukkitWorld = getBukkitWorld(level);
        net.momirealms.craftengine.core.world.World ceWorld = getCEWorld(level);
        BlockPos cePos = LocationUtils.fromBlockPos(abovePos);

        int before = upperState.get(this.ageProperty);
        ContextHolder holder = ContextHolder.builder()
                .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, upperState)
                .withParameter(DirectContextParameters.POSITION,
                        ceWorld != null ? new WorldPosition(ceWorld,
                                Vec3d.atCenterOf(new Vec3i(cePos.x, cePos.y, cePos.z))) : null)
                .build();
        int bonus = this.boneMealBonus.getInt(SimpleContext.of(holder));
        int after = Math.min(before + bonus, this.upperMaxAge);

        if (after > before) {
            boolean success = fireBlockGrowEvent(level, abovePos,
                    upperState.with(this.ageProperty, after)
                            .customBlockState().minecraftState());
            if (success && bukkitWorld != null) {
                bukkitWorld.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                        cePos.x + 0.5, cePos.y + 0.5, cePos.z + 0.5,
                        15, 0.25, 0.25, 0.25);
            }
        }
    }

    private void growLowerSync(Object level, Object pos, ImmutableBlockState lowerState) {
        org.bukkit.World bukkitWorld = getBukkitWorld(level);
        net.momirealms.craftengine.core.world.World ceWorld = getCEWorld(level);
        BlockPos cePos = LocationUtils.fromBlockPos(pos);

        int before = lowerState.get(this.ageProperty);
        ContextHolder holder = ContextHolder.builder()
                .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, lowerState)
                .withParameter(DirectContextParameters.POSITION,
                        ceWorld != null ? new WorldPosition(ceWorld,
                                Vec3d.atCenterOf(new Vec3i(cePos.x, cePos.y, cePos.z))) : null)
                .build();
        int bonus = this.boneMealBonus.getInt(SimpleContext.of(holder));
        int after = Math.min(before + bonus, this.maxAge);

        if (after > before) {
            boolean success = fireBlockGrowEvent(level, pos,
                    lowerState.with(this.ageProperty, after)
                            .customBlockState().minecraftState());
            if (success) {
                syncUpperIfNeeded(level, pos, lowerState, after);
                if (bukkitWorld != null) {
                    bukkitWorld.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                            cePos.x + 0.5, cePos.y + 0.5, cePos.z + 0.5,
                            15, 0.25, 0.25, 0.25);
                }
            }
        }
    }

    private void growLowerWithOverflow(Object level, Object pos,
                                       ImmutableBlockState lowerState, Object abovePos) {
        org.bukkit.World bukkitWorld = getBukkitWorld(level);
        net.momirealms.craftengine.core.world.World ceWorld = getCEWorld(level);
        BlockPos cePos = LocationUtils.fromBlockPos(pos);

        int before = lowerState.get(this.ageProperty);
        ContextHolder holder = ContextHolder.builder()
                .withParameter(DirectContextParameters.CUSTOM_BLOCK_STATE, lowerState)
                .withParameter(DirectContextParameters.POSITION,
                        ceWorld != null ? new WorldPosition(ceWorld,
                                Vec3d.atCenterOf(new Vec3i(cePos.x, cePos.y, cePos.z))) : null)
                .build();
        int bonus = this.boneMealBonus.getInt(SimpleContext.of(holder));

        int totalAge = Math.min(before + bonus, this.ageProperty.max + this.upperMaxAge);

        if (totalAge <= before) return;

        if (totalAge <= this.ageProperty.max) {

            boolean success = fireBlockGrowEvent(level, pos,
                    lowerState.with(this.ageProperty, totalAge)
                            .customBlockState().minecraftState());
            if (success && bukkitWorld != null) {
                bukkitWorld.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                        cePos.x + 0.5, cePos.y + 0.5, cePos.z + 0.5,
                        15, 0.25, 0.25, 0.25);
            }
        } else {

            int overflowAge = totalAge - this.ageProperty.max;

            Object aboveCheck = getBlockStateNMS(level, abovePos);
            boolean canPlaceUpper = aboveCheck != null && isAirBlock(aboveCheck);

            ImmutableBlockState lowerMax = lowerState
                    .with(this.ageProperty, this.ageProperty.max);
            if (this.supportingProperty != null) {
                lowerMax = lowerMax.with(this.supportingProperty, canPlaceUpper);
            }
            fireBlockGrowEvent(level, pos, lowerMax.customBlockState().minecraftState());

            if (canPlaceUpper) {
                ImmutableBlockState upperState = lowerState
                        .with(this.halfProperty, DoubleBlockHalf.UPPER)
                        .with(this.ageProperty, overflowAge);
                if (this.supportingProperty != null) {
                    upperState = upperState.with(this.supportingProperty, false);
                }
                setBlockNMS(level, abovePos, upperState.customBlockState().minecraftState());
            }

            if (bukkitWorld != null) {
                bukkitWorld.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                        cePos.x + 0.5, cePos.y + 0.5, cePos.z + 0.5,
                        15, 0.25, 0.25, 0.25);
            }
        }
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

        if (!this.isBoneMealTarget) return InteractionResult.PASS;

        DoubleBlockHalf half = state.get(this.halfProperty);
        int age = state.get(this.ageProperty);

        if (this.syncAges && half == DoubleBlockHalf.UPPER) {
            return InteractionResult.PASS;
        }
        if (half == DoubleBlockHalf.UPPER && age >= this.upperMaxAge) {
            return InteractionResult.PASS;
        }
        if (half == DoubleBlockHalf.LOWER && age >= this.ageProperty.max) {

            if (this.syncAges) return InteractionResult.PASS;

            Object aboveState = getBlockStateNMS(context.getLevel().minecraftWorld(),
                    LocationUtils.above(LocationUtils.toBlockPos(
                            context.getClickedPos().x(),
                            context.getClickedPos().y(),
                            context.getClickedPos().z())));
            Optional<ImmutableBlockState> aboveCustom =
                    BlockStateUtils.getOptionalCustomBlockState(aboveState);
            if (aboveCustom.isPresent() && !aboveCustom.get().isEmpty()
                    && aboveCustom.get().owner().value() == this.blockDefinition
                    && aboveCustom.get().get(this.ageProperty) >= this.upperMaxAge) {

                return InteractionResult.PASS;
            }
        }

        player.swingHand(context.getHand());
        return InteractionResult.SUCCESS;
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object level = args[US$LEVEL];
        Object blockPos = args[US$BLOCKPOS];
        Object blockState = args[US$BLOCKSTATE];
        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(blockState).orElse(null);
        if (customState == null || customState.isEmpty()) return blockState;

        DoubleBlockHalf half = customState.get(this.halfProperty);
        Object direction = args[US$DIRECTION];

        if (isYAxis(direction)) {
            if (half == DoubleBlockHalf.UPPER && isDown(direction)) {

                if (this.syncAges) return blockState;

                ImmutableBlockState neighborState = BlockStateUtils
                        .getOptionalCustomBlockState(args[US$NEIGHBOR_STATE]).orElse(null);
                if (neighborState == null || neighborState.isEmpty()
                        || neighborState.owner().value() != this.blockDefinition
                        || neighborState.get(this.halfProperty) != DoubleBlockHalf.LOWER) {
                    return airState();
                }
                return blockState;
            }

            if (half == DoubleBlockHalf.LOWER && isUp(direction)) {

                if (this.supportingProperty != null) {
                    ImmutableBlockState neighborState = BlockStateUtils
                            .getOptionalCustomBlockState(args[US$NEIGHBOR_STATE]).orElse(null);
                    boolean hasUpper = neighborState != null && !neighborState.isEmpty()
                            && neighborState.owner().value() == this.blockDefinition
                            && neighborState.get(this.halfProperty) == DoubleBlockHalf.UPPER;
                    return customState.with(this.supportingProperty, hasUpper)
                            .customBlockState().minecraftState();
                }
                return blockState;
            }
        }

        if (half == DoubleBlockHalf.LOWER && isDown(direction)
                && !canSurviveNMS(thisBlock, blockState, level, blockPos)) {
            destroyLowerAndUpper(level, blockPos, customState);
            return this.plantInWater ? waterState() : airState();
        }

        return blockState;
    }

    @Override
    public void onPlace(Object thisBlock, Object[] args) {
        LevelAccessorProxy.INSTANCE.scheduleTick$0(args[1], args[2], thisBlock, 2);
    }

    @Override
    public void tick(Object thisBlock, Object[] args) {
        if (!canSurviveNMS(thisBlock, args[0], args[1], args[2])) {
            BlockStateUtils.getOptionalCustomBlockState(args[0]).ifPresent(customState -> {
                if (!customState.isEmpty() && customState.owner().value() == this.blockDefinition) {
                    destroyLowerAndUpper(args[1], args[2], customState);
                }
            });
        }
    }

    @Override
    public Object playerWillDestroy(Object thisBlock, Object[] args) {
        Object level = args[PWD$LEVEL];
        Object pos = args[PWD$POS];
        Object state = args[PWD$STATE];
        Object player = args[PWD$PLAYER];

        ImmutableBlockState blockState = BlockStateUtils.getOptionalCustomBlockState(state).orElse(null);
        if (blockState == null || blockState.isEmpty()) return state;

        DoubleBlockHalf half = blockState.get(this.halfProperty);

        if (half == DoubleBlockHalf.LOWER) {
            org.bukkit.entity.Player bukkitPlayer = getBukkitPlayer(player);
            if (bukkitPlayer != null) {
                BukkitServerPlayer cePlayer = BukkitAdaptor.adapt(bukkitPlayer);
                if (cePlayer != null) {
                    Item item = cePlayer.getItemInHand(InteractionHand.MAIN_HAND);
                    if (cePlayer.canInstabuild() || !BlockStateUtils.isCorrectTool(blockState, item)) {
                        destroyUpperHalf(level, pos);
                    }
                }
            }
        }

        if (half == DoubleBlockHalf.UPPER && !this.upperIndependent) {
            destroyLowerHalf(level, pos);
        }

        return state;
    }

    private void destroyUpperHalf(Object level, Object pos) {
        Object abovePos = LocationUtils.above(pos);
        Object aboveState = getBlockStateNMS(level, abovePos);
        ImmutableBlockState aboveCustom = BlockStateUtils.getOptionalCustomBlockState(aboveState).orElse(null);
        if (aboveCustom != null && !aboveCustom.isEmpty()
                && aboveCustom.owner().value() == this.blockDefinition
                && aboveCustom.get(this.halfProperty) == DoubleBlockHalf.UPPER) {
            setBlockNMS(level, abovePos, airState());
        }
    }

    private void destroyLowerHalf(Object level, Object pos) {
        Object belowPos = LocationUtils.below(pos);
        Object belowState = getBlockStateNMS(level, belowPos);
        ImmutableBlockState belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustom != null && !belowCustom.isEmpty()
                && belowCustom.owner().value() == this.blockDefinition
                && belowCustom.get(this.halfProperty) == DoubleBlockHalf.LOWER) {
            setBlockNMS(level, belowPos, airState());
        }
    }

    private void destroyLowerAndUpper(Object level, Object pos, ImmutableBlockState customState) {

        Object abovePos = LocationUtils.above(pos);
        Object aboveState = getBlockStateNMS(level, abovePos);
        ImmutableBlockState aboveCustom = BlockStateUtils.getOptionalCustomBlockState(aboveState).orElse(null);
        if (aboveCustom != null && !aboveCustom.isEmpty()
                && aboveCustom.owner().value() == this.blockDefinition) {
            setBlockNMS(level, abovePos, airState());
        }
        BlockPos cePos = LocationUtils.fromBlockPos(pos);
        net.momirealms.craftengine.core.world.World ceWorld = getCEWorld(level);
        if (ceWorld != null) {
            ceWorld.playBlockSound(new WorldPosition(ceWorld, Vec3d.atCenterOf(cePos)),
                    customState.settings().sounds().breakSound());
            setBlockNMS(level, pos, this.plantInWater ? waterState() : airState());
        }
    }

    @Override
    public void affectNeighborsAfterRemoval(Object thisBlock, Object[] args) {
        if (!this.plantInWater) return;
        Object state = args[0];
        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(state).orElse(null);
        if (customState == null || customState.isEmpty()) return;

        DoubleBlockHalf half = customState.get(this.halfProperty);
        if (half == DoubleBlockHalf.LOWER) {
            setBlockNMS(args[1], args[2], waterState());
        }
    }

    private static org.bukkit.World getBukkitWorld(Object nmsLevel) {
        return NMSHelper.bukkitWorldOf(nmsLevel);
    }

    private static net.momirealms.craftengine.core.world.World getCEWorld(Object nmsLevel) {
        org.bukkit.World bukkitWorld = getBukkitWorld(nmsLevel);
        if (bukkitWorld == null) return null;
        return BukkitAdaptor.adapt(bukkitWorld);
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:double_crop"), FACTORY);
    }

    private static final String[] GROW_SPEED = {"grow_speed", "grow-speed"};
    private static final String[] LIGHT_REQUIREMENT = {"light_requirement", "light-requirement"};
    private static final String[] SPAWN_LIGHT_REQUIREMENT = {"spawn_light_requirement", "spawn-light-requirement"};
    private static final String[] IS_BONE_MEAL_TARGET = {"is_bone_meal_target", "is-bone-meal-target"};
    private static final String[] AGE_BONUS = {"bone_meal_age_bonus", "bone-meal-age-bonus"};
    private static final String[] GROWTH_MODIFIER = {"growth_modifier", "growth-modifier"};
    private static final String[] BONEMEAL_CHANCE = {"bonemeal_chance", "bonemeal-chance"};
    private static final String[] UPPER_INDEPENDENT = {"upper_independent", "upper-independent"};
    private static final String[] SUPPORTING = {"supporting"};
    private static final String[] CAN_HARVEST = {"can_harvest_by_villagers", "can-harvest-by-villagers"};
    private static final String[] CAN_REPLANT = {"can_replant_by_villagers", "can-replant-by-villagers"};

    private static class Factory implements BlockBehaviorFactory<DoubleCropBlockBehavior> {

        @Override
        @SuppressWarnings("unchecked")
        public DoubleCropBlockBehavior create(BlockDefinition block, ConfigSection section) {
            dev.tako.papersdelight.mechanic.farm.CropBonemealFix.registerCropBlockId(block.id().toString());
            List<SoilEntry> soils = parseSoils(section);
            if (soils.isEmpty()) {
                throw new IllegalArgumentException(
                        section.assemblePath("soils") + ": at least one soil entry is required");
            }

            String supportingPropName = section.getString(SUPPORTING, (String) null);
            Property<Boolean> supportingProperty = null;
            if (supportingPropName != null && !supportingPropName.isEmpty()) {
                supportingProperty = (Property<Boolean>) (Property<?>)
                        getOptionalProperty(section.path(), block, supportingPropName, Boolean.class);
            }

            boolean upperIndependent = section.getBoolean(UPPER_INDEPENDENT, true);
            boolean syncAges = section.getBoolean(new String[]{"sync_ages", "sync-ages"}, false);
            int maxAge = section.getInt(new String[]{"max_age", "max-age"}, 3);
            int upperMaxAge = section.getInt(new String[]{"upper_max_age", "upper-max-age"}, maxAge);
            int upperMinAge = section.getInt(new String[]{"upper_min_age", "upper-min-age"}, 0);

            return new DoubleCropBlockBehavior(
                    block,
                    BlockBehaviorFactory.getProperty(section.path(), block, "half", DoubleBlockHalf.class),
                    BlockBehaviorFactory.getProperty(section.path(), block, "age", Integer.class),
                    supportingProperty,
                    maxAge,
                    upperMaxAge,
                    section.getBoolean("plant_in_water"),
                    upperIndependent,
                    syncAges,
                    upperMinAge,
                    section.getFloat(GROW_SPEED, 0.125f),
                    section.getInt(LIGHT_REQUIREMENT, 6),
                    section.getInt(SPAWN_LIGHT_REQUIREMENT, section.getInt(LIGHT_REQUIREMENT, 6)),
                    section.getBoolean(IS_BONE_MEAL_TARGET, true),
                    section.getNumber(AGE_BONUS, ConfigConstants.CONSTANT_ONE),
                    section.getBoolean(CAN_HARVEST, false),
                    section.getBoolean(CAN_REPLANT, false),
                    soils
            );
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Property<?> getOptionalProperty(String path, BlockDefinition block, String propName, Class type) {
            try {
                return BlockBehaviorFactory.getProperty(path, block, propName, type);
            } catch (Exception e) {
                return null;
            }
        }

        private static List<SoilEntry> parseSoils(ConfigSection section) {
            List<ConfigSection> soilSections = section.getSectionList("soils", s -> s);
            List<SoilEntry> result = new ArrayList<>();

            for (ConfigSection ss : soilSections) {
                String blockStr = ss.getNonEmptyString("block");
                float modifier = ss.getFloat(GROWTH_MODIFIER, 1.0f);
                float bonemealChance = ss.getFloat(BONEMEAL_CHANCE, 0.0f);

                if (modifier < 0f) {
                    throw new IllegalArgumentException(
                            ss.assemblePath(GROWTH_MODIFIER[0])
                                    + ": growth_modifier must be >= 0, got " + modifier);
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
                        org.bukkit.block.data.BlockData bd = org.bukkit.Bukkit.createBlockData(raw);
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
