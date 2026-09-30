package dev.tako.papersdelight.cookingpot;

import dev.tako.papersdelight.util.WorldLookup;
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

public final class CookingPotBlockBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<CookingPotBlockBehavior> FACTORY = new Factory();

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:cooking_pot"), FACTORY);
    }

    private int controllerId;

    public CookingPotBlockBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new CookingPotBlockEntityController(blockEntity);
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

        CookingPotManager mgr = CookingPotManager.instance;
        if (mgr == null) return InteractionResult.FAIL;

        return mgr.handlePotInteract(player, block);
    }

    @Override
    public InteractionResult useWithoutItem(UseOnContext context, ImmutableBlockState state) {
        return InteractionResult.PASS;
    }

    public static boolean isCookingPot(Block block) {
        CookingPotManager mgr = CookingPotManager.instance;
        return mgr != null && mgr.getController(block) != null;
    }

    @Override
    public void onPlace(Object thisBlock, Object[] args) {
        World world = WorldLookup.worldOf(args[0]);
        if (world == null) return;
        BlockPos cePos = LocationUtils.fromBlockPos(args[1]);
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());

        CookingPotManager mgr = CookingPotManager.instance;
        if (mgr == null) return;

        mgr.trackedLocations.add(CookingPotManager.blockKey(block));
    }

    @Override
    public boolean hasAnalogOutputSignal(Object thisBlock, Object[] args) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(Object thisBlock, Object[] args) {
        Object world = args[1];
        Object blockPos = args[2];
        BlockPos pos = LocationUtils.fromBlockPos(blockPos);
        org.bukkit.World bukkitWorld = WorldLookup.worldOf(world);
        if (bukkitWorld == null) return 0;
        CEWorld ceWorld = CraftEngineUtil.getLoadedWorld(bukkitWorld);
        if (ceWorld == null) return 0;
        BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(pos);
        if (blockEntity == null) return 0;
        return blockEntity.controller.let(CookingPotBlockEntityController.class, this.controllerId, c -> {
            ItemStack meal = c.waitingOutput();
            if (meal.getType().isAir()) return 0;
            return meal.getAmount() > 0 ? 15 : 0;
        });
    }

    private static class Factory implements BlockBehaviorFactory<CookingPotBlockBehavior> {
        @Override
        public CookingPotBlockBehavior create(BlockDefinition block, ConfigSection section) {
            return new CookingPotBlockBehavior(block);
        }
    }
}
