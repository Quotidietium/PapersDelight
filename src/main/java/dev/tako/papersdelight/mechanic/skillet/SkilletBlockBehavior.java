package dev.tako.papersdelight.mechanic.skillet;

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
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class SkilletBlockBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<SkilletBlockBehavior> FACTORY = new Factory();

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:skillet"), FACTORY);
    }

    private int controllerId;

    public SkilletBlockBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new SkilletBlockEntityController(blockEntity);
    }

    @Override
    public void initControllerId(int id) {
        this.controllerId = id;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player cePlayer = context.getPlayer();
        if (cePlayer == null) return InteractionResult.FAIL;

        World world = (World) context.getLevel().platformWorld();
        BlockPos cePos = context.getClickedPos();
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());

        SkilletManager mgr = SkilletManager.instance;
        if (mgr == null) return InteractionResult.FAIL;

        return mgr.handleSkilletInteract(cePlayer, block, context.getHand());
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        return InteractionResult.PASS;
    }

    @Override
    public void onPlace(Object thisBlock, Object[] args) {
        World world = NMSHelper.bukkitWorldOf(args[1]);
        if (world == null) return;
        BlockPos cePos = LocationUtils.fromBlockPos(args[2]);
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());

        SkilletManager mgr = SkilletManager.instance;
        if (mgr == null) return;

        mgr.knownSkillets.add(block.getLocation().toBlockLocation());
        mgr.updateAutomaticSupport(block);
    }

    @Override
    public void neighborChanged(Object thisBlock, Object[] args) {
        World world = NMSHelper.bukkitWorldOf(args[1]);
        if (world == null) return;

        BlockPos cePos = LocationUtils.fromBlockPos(args[2]);
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());
        SkilletManager mgr = SkilletManager.instance;
        if (mgr != null) mgr.updateAutomaticSupport(block);
    }

    public static boolean isWaterlogged(Block block) {
        String w = CraftEngineUtil.getCustomBlockProperty(block, "waterlogged");
        return "true".equalsIgnoreCase(w);
    }

    public static String getFacing(Block block) {
        String facing = CraftEngineUtil.getCustomBlockProperty(block, "facing");
        return (facing != null && !facing.isEmpty()) ? facing.toLowerCase() : "north";
    }

    private static class Factory implements BlockBehaviorFactory<SkilletBlockBehavior> {
        @Override
        public SkilletBlockBehavior create(BlockDefinition block, ConfigSection section) {
            return new SkilletBlockBehavior(block);
        }
    }
}
