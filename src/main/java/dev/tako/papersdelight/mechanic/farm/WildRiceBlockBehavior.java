package dev.tako.papersdelight.mechanic.farm;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.proxy.minecraft.world.level.LevelAccessorProxy;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LevelUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.util.DirectionUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.block.property.type.DoubleBlockHalf;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.LazyReference;
import net.momirealms.craftengine.core.world.*;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import org.bukkit.Bukkit;
import java.util.*;

public final class WildRiceBlockBehavior extends BukkitBlockBehavior {

    public static final BlockBehaviorFactory<WildRiceBlockBehavior> FACTORY = new Factory();

    public final Property<DoubleBlockHalf> halfProperty;
    public final List<Key> bottomTags;
    public final LazyReference<Set<Object>> bottomBlockStates;
    private final boolean blacklistMode;
    private final boolean stackable;
    private final int maxHeight;

    private WildRiceBlockBehavior(
            BlockDefinition blockDefinition,
            Property<DoubleBlockHalf> halfProperty,
            boolean blacklist,
            boolean stackable,
            int maxHeight,
            List<Key> bottomTags,
            LazyReference<Set<Object>> bottomBlockStates
    ) {
        super(blockDefinition);
        this.halfProperty = halfProperty;
        this.blacklistMode = blacklist;
        this.stackable = stackable;
        this.maxHeight = maxHeight;
        this.bottomTags = List.copyOf(bottomTags);
        this.bottomBlockStates = bottomBlockStates;
    }

    private static boolean isFullWaterSource(Object nmsLevel, Object nmsPos) {
        return NMSHelper.isFullWaterSource(nmsLevel, nmsPos);
    }

    private static Object getBlockStateNMS(Object level, Object pos) {
        return NMSHelper.getBlockState(level, pos);
    }

    private static void setBlockNMS(Object level, Object pos, Object state) {
        NMSHelper.setBlockState(level, pos, state, 3);
    }

    private static boolean isInTag(Object nmsState, Key tag) {
        return nmsState != null && tag != null && BlockStateUtils.isTag(nmsState, tag);
    }

    private static Object airState() {
        return NMSHelper.airStateObj();
    }

    private static Object waterState() {
        return NMSHelper.defaultStateFromId("minecraft:water");
    }

    private static org.bukkit.entity.Player getBukkitPlayer(Object nmsPlayer) {
        return NMSHelper.getBukkitPlayer(nmsPlayer);
    }

    private static World getWorldFromLevel(Object nmsLevel) {
        org.bukkit.World bukkitWorld = NMSHelper.bukkitWorldOf(nmsLevel);
        return bukkitWorld == null ? null : BukkitAdaptor.adapt(bukkitWorld);
    }

    private static void levelEvent(Object nmsLevel, Object blockPos, int eventId) {
        NMSHelper.levelEventObj(nmsLevel, blockPos, eventId, 0);
    }

    private static boolean isYAxis(Object nmsDirection) {
        Direction dir = DirectionUtils.fromNMSDirection(nmsDirection);
        return dir.axis() == Direction.Axis.Y;
    }

    private static boolean isUp(Object nmsDirection) {
        return DirectionUtils.fromNMSDirection(nmsDirection) == Direction.UP;
    }

    private static boolean isDown(Object nmsDirection) {
        return DirectionUtils.fromNMSDirection(nmsDirection) == Direction.DOWN;
    }

