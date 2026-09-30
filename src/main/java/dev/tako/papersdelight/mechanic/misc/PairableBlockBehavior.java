package dev.tako.papersdelight.mechanic.misc;

import dev.tako.papersdelight.bridge.NMSHelper;
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
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;

import java.util.Optional;

public final class PairableBlockBehavior extends BukkitBlockBehavior {

    public static final BlockBehaviorFactory<PairableBlockBehavior> FACTORY = new Factory();

    private final Property<Direction> facingProperty;
    private final Property<Boolean> pairedProperty;
    private final boolean disableWhenSneaking;

    private static final int PMS$LEVEL = 0;
    private static final int PMS$POS = 1;
    private static final int PMS$STATE = 2;
    private static final int PMS$PLAYER = 3;

    private static final int PWD$LEVEL = 0;
    private static final int PWD$POS = 1;
    private static final int PWD$STATE = 2;

    private PairableBlockBehavior(BlockDefinition blockDef,
                                  Property<Direction> facing,
                                  Property<Boolean> paired,
                                  boolean disableWhenSneaking) {
        super(blockDef);
        this.facingProperty = facing;
        this.pairedProperty = paired;
        this.disableWhenSneaking = disableWhenSneaking;
    }

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:pairable_block"), FACTORY);
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {

        Direction clickedFace = context.getClickedFace();
        Direction facing = clickedFace.opposite();
        return state.with(this.facingProperty, facing).with(this.pairedProperty, false);
    }

    @Override
    public boolean hasMultiState(ImmutableBlockState baseState) {

        return true;
    }

    @Override
    public boolean canPlaceMultiState(net.momirealms.craftengine.core.world.WorldAccessor accessor,
                                      net.momirealms.craftengine.core.world.BlockPos pos,
                                      ImmutableBlockState state) {

        return true;
    }

    @Override
    public void placeMultiState(Object thisBlock, Object[] args) {
        Object level = args[PMS$LEVEL];
        Object pos = args[PMS$POS];
        Object nmsState = args[PMS$STATE];

        Optional<ImmutableBlockState> opt = BlockStateUtils.getOptionalCustomBlockState(nmsState);
        if (opt.isEmpty() || opt.get().isEmpty()) return;
        ImmutableBlockState customState = opt.get();

        if (this.disableWhenSneaking && isSneaking(args[PMS$PLAYER])) return;

        if (Boolean.TRUE.equals(customState.get(this.pairedProperty))) return;

        Direction facing = customState.get(this.facingProperty);
        Object neighborPos = NMSHelper.offsetPos(pos, facing.stepX(), facing.stepY(), facing.stepZ());

        Object neighborNmsState = getBlockStateNMS(level, neighborPos);
        if (neighborNmsState == null) return;

        Optional<ImmutableBlockState> neighborOpt = BlockStateUtils.getOptionalCustomBlockState(neighborNmsState);
        if (neighborOpt.isEmpty() || neighborOpt.get().isEmpty()) return;
        ImmutableBlockState neighborState = neighborOpt.get();

        if (neighborState.owner().value() != this.blockDefinition) return;

        if (Boolean.TRUE.equals(neighborState.get(this.pairedProperty))) return;

        PairableBlockBehavior neighborBehavior = neighborState.behavior()
                .getFirst(PairableBlockBehavior.class);
        if (neighborBehavior == null) return;

        ImmutableBlockState newNeighborState = neighborState
                .with(this.facingProperty, facing.opposite())
                .with(this.pairedProperty, true);
        setBlockNMS(level, neighborPos, newNeighborState.customBlockState().minecraftState(), 1 | 2);

        ImmutableBlockState newSelfState = customState.with(this.pairedProperty, true);
        setBlockNMS(level, pos, newSelfState.customBlockState().minecraftState(), 1 | 2);
    }

    @Override
    public Object playerWillDestroy(Object thisBlock, Object[] args) {
        Object state = args[PWD$STATE];
        Optional<ImmutableBlockState> opt = BlockStateUtils.getOptionalCustomBlockState(state);
        if (opt.isEmpty() || opt.get().isEmpty()) return state;
        ImmutableBlockState customState = opt.get();

        if (!Boolean.TRUE.equals(customState.get(this.pairedProperty))) return state;

        Direction facing = customState.get(this.facingProperty);
        Object level = args[PWD$LEVEL];
        Object pos = args[PWD$POS];

        Object partnerPos = NMSHelper.offsetPos(pos, facing.stepX(), facing.stepY(), facing.stepZ());

        Object partnerNmsState = getBlockStateNMS(level, partnerPos);
        if (partnerNmsState == null) return state;

        Optional<ImmutableBlockState> partnerOpt = BlockStateUtils.getOptionalCustomBlockState(partnerNmsState);
        if (partnerOpt.isEmpty() || partnerOpt.get().isEmpty()) return state;
        ImmutableBlockState partnerState = partnerOpt.get();

        if (partnerState.owner().value() != this.blockDefinition) return state;
        if (!Boolean.TRUE.equals(partnerState.get(this.pairedProperty))) return state;
        if (partnerState.get(this.facingProperty) != facing.opposite()) return state;

        ImmutableBlockState newPartnerState = partnerState.with(this.pairedProperty, false);
        setBlockNMS(level, partnerPos, newPartnerState.customBlockState().minecraftState(), 1 | 2);

        return state;
    }

    private static Object getBlockStateNMS(Object level, Object pos) {
        return NMSHelper.getBlockState(level, pos);
    }

    private static void setBlockNMS(Object level, Object pos, Object state, int flags) {
        NMSHelper.setBlockState(level, pos, state, flags);
    }

    private static boolean isSneaking(Object nmsPlayer) {
        return NMSHelper.isShiftKeyDown(nmsPlayer);
    }

    private static class Factory implements BlockBehaviorFactory<PairableBlockBehavior> {
        @Override
        @SuppressWarnings("unchecked")
        public PairableBlockBehavior create(BlockDefinition block, ConfigSection section) {
            ConfigSection properties = section.getNonNullSection("properties");
            Property<Direction> facing = (Property<Direction>) BlockBehaviorFactory.getProperty(
                    section.path(), block, properties.getNonNullString("facing"), Direction.class);
            Property<Boolean> paired = (Property<Boolean>) BlockBehaviorFactory.getProperty(
                    section.path(), block, properties.getNonNullString("paired"), Boolean.class);
            boolean disableWhenSneaking = section.getBoolean("disable-when-sneaking", true);
            return new PairableBlockBehavior(block, facing, paired, disableWhenSneaking);
        }
    }
}
