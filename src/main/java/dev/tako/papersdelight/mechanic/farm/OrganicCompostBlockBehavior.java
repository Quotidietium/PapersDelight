package dev.tako.papersdelight.mechanic.farm;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.parser.BlockStateParser;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.random.RandomUtils;
import net.momirealms.craftengine.core.world.BlockPos;

import java.util.*;

public final class OrganicCompostBlockBehavior extends BukkitBlockBehavior implements RandomTickBlock {

    public static final BlockBehaviorFactory<OrganicCompostBlockBehavior> FACTORY = new Factory();

    private final IntegerProperty compostingProperty;
    private final int maxStage;
    private final String resultBlock;
    private final double activatorMultiplier;
    private final double lightBonusHigh;
    private final double lightBonusLow;
    private final double waterBonus;
    private final int lightThreshold;
    private final Set<Key> activators;
    private final boolean hasComparator;

    private OrganicCompostBlockBehavior(BlockDefinition block,
                                        IntegerProperty compostingProperty,
                                        int maxStage,
                                        String resultBlock,
                                        double activatorMultiplier,
                                        double lightBonusHigh,
                                        double lightBonusLow,
                                        double waterBonus,
                                        int lightThreshold,
                                        Set<Key> activators,
                                        boolean hasComparator) {
        super(block);
        this.compostingProperty = compostingProperty;
        this.maxStage = maxStage;
        this.resultBlock = resultBlock;
        this.activatorMultiplier = activatorMultiplier;
        this.lightBonusHigh = lightBonusHigh;
        this.lightBonusLow = lightBonusLow;
        this.waterBonus = waterBonus;
        this.lightThreshold = lightThreshold;
        this.activators = Set.copyOf(activators);
        this.hasComparator = hasComparator;
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return true;
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {

        Object state = args[0];
        Object level = args[1];
        Object pos = args[2];

        Optional<ImmutableBlockState> optState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optState.isEmpty()) return;
        ImmutableBlockState customState = optState.get();

        int stage = getComposting(customState);
        if (stage >= this.maxStage) return;

        double chance = calculateChance(level, pos);
        if (RandomUtils.generateRandomDouble(0.0, 1.0) > chance) {
            return;
        }

        if (stage + 1 >= this.maxStage) {

            convertToResult(level, pos);
        } else {

            ImmutableBlockState newState = customState.with(this.compostingProperty, stage + 1);
            setBlockState(level, pos, newState);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(Object thisBlock, Object[] args) {
        return this.hasComparator;
    }

    @Override
    public int getAnalogOutputSignal(Object thisBlock, Object[] args) {
        if (!this.hasComparator) return 0;
        Object state = args[0];
        Optional<ImmutableBlockState> optState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optState.isEmpty()) return 0;
        ImmutableBlockState customState = optState.get();
        int stage = getComposting(customState);
        return (this.maxStage + 1) - stage;
    }

    private double calculateChance(Object level, Object pos) {
        double chance = 0.0;
        boolean hasWater = false;
        int maxLight = 0;

        BlockPos cePos = LocationUtils.fromBlockPos(pos);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = cePos.x + dx;
                    int ny = cePos.y + dy;
                    int nz = cePos.z + dz;

                    Object neighborPos = LocationUtils.toBlockPos(nx, ny, nz);
                    Object neighborState = NMSHelper.getBlockState(level, neighborPos);

                    if (neighborState != null) {
                        Key blockKey = BlockStateUtils.getBlockOwnerIdFromState(neighborState);
                        if (blockKey != null && this.activators.contains(blockKey)) {
                            chance += this.activatorMultiplier;
                        }
                    }

                    if (!hasWater && neighborState != null) {
                        hasWater = hasWaterAt(level, neighborPos, neighborState);
                    }

                    if (dy == 1 && dx == 0 && dz == 0) {
                        int skyLight = NMSHelper.getSkyBrightnessAt(level, neighborPos);
                        if (skyLight > maxLight) maxLight = skyLight;
                    }
                }
            }
        }

        chance += (maxLight > this.lightThreshold) ? this.lightBonusHigh : this.lightBonusLow;

        if (hasWater) {
            chance += this.waterBonus;
        }

        return chance;
    }

    private int getComposting(ImmutableBlockState state) {
        Object val = state.get(this.compostingProperty);
        return val instanceof Number n ? n.intValue() : 0;
    }

    private boolean hasWaterAt(Object level, Object pos, Object blockState) {
        return NMSHelper.hasWaterFluidAt(level, pos);
    }

    private void setBlockState(Object level, Object pos, ImmutableBlockState ceState) {
        NMSHelper.setBlockState(level, pos, ceState.customBlockState().minecraftState(), 3);
    }

    private void convertToResult(Object level, Object pos) {
        if (this.resultBlock == null || this.resultBlock.isEmpty()) return;
        ImmutableBlockState targetState = BlockStateParser.deserialize(this.resultBlock);
        if (targetState == null) return;
        setBlockState(level, pos, targetState);
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:organic_compost"), FACTORY);
    }

    private static class Factory implements BlockBehaviorFactory<OrganicCompostBlockBehavior> {

        private static final String[] COMPOSTING = {"composting"};
        private static final String[] RESULT = {"result"};
        private static final String[] ACTIVATOR_MULTIPLIER = {"activator_multiplier", "activator-multiplier"};
        private static final String[] LIGHT_BONUS_HIGH = {"light_bonus_high", "light-bonus-high"};
        private static final String[] LIGHT_BONUS_LOW = {"light_bonus_low", "light-bonus-low"};
        private static final String[] WATER_BONUS = {"water_bonus", "water-bonus"};
        private static final String[] LIGHT_THRESHOLD = {"light_threshold", "light-threshold"};
        private static final String[] ACTIVATORS = {"activators"};
        private static final String[] HAS_COMPARATOR = {"has_comparator", "has-comparator"};

        @Override
        public OrganicCompostBlockBehavior create(BlockDefinition block, ConfigSection section) {

            Property<Integer> compostingProp = BlockBehaviorFactory.getProperty(
                    section.path(), block, "composting", Integer.class);
            IntegerProperty compostingProperty = (IntegerProperty) compostingProp;
            int max = compostingProperty.max;

            String result = section.getString(RESULT, "farmersdelight:rich_soil");

            double activatorMul = section.getFloat(ACTIVATOR_MULTIPLIER, 0.02f);
            double lightHigh = section.getFloat(LIGHT_BONUS_HIGH, 0.1f);
            double lightLow = section.getFloat(LIGHT_BONUS_LOW, 0.05f);
            double waterBon = section.getFloat(WATER_BONUS, 0.1f);
            int lightThresh = section.getInt(LIGHT_THRESHOLD, 12);

            Set<Key> activatorSet = new HashSet<>();
            List<String> list = section.getStringList(ACTIVATORS, new ArrayList<>());
            for (String s : list) {
                activatorSet.add(Key.of(s));
            }

            boolean hasComparator = section.getBoolean(HAS_COMPARATOR, true);

            return new OrganicCompostBlockBehavior(block, compostingProperty, max,
                    result, activatorMul, lightHigh, lightLow, waterBon, lightThresh, activatorSet, hasComparator);
        }
    }
}
