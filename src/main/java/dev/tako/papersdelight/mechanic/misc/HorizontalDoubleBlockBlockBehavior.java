package dev.tako.papersdelight.mechanic.misc;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.WorldAccessor;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;

import java.util.Optional;

@SuppressWarnings("DuplicatedCode")
public final class HorizontalDoubleBlockBlockBehavior extends BukkitBlockBehavior {

    public static final BlockBehaviorFactory<HorizontalDoubleBlockBlockBehavior> FACTORY = new Factory();

    final Property<String> partProperty;
    final Property<Direction> facingProperty;

    private final boolean needsSupport;

    private static final int US$STATE = 0;
    private static final int US$LEVEL = 1;
    private static final int US$POS = 3;
    private static final int US$DIRECTION = 4;
    private static final int US$NEIGHBOR_STATE = 6;

    private static final int PWD$STATE = 2;

    private static Object getBlockStateNMS(Object level, Object pos) {
        return NMSHelper.getBlockState(level, pos);
    }

    private static void setBlockNMS(Object level, Object pos, Object state, int flags) {
        NMSHelper.setBlockState(level, pos, state, flags);
    }

    private HorizontalDoubleBlockBlockBehavior(BlockDefinition blockDef,
                                               Property<String> part,
                                               Property<Direction> facing,
                                               boolean needsSupport) {
        super(blockDef);
        this.partProperty = part;
        this.facingProperty = facing;
        this.needsSupport = needsSupport;
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:horizontal_double_block"), FACTORY);
    }

    @Override
    public boolean hasMultiState(ImmutableBlockState baseState) {

        return "head".equals(baseState.get(this.partProperty));
    }

    @Override
    public boolean canPlaceMultiState(WorldAccessor accessor, BlockPos pos, ImmutableBlockState state) {

        if (!"head".equals(state.get(this.partProperty))) return true;
        Direction facing = state.get(this.facingProperty);
        BlockPos outerPos = pos.relative(facing);
        if (accessor.worldHeight().isOutsideBuildHeight(outerPos)) return false;

        if (!accessor.getBlockState(outerPos).isAir()) return false;

        if (this.needsSupport) {
            BlockPos outerBelow = outerPos.below();
            if (accessor.worldHeight().isOutsideBuildHeight(outerBelow)) return false;
            if (accessor.getBlockState(outerBelow).isAir()) return false;
        }
        return true;
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        Direction facing = context.getHorizontalDirection();
        return state.with(this.facingProperty, facing).with(this.partProperty, "head");
    }

    @Override
    public void placeMultiState(Object thisBlock, Object[] args) {

        Object nmsState = args[2];
        Optional<ImmutableBlockState> opt = BlockStateUtils.getOptionalCustomBlockState(nmsState);
        if (opt.isEmpty() || opt.get().isEmpty()) return;
        ImmutableBlockState customState = opt.get();

        if (!"head".equals(customState.get(this.partProperty))) return;

        Direction facing = customState.get(this.facingProperty);
        Object outerPos = NMSHelper.offsetPos(args[1], facing.stepX(), 0, facing.stepZ());

        ImmutableBlockState outerState = customState.with(this.partProperty, "foot");
        setBlockNMS(args[0], outerPos, outerState.customBlockState().minecraftState(), 1 | 2);
    }

    @Override
    public Object playerWillDestroy(Object thisBlock, Object[] args) {

        return args[PWD$STATE];
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object state = args[US$STATE];
        Optional<ImmutableBlockState> opt = BlockStateUtils.getOptionalCustomBlockState(state);
        if (opt.isEmpty() || opt.get().isEmpty()) return state;
        ImmutableBlockState customState = opt.get();

        Object direction = args[US$DIRECTION];
        String part = customState.get(this.partProperty);
        Direction facing = customState.get(this.facingProperty);

        Direction partnerDir = "head".equals(part) ? facing : facing.opposite();
        if (directionOrdinal(direction) == partnerDir.ordinal()) {
            Object neighborState = args[US$NEIGHBOR_STATE];
            Optional<ImmutableBlockState> neighborOpt = BlockStateUtils.getOptionalCustomBlockState(neighborState);
            if (neighborOpt.isEmpty() || neighborOpt.get().isEmpty()
                    || neighborOpt.get().owner().value() != this.blockDefinition
                    || part.equals(neighborOpt.get().get(this.partProperty))) {
                return airState();
            }
            return state;
        }

        if (this.needsSupport && directionOrdinal(direction) == Direction.DOWN.ordinal()) {
            Object blockPos = args[US$POS];
            Object belowState = getBlockStateNMS(args[US$LEVEL], LocationUtils.below(blockPos));
            if (isAir(belowState)) {
                return airState();
            }
        }

        return state;
    }

    @Override
    public boolean canSurvive(Object thisBlock, Object[] args) {
        if (!this.needsSupport) return true;

        Optional<ImmutableBlockState> opt = BlockStateUtils.getOptionalCustomBlockState(args[0]);
        if (opt.isEmpty() || opt.get().isEmpty()) return false;

        Object belowPos = LocationUtils.below(args[2]);
        Object belowState = getBlockStateNMS(args[1], belowPos);
        return !isAir(belowState);
    }

    private static Object airState() {
        return NMSHelper.airStateObj();
    }

    private static boolean isAir(Object nmsState) {
        return nmsState == null || BlockStateUtils.toBlockStateWrapper(nmsState).isAir();
    }

    private static int directionOrdinal(Object nmsDirection) {
        return ((Enum<?>) nmsDirection).ordinal();
    }

    private static class Factory implements BlockBehaviorFactory<HorizontalDoubleBlockBlockBehavior> {
        @Override
        @SuppressWarnings("unchecked")
        public HorizontalDoubleBlockBlockBehavior create(BlockDefinition block, ConfigSection section) {
            Property<String> part = (Property<String>) BlockBehaviorFactory.getProperty(
                    section.path(), block, section.getNonNullString("part"), String.class);
            Property<Direction> facing = (Property<Direction>) BlockBehaviorFactory.getProperty(
                    section.path(), block, section.getNonNullString("facing"), Direction.class);
            boolean needsSupport = section.getBoolean("needs_support", true);
            return new HorizontalDoubleBlockBlockBehavior(block, part, facing, needsSupport);
        }
    }
}
