package dev.tako.papersdelight.mechanic.cutting;

import dev.tako.papersdelight.bridge.NMSHelper;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
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
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

public final class CuttingBoardBlockBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<CuttingBoardBlockBehavior> FACTORY = new Factory();

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:cutting_board"), FACTORY);
    }

    private final boolean hasComparator;
    private int controllerId;

    public CuttingBoardBlockBehavior(BlockDefinition blockDefinition, boolean hasComparator) {
        super(blockDefinition);
        this.hasComparator = hasComparator;
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new CuttingBoardBlockEntityController(blockEntity);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player cePlayer = context.getPlayer();
        if (cePlayer == null) return InteractionResult.FAIL;

        org.bukkit.entity.Player player = (org.bukkit.entity.Player) cePlayer.platformPlayer();
        World world = (World) context.getLevel().platformWorld();
        BlockPos cePos = context.getClickedPos();
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());

        CuttingBoardManager mgr = CuttingBoardManager.instance;
        if (mgr == null) return InteractionResult.FAIL;

        return mgr.handleBoardInteract(player, block);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        return InteractionResult.PASS;
    }

    @Override
    public void onPlace(Object thisBlock, Object[] args) {
        World world = NMSHelper.bukkitWorldOf(args[0]);
        if (world == null) return;
        BlockPos cePos = LocationUtils.fromBlockPos(args[1]);
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());

        CuttingBoardManager mgr = CuttingBoardManager.instance;
        if (mgr != null) mgr.markPlaced(block.getLocation());
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        Object level = args[updateShape$level];
        Object blockPos = args[updateShape$blockPos];
        BlockPos cePos = LocationUtils.fromBlockPos(blockPos);

        World world = NMSHelper.bukkitWorldOf(level);
        if (world != null) {
            Block below = world.getBlockAt(cePos.x(), cePos.y() - 1, cePos.z());
            if (below.isEmpty() || below.isLiquid()) {
                CuttingBoardManager mgr = CuttingBoardManager.instance;
                if (mgr != null) {
                    mgr.preDestroyCleanup(world.getBlockAt(cePos.x(), cePos.y(), cePos.z()).getLocation());
                }
            }
        }

        return super.updateShape(thisBlock, args);
    }

    @Override
    public boolean hasAnalogOutputSignal(Object thisBlock, Object[] args) {
        return this.hasComparator;
    }

    @Override
    public int getAnalogOutputSignal(Object thisBlock, Object[] args) {
        if (!this.hasComparator) return 0;
        Object world = args[1];
        Object blockPos = args[2];
        BlockPos pos = LocationUtils.fromBlockPos(blockPos);
        org.bukkit.World bukkitWorld = NMSHelper.bukkitWorldOf(world);
        if (bukkitWorld == null) return 0;
        CEWorld ceWorld = CraftEngineUtil.getLoadedWorld(bukkitWorld);
        if (ceWorld == null) return 0;
        BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) return 0;
        return blockEntity.controller.let(CuttingBoardBlockEntityController.class, this.controllerId, c -> {
            if (!c.hasItem()) return 0;
            ItemStack item = c.item();
            float proportion = (float) item.getAmount() / Math.min(64, item.getMaxStackSize());
            return (int) Math.floor(proportion * 14.0F) + 1;
        });
    }

    private static class Factory implements BlockBehaviorFactory<CuttingBoardBlockBehavior> {
        private static final String[] HAS_COMPARATOR = {"has_comparator", "has-comparator"};

        @Override
        public CuttingBoardBlockBehavior create(BlockDefinition block, ConfigSection section) {
            boolean hasComparator = section.getBoolean(HAS_COMPARATOR, true);
            return new CuttingBoardBlockBehavior(block, hasComparator);
        }
    }
}