    private static Object nmsDown() {
        return DirectionUtils.toNMSDirection(Direction.DOWN);
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

    private static final int PMS$LEVEL = 0;
    private static final int PMS$POS = 1;
    private static final int PMS$STATE = 2;

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        World world = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (pos.y() < world.worldHeight().getMaxBuildHeight() - 1
                && context.isWaterSource()
                && world.getBlockState(pos.above()).isAir()) {
            return state.with(this.halfProperty, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public boolean canPlaceMultiState(WorldAccessor accessor, BlockPos pos, ImmutableBlockState state) {
        if (pos.y() >= accessor.worldHeight().getMaxBuildHeight() - 1) return false;
        return accessor.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean hasMultiState(ImmutableBlockState baseState) {
        return baseState.get(this.halfProperty) == DoubleBlockHalf.LOWER;
    }

    @Override
    public void placeMultiState(Object thisBlock, Object[] args) {
        ImmutableBlockState customState = BlockStateUtils
                .getOptionalCustomBlockState(args[PMS$STATE]).orElse(null);
        if (customState == null) return;

        Object level = args[PMS$LEVEL];
        Object pos = args[PMS$POS];
        Object upperPos = LocationUtils.above(pos);
        Object upperMinecraftState = customState.with(halfProperty, DoubleBlockHalf.UPPER)
                .customBlockState().minecraftState();
        setBlockNMS(level, upperPos, upperMinecraftState);
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        Object state = args[0];
        Object level = args[1];
        Object blockPos = args[2];
        return canSurviveNMS(thisBlock, state, level, blockPos);
    }

    private boolean canSurviveNMS(Object thisBlock, Object state, Object level, Object blockPos) {
        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(state).orElse(null);
        if (customState == null || customState.isEmpty()) return false;

        DoubleBlockHalf half = customState.get(this.halfProperty);

        if (half == DoubleBlockHalf.UPPER) {
            Object belowPos = LocationUtils.below(blockPos);
            Object belowState = getBlockStateNMS(level, belowPos);
            Optional<ImmutableBlockState> belowCustom = BlockStateUtils.getOptionalCustomBlockState(belowState);
            return belowCustom
                    .filter(ibs -> ibs.owner().value() == super.blockDefinition)
                    .isPresent();
        }

        BlockPos cePos = LocationUtils.fromBlockPos(blockPos);
        Object belowPos = LocationUtils.toBlockPos(cePos.x(), cePos.y() - 1, cePos.z());
        Object belowState = getBlockStateNMS(level, belowPos);
        return isFullWaterSource(level, blockPos) && mayPlaceOn(belowState, level, belowPos);
    }

    private boolean mayPlaceOn(Object belowState, Object world, Object belowPos) {
        for (Key tag : this.bottomTags) {
            if (isInTag(belowState, tag)) {
                return !this.blacklistMode;
            }
        }
        Optional<ImmutableBlockState> optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(belowState);
        if (optionalCustomState.isEmpty()) {
            if (this.bottomBlockStates.get().contains(belowState)) {
                return !this.blacklistMode;
            }
        } else {
            ImmutableBlockState belowCustomState = optionalCustomState.get();
            if (belowCustomState.owner().value() == super.blockDefinition) {
                if (!this.stackable || this.maxHeight == 1) return false;
                if (this.maxHeight > 1) {
                    return mayStackOn(world, belowPos);
                }
                return true;
            }
            if (this.bottomBlockStates.get().contains(belowState)) {
                return !this.blacklistMode;
            }
        }
        return this.blacklistMode;
    }

    private boolean mayStackOn(Object world, Object belowPos) {
        int count = 1;
        Object cursorPos = LocationUtils.below(belowPos);
        while (count < this.maxHeight) {
            Object belowState = getBlockStateNMS(world, cursorPos);
            Optional<ImmutableBlockState> belowCustomState = BlockStateUtils.getOptionalCustomBlockState(belowState);
            if (belowCustomState.isPresent()
                    && belowCustomState.get().owner().value() == super.blockDefinition) {
                count++;
                cursorPos = LocationUtils.below(cursorPos);
            } else {
                break;
            }
        }
        return count < this.maxHeight;
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

        if (isYAxis(direction) && (half == DoubleBlockHalf.LOWER) == isUp(direction)) {
            ImmutableBlockState neighborState = BlockStateUtils
                    .getOptionalCustomBlockState(args[US$NEIGHBOR_STATE]).orElse(null);
            if (neighborState == null || neighborState.isEmpty()) {
                return airState();
            }
            WildRiceBlockBehavior neighborBehavior = neighborState.behavior()
                    .getFirst(WildRiceBlockBehavior.class);
            if (neighborBehavior == null) return airState();
            if (neighborState.get(neighborBehavior.halfProperty) != half) {
                return neighborState.with(neighborBehavior.halfProperty, half)
                        .customBlockState().minecraftState();
            }
            return airState();
        } else if (half == DoubleBlockHalf.LOWER && isDown(direction)
                && !canSurviveNMS(thisBlock, blockState, level, blockPos)) {
            BlockPos pos = LocationUtils.fromBlockPos(blockPos);
            World world = getWorldFromLevel(level);
            if (world == null) return airState();
            WorldPosition position = new WorldPosition(world, Vec3d.atCenterOf(pos));
            world.playBlockSound(position, customState.settings().sounds().breakSound());
            levelEvent(level, blockPos, customState.customBlockState().registryId());
            return airState();
        }
        return blockState;
    }

    @Override
    public void onPlace(Object thisBlock, Object[] args) {
        LevelAccessorProxy.INSTANCE.scheduleTick$0(args[1], args[2], thisBlock, 2);
    }

    @Override
    public void tick(Object thisBlock, Object[] args) {
        Object blockState = args[0];
        Object level = args[1];
        Object blockPos = args[2];
        if (!canSurviveNMS(thisBlock, blockState, level, blockPos)) {
            BlockStateUtils.getOptionalCustomBlockState(blockState).ifPresent(customState -> {
                if (!customState.isEmpty() && customState.owner().value() == this.blockDefinition) {
                    BlockPos pos = LocationUtils.fromBlockPos(blockPos);
                    World world = getWorldFromLevel(level);
                    if (world == null) return;
                    WorldPosition position = new WorldPosition(world, Vec3d.atCenterOf(pos));
                    world.playBlockSound(position, customState.settings().sounds().breakSound());
                    setBlockNMS(level, blockPos, airState());
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

        org.bukkit.entity.Player bukkitPlayer = getBukkitPlayer(player);
        if (bukkitPlayer == null) return state;

        BukkitServerPlayer cePlayer = BukkitAdaptor.adapt(bukkitPlayer);
        if (cePlayer == null) return state;

        Item item = cePlayer.getItemInHand(InteractionHand.MAIN_HAND);
        if (cePlayer.canInstabuild() || !BlockStateUtils.isCorrectTool(blockState, item)) {
            preventDropFromBottomPart(level, pos, blockState, player);
        }
        return state;
    }

    private void preventDropFromBottomPart(Object level, Object pos,
                                           ImmutableBlockState state, Object player) {
        if (state.get(this.halfProperty) != DoubleBlockHalf.UPPER) return;

        Object belowPos = LocationUtils.below(pos);
        Object belowState = getBlockStateNMS(level, belowPos);
        ImmutableBlockState belowCustomState = BlockStateUtils.getOptionalCustomBlockState(belowState).orElse(null);
        if (belowCustomState == null || belowCustomState.isEmpty()) return;

        WildRiceBlockBehavior belowBehavior = belowCustomState.behavior()
                .getFirst(WildRiceBlockBehavior.class);
        if (belowBehavior == null
                || belowCustomState.get(belowBehavior.halfProperty) != DoubleBlockHalf.LOWER) return;

        setBlockNMS(level, belowPos, waterState());
        LevelUtils.levelEvent(level, player, WorldEvents.BLOCK_BREAK_EFFECT, belowPos,
                belowCustomState.customBlockState().registryId());
    }

    @Override
    public void affectNeighborsAfterRemoval(Object thisBlock, Object[] args) {
        Object state = args[0];
        Object level = args[1];
        Object pos = args[2];

        ImmutableBlockState customState = BlockStateUtils.getOptionalCustomBlockState(state).orElse(null);
        if (customState == null || customState.isEmpty()) return;

        DoubleBlockHalf half = customState.get(this.halfProperty);
        if (half == DoubleBlockHalf.LOWER) {
            setBlockNMS(level, pos, waterState());
        }
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:wild_rice"), FACTORY);
    }

    private static class Factory implements BlockBehaviorFactory<WildRiceBlockBehavior> {
        private static final String[] MAX_HEIGHT = {"max_height", "max-height"};

        @Override
        public WildRiceBlockBehavior create(BlockDefinition block, ConfigSection section) {
            TagsAndState tagsAndState = readTagsAndState(section, "bottom");
            return new WildRiceBlockBehavior(
                    block,
                    BlockBehaviorFactory.getProperty(section.path(), block, "half", DoubleBlockHalf.class),
                    section.getBoolean("blacklist"),
                    section.getBoolean("stackable"),
                    section.getInt(MAX_HEIGHT),
                    tagsAndState.tags(),
                    tagsAndState.blockStates()
            );
        }
    }

    private static TagsAndState readTagsAndState(ConfigSection section, String prefix) {

        List<Key> mcTags = section.getList(
                new String[]{prefix + "_block_tags", prefix.replace("_", "-") + "-block-tags"},
                v -> Key.of(v.getAsString()));

        Set<Object> blockStates = new HashSet<>();
        List<Key> customBlocks = new ArrayList<>();
        List<String> customStates = new ArrayList<>();

        for (String blockState : section.getStringList(
                new String[]{prefix + "_blocks", prefix.replace("_", "-") + "-blocks"})) {
            int index = blockState.indexOf('[');
            Key blockType = index != -1
                    ? Key.of(blockState.substring(0, index))
                    : Key.of(blockState);

            try {
                if (index == -1) {
                    var states = BlockStateUtils.getPossibleBlockStates(blockType);
                    if (states != null && !states.isEmpty()) {
                        blockStates.addAll(states);
                        continue;
                    }
                } else {
                    org.bukkit.block.data.BlockData data = Bukkit.createBlockData(blockState);
                    if (data != null) {
                        blockStates.add(BlockStateUtils.blockDataToBlockState(data));
                        continue;
                    }
                }
            } catch (Exception ignored) {}

            if (index == -1) {
                customBlocks.add(Key.of(blockState));
            } else {
                customStates.add(blockState);
            }
        }

        LazyReference<Set<Object>> lazyBlockStates = LazyReference.oneTime(() -> {
            for (Key customBlock : customBlocks) {
                net.momirealms.craftengine.bukkit.block.BukkitBlockManager.instance()
                        .blockById(customBlock).ifPresent(block -> {
                            for (ImmutableBlockState state : block.variantProvider().states()) {
                                blockStates.add(state.customBlockState().minecraftState());
                            }
                        });
            }
            for (String customState : customStates) {
                java.util.Optional.ofNullable(
                        net.momirealms.craftengine.core.block.parser.BlockStateParser
                                .deserialize(customState)).ifPresent(bs -> {
                    blockStates.add(bs.customBlockState().minecraftState());
                });
            }
            return blockStates;
        });

        return new TagsAndState(mcTags, lazyBlockStates);
    }

    public record TagsAndState(List<Key> tags, LazyReference<Set<Object>> blockStates) {}
}
