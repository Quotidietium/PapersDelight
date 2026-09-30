package dev.tako.papersdelight.jug;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.behavior.EntityBlock;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class JugBlockBehavior extends BukkitBlockBehavior implements EntityBlock {

    public static final String BEHAVIOR_ID = "papersdelight:jug";
    public static final BlockBehaviorFactory<JugBlockBehavior> FACTORY = new Factory();

    @SuppressWarnings("unused")
    private int controllerId;

    public static void register() {
        BlockBehaviors.register(Key.of(BEHAVIOR_ID), FACTORY);
    }

    public JugBlockBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return JugGate.createController(blockEntity);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player cePlayer = context.getPlayer();
        if (cePlayer == null || !(cePlayer.platformPlayer() instanceof org.bukkit.entity.Player player)) {
            return InteractionResult.PASS;
        }
        if (!JugGate.available()) {

            JugUnavailableNotice.notifyUnavailable(player);
            return InteractionResult.SUCCESS_AND_CANCEL;
        }
        Object platformWorld = context.getLevel().platformWorld();
        if (!(platformWorld instanceof World world)) return InteractionResult.PASS;
        BlockPos position = context.getClickedPos();
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        return JugGate.interact(player, block);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        return useOnBlock(context, state);
    }

    @Override
    public void onPlace(Object thisBlock, Object[] args) {
        JugGate.onPlace(JugGate.blockFromPlaceArgs(args));
    }

    @Override
    public boolean hasAnalogOutputSignal(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(Object thisBlock, Object[] args) {
        return JugGate.analogSignal(JugGate.blockFromSignalArgs(args));
    }

    private static final class Factory implements BlockBehaviorFactory<JugBlockBehavior> {
        @Override
        public JugBlockBehavior create(BlockDefinition block, ConfigSection section) {
            return new JugBlockBehavior(block);
        }
    }
}
