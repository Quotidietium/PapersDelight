package dev.tako.papersdelight.mechanic.stove;

import dev.tako.papersdelight.bridge.NMSHelper;
import dev.tako.papersdelight.api.protection.ProtectionGate;
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
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class StoveBlockBehavior extends BukkitBlockBehavior implements EntityBlock {
    public static final BlockBehaviorFactory<StoveBlockBehavior> FACTORY = new Factory();

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:stove"), FACTORY);
    }

    private int controllerId;

    public StoveBlockBehavior(BlockDefinition blockDefinition) {
        super(blockDefinition);
    }

    @Override
    public BlockEntityController createBlockEntityController(BlockEntity blockEntity) {
        return new StoveBlockEntityController(blockEntity);
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
        BlockPos cePos = context.getClickedPos();
        World world = (World) context.getLevel().platformWorld();
        Block block = world.getBlockAt(cePos.x(), cePos.y(), cePos.z());
        ItemStack held = player.getInventory().getItemInMainHand();

        StoveManager mgr = StoveManager.instance;
        if (mgr == null) return InteractionResult.FAIL;

        if (!ProtectionGate.canInteract(player, block.getLocation())) {
            return InteractionResult.FAIL;
        }

        if (held == null || held.getType() == Material.AIR) return InteractionResult.PASS;

        if (!StoveBlockEntityController.isCampfireIngredient(held)) return InteractionResult.PASS;

        Block aboveBlock = block.getRelative(0, 1, 0);
        Material aboveMat = aboveBlock.getType();
        if (aboveMat != Material.AIR && aboveMat != Material.CAVE_AIR && aboveMat != Material.VOID_AIR)
            return InteractionResult.PASS;

        CEWorld ceWorld = CraftEngineUtil.getLoadedWorld(world);
        if (ceWorld == null) return InteractionResult.FAIL;
        BlockEntity blockEntity = ceWorld.getBlockEntityAtIfLoaded(cePos);
        if (blockEntity == null) return InteractionResult.FAIL;

        return blockEntity.controller.let(StoveBlockEntityController.class, this.controllerId, c -> {
            int slot = c.getNextEmptySlot();
            if (slot < 0) return InteractionResult.PASS;

            c.placeItem(held);
            if (player.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
            mgr.spawnDisplayEntity(block, slot, c.getSlotItem(slot));
            mgr.knownStoves.add(block.getLocation().toBlockLocation());
            player.swingMainHand();
            mgr.playSound(block, mgr.soundPlaceFood());
            return InteractionResult.SUCCESS_AND_CANCEL;
        });
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

        StoveManager mgr = StoveManager.instance;
        if (mgr == null) return;

        mgr.knownStoves.add(block.getLocation().toBlockLocation());
    }

    public static boolean isLit(Block block) {
        String lit = CraftEngineUtil.getCustomBlockProperty(block, "lit");
        return "true".equalsIgnoreCase(lit);
    }

    public static String getFacing(Block block) {
        String facing = CraftEngineUtil.getCustomBlockProperty(block, "facing");
        return (facing != null && !facing.isEmpty()) ? facing.toLowerCase() : "north";
    }

    private static class Factory implements BlockBehaviorFactory<StoveBlockBehavior> {
        private static final String[] SOUND = {"sound"};
        private static final String[] INTERVAL = {"interval"};
        private static final String[] VOLUME = {"volume"};
        private static final String[] PITCH = {"pitch"};

        @Override
        public StoveBlockBehavior create(BlockDefinition block, ConfigSection section) {
            String soundKey = section.getString(SOUND, "farmersdelight:block.stove.crackle");
            FRange interval = parseRange(section, INTERVAL, 80);
            if (interval.lo == 80 && interval.hi == 80) { interval = new FRange(60, 100); }
            FRange volume = parseRange(section, VOLUME, 1.0f);
            FRange pitch  = parseRange(section, PITCH, 1.0f);
            if (pitch.lo == 1.0f && pitch.hi == 1.0f) { pitch = new FRange(0.9f, 1.1f); }
            StoveManager.setAmbientSoundConfig(soundKey,
                    interval.lo, interval.hi,
                    volume.lo, volume.hi,
                    pitch.lo, pitch.hi);
            return new StoveBlockBehavior(block);
        }

        private static FRange parseRange(ConfigSection sec, String[] keys, float fallback) {
            try {
                float v = sec.getFloat(keys, fallback);
                return new FRange(v, v);
            } catch (Exception ignored) {}
            try {
                List<?> list = sec.getList(keys);
                if (list != null && list.size() == 2) {
                    float a = Float.parseFloat(String.valueOf(list.get(0)));
                    float b = Float.parseFloat(String.valueOf(list.get(1)));
                    return new FRange(Math.min(a, b), Math.max(a, b));
                }
            } catch (Exception ignored) {}
            return new FRange(fallback, fallback);
        }

        private record FRange(float lo, float hi) {}
    }
}
