package dev.tako.papersdelight.mechanic.farm;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelAccessorProxy;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.PrioritizedFallOnHandler;
import net.momirealms.craftengine.core.block.behavior.RandomTickBlock;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.registry.BuiltInRegistries;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import java.util.Optional;

public final class FarmlandBlockBehavior extends BukkitBlockBehavior
        implements RandomTickBlock, PrioritizedFallOnHandler {

    public static final BlockBehaviorFactory<FarmlandBlockBehavior> FACTORY = new Factory();

    private static final Key MAINTAINS_FARMLAND_TAG = Key.of("minecraft", "maintains_farmland");

    private static Key getMaintainsFarmlandTag() {
        return MAINTAINS_FARMLAND_TAG;
    }

    private final int maxMoisture;
    private final Property<Integer> moistureProperty;
    private final int waterRange;
    private final boolean trampling;
    private final String turnToBlock;
    private final java.util.Set<Key> solidAboveWhitelist;
    private final java.util.Set<Key> solidAboveBlacklist;

    private FarmlandBlockBehavior(
            BlockDefinition block,
            Property<Integer> moistureProperty,
            int maxMoisture,
            int waterRange,
            boolean trampling,
            String turnToBlock,
            java.util.Set<Key> solidAboveWhitelist,
            java.util.Set<Key> solidAboveBlacklist
    ) {
        super(block);
        this.moistureProperty = moistureProperty;
        this.maxMoisture = maxMoisture;
        this.waterRange = waterRange;
        this.trampling = trampling;
        this.turnToBlock = turnToBlock;
        this.solidAboveWhitelist = solidAboveWhitelist;
        this.solidAboveBlacklist = solidAboveBlacklist;
    }

    @Override
    public boolean canRandomlyTick(ImmutableBlockState state) {
        return true;
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        Object level = args[1];
        Object nmsPos = args[2];

        Object abovePos = above(nmsPos);
        Object aboveState = getBlockStateNMS(level, abovePos);
        if (aboveState == null) return false;

        Key aboveBlockKey = BlockStateUtils.getBlockOwnerIdFromState(aboveState);

        if (aboveBlockKey != null && solidAboveBlacklist.contains(aboveBlockKey)) return false;
        java.util.Optional<ImmutableBlockState> aboveCustom = BlockStateUtils.getOptionalCustomBlockState(aboveState);
        if (aboveCustom.isPresent() && solidAboveBlacklist.contains(aboveCustom.get().owner().value().id())) return false;

        if (!isSolidBlock(aboveState)) return true;

        if (isInTag(aboveState, getMaintainsFarmlandTag())) return true;

        if (aboveBlockKey != null && solidAboveWhitelist.contains(aboveBlockKey)) return true;
        if (aboveCustom.isPresent() && solidAboveWhitelist.contains(aboveCustom.get().owner().value().id())) return true;

        if (aboveCustom.isPresent()) return true;

        return false;
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Direction direction = DirectionUtils.fromNMSDirection(args[updateShape$direction]);
        if (direction != Direction.UP) return args[0];

        Object[] survivalArgs = new Object[]{args[0], args[updateShape$level], args[updateShape$blockPos]};
        if (!canSurvive(thisBlock, survivalArgs)) {
            scheduleTick(args[updateShape$level], args[updateShape$blockPos], args[0], 1);
        }
        return args[0];
    }

    @Override
    public void tick(Object thisBlock, Object[] args) {
        Object[] survivalArgs = new Object[]{args[0], args[1], args[2]};
        if (!canSurvive(thisBlock, survivalArgs)) {
            turnToDirt(args[1], args[2]);
        }
    }

    @Override
    public void randomTick(Object thisBlock, Object[] args) {
        Object state = args[0];
        Object level = args[1];
        Object nmsPos = args[2];

        Optional<ImmutableBlockState> opt = getCustomState(state);
        if (opt.isEmpty()) return;
        ImmutableBlockState customState = opt.get();

        int moisture = customState.get(this.moistureProperty);
        Object abovePos = above(nmsPos);

        if (isNearWater(level, nmsPos) || isRainingAt(level, abovePos)) {
            if (moisture < this.maxMoisture) {
                setMoisture(level, nmsPos, customState, this.maxMoisture);
            }
        } else {
            if (moisture > 0) {
                setMoisture(level, nmsPos, customState, moisture - 1);
            } else if (this.trampling) {
                Object aboveState = getBlockStateNMS(level, abovePos);
                if (!isInTag(aboveState, getMaintainsFarmlandTag())) {
                    turnToDirt(level, nmsPos);
                }
            }
        }
    }

    @Override
    public void fallOn(Object thisBlock, Object[] args) {
        if (!this.trampling) return;

        Object level = args[0];
        if (!isServerLevel(level)) return;

        Object entity = args[3];
        double fallDistance = getFallDistance(args[4]);

        if (NMSHelper.entityNextFloat(entity) >= fallDistance - 0.5) return;

        if (!NMSHelper.isLivingEntity(entity)) return;

        if (!NMSHelper.isPlayerEntity(entity) && !NMSHelper.isMobGriefing(level)) return;

        float width = NMSHelper.entityBbWidth(entity);
        float height = NMSHelper.entityBbHeight(entity);
        if (width * width * height <= 0.512f) return;

        turnToDirt(level, args[2]);
    }

    @Override
    public void updateEntityMovementAfterFallOn(Object thisBlock, Object[] args) {
        NMSHelper.zeroEntityDeltaY(args[1]);
    }

    private boolean isNearWater(Object level, Object nmsPos) {
        for (int dx = -this.waterRange; dx <= this.waterRange; dx++) {
            for (int dz = -this.waterRange; dz <= this.waterRange; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    Object checkPos = offset(nmsPos, dx, dy, dz);
                    if (NMSHelper.hasWaterFluidAt(level, checkPos)) return true;
                }
            }
        }
        return false;
    }

    private boolean isRainingAt(Object level, Object pos) {
        return NMSHelper.isRainingAtPos(level, pos);
    }

    private static boolean isSolidBlock(Object nmsState) {
        return NMSHelper.isStateSolid(nmsState);
    }

    private static boolean isInTag(Object nmsState, Key tag) {
        return nmsState != null && tag != null && BlockStateUtils.isTag(nmsState, tag);
    }

    private void turnToDirt(Object level, Object nmsPos) {
        try {
            Key key = Key.of(this.turnToBlock);
            BlockDefinition def = BuiltInRegistries.BLOCK.getValue(key);
            if (def != null) {
                Object nmsState = def.defaultState().customBlockState().minecraftState();
                setBlockNMS(level, nmsPos, nmsState);
            } else {

                org.bukkit.block.data.BlockData bd =
                        org.bukkit.Bukkit.createBlockData(this.turnToBlock);
                Object state = BlockStateUtils.blockDataToBlockState(bd);
                setBlockNMS(level, nmsPos, state);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to turn farmland to " + this.turnToBlock, e);
        }
    }

    private void setMoisture(Object level, Object nmsPos,
                             ImmutableBlockState currentState, int value) {
        ImmutableBlockState newState = currentState.with(this.moistureProperty, value);
        setBlockNMS(level, nmsPos, newState.customBlockState().minecraftState());
    }

    private static Object getBlockStateNMS(Object level, Object pos) {
        return NMSHelper.getBlockState(level, pos);
    }

    private static void setBlockNMS(Object level, Object pos, Object state) {
        NMSHelper.setBlockState(level, pos, state, 3);
    }

    private static Optional<ImmutableBlockState> getCustomState(Object nmsState) {
        return BlockStateUtils.getOptionalCustomBlockState(nmsState);
    }

    private static Object above(Object nmsPos) {
        return LocationUtils.above(nmsPos);
    }

    private static Object offset(Object nmsPos, int dx, int dy, int dz) {
        return NMSHelper.offsetPos(nmsPos, dx, dy, dz);
    }

    private static void scheduleTick(Object level, Object pos, Object state, int delay) {
        LevelAccessorProxy.INSTANCE.scheduleTick$0(level, pos, BlockStateUtils.getBlockOwner(state), delay);
    }

    private static boolean isServerLevel(Object level) {
        return NMSHelper.isServerLevel(level);
    }

    private static double getFallDistance(Object fallDistArg) {
        if (fallDistArg instanceof Double d) return d;
        if (fallDistArg instanceof Float f) return f.doubleValue();
        if (fallDistArg instanceof Number n) return n.doubleValue();
        return 0;
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:farmland"), FACTORY);
    }

    private static final String[] MAX_MOISTURE = {"max_moisture", "max-moisture"};
    private static final String[] WATER_RANGE = {"water_range", "water-range"};
    private static final String[] TRAMPLING = {"trampling"};
    private static final String[] TURN_TO = {"turn_to", "turn-to"};
    private static final String[] SOLID_ABOVE_WHITELIST = {"solid_above_whitelist", "solid-above-whitelist"};
    private static final String[] SOLID_ABOVE_BLACKLIST = {"solid_above_blacklist", "solid-above-blacklist"};

    private static final java.util.Set<Key> DEFAULT_SOLID_ABOVE_WHITELIST = java.util.Set.of(

            Key.of("minecraft:melon_stem"),
            Key.of("minecraft:pumpkin_stem"),
            Key.of("minecraft:wheat"),
            Key.of("minecraft:beetroots"),
            Key.of("minecraft:carrots"),
            Key.of("minecraft:potatoes"),
            Key.of("minecraft:torchflower_crop"),
            Key.of("minecraft:pitcher_crop")
    );

    private static class Factory implements BlockBehaviorFactory<FarmlandBlockBehavior> {
        @Override
        public FarmlandBlockBehavior create(BlockDefinition block, ConfigSection section) {

            java.util.Set<Key> whitelist;
            java.util.List<String> rawWhitelist = section.getStringList(SOLID_ABOVE_WHITELIST);
            if (rawWhitelist != null && !rawWhitelist.isEmpty()) {
                whitelist = new java.util.LinkedHashSet<>();
                for (String entry : rawWhitelist) {
                    whitelist.add(Key.of(entry.trim()));
                }
            } else {
                whitelist = DEFAULT_SOLID_ABOVE_WHITELIST;
            }

            java.util.Set<Key> blacklist = new java.util.LinkedHashSet<>();
            java.util.List<String> rawBlacklist = section.getStringList(SOLID_ABOVE_BLACKLIST);
            if (rawBlacklist != null) {
                for (String entry : rawBlacklist) {
                    blacklist.add(Key.of(entry.trim()));
                }
            }

            return new FarmlandBlockBehavior(
                    block,
                    BlockBehaviorFactory.getProperty(section.path(), block, "moisture", Integer.class),
                    section.getInt(MAX_MOISTURE, 7),
                    section.getInt(WATER_RANGE, 4),
                    section.getBoolean(TRAMPLING, true),
                    section.getString(TURN_TO, "minecraft:dirt"),
                    whitelist,
                    blacklist
            );
        }
    }
}
