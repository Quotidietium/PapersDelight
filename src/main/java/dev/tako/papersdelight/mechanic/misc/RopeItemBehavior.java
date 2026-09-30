package dev.tako.papersdelight.mechanic.misc;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.bukkit.item.behavior.BlockItemBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockHitResult;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;

import java.nio.file.Path;
import java.util.Map;

public final class RopeItemBehavior extends BlockItemBehavior {
    public static final ItemBehaviorFactory<RopeItemBehavior> FACTORY = new Factory();

    private RopeItemBehavior(Key blockId) {
        super(blockId);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context) {

        BlockPos clickedPos = context.getClickedPos();
        Key blockId = this.block();
        if (!isRopeBlock(context, clickedPos, blockId)) {
            return super.useOnBlock(context);
        }

        Player player = context.getPlayer();
        Direction dir;
        if (player != null && player.isSneaking()) {
            dir = context.getClickedFace();
        } else {
            dir = Direction.DOWN;
        }

        BlockPos searchPos = clickedPos.relative(dir);
        int minY = context.getLevel().worldHeight().getMinBuildHeight();
        int maxY = context.getLevel().worldHeight().getMaxBuildHeight() - 1;

        while (searchPos.y() >= minY && searchPos.y() <= maxY) {
            Key searchOwner = getBlockOwner(context, searchPos);

            if (!blockId.equals(searchOwner)) {

                if (isPlaceable(context, searchPos)) {
                    return placeAt(context, searchPos, dir);
                }
                break;
            }

            if (dir != Direction.DOWN) {
                return InteractionResult.PASS;
            }

            searchPos = searchPos.relative(dir);
        }

        return InteractionResult.PASS;
    }

    private boolean isRopeBlock(UseOnContext context, BlockPos cePos, Key blockId) {
        Key owner = getBlockOwner(context, cePos);
        return blockId.equals(owner);
    }

    private static Key getBlockOwner(UseOnContext context, BlockPos cePos) {
        try {
            Object nmsLevel = context.getLevel().minecraftWorld();
            Object nmsPos = LocationUtils.toBlockPos(cePos.x(), cePos.y(), cePos.z());
            Object nmsState = NMSHelper.getBlockState(nmsLevel, nmsPos);

            var custom = BlockStateUtils.getOptionalCustomBlockState(nmsState);
            if (custom.isPresent() && !custom.get().isEmpty()) {
                return custom.get().owner().keyOptional()
                        .map(h -> h.location())
                        .orElseGet(() -> BlockStateUtils.getBlockOwnerIdFromState(nmsState));
            }

            return BlockStateUtils.getBlockOwnerIdFromState(nmsState);
        } catch (Exception e) {
            return Key.of("minecraft:air");
        }
    }

    private static boolean isPlaceable(UseOnContext context, BlockPos cePos) {
        try {
            Object nmsLevel = context.getLevel().minecraftWorld();
            Object nmsPos = LocationUtils.toBlockPos(cePos);
            Object nmsState = NMSHelper.getBlockState(nmsLevel, nmsPos);
            Key owner = BlockStateUtils.getBlockOwnerIdFromState(nmsState);

            if (owner.equals(Key.of("minecraft:air")) || owner.equals(Key.of("minecraft:water"))) {
                return true;
            }

            return BlockStateUtils.isReplaceable(nmsState);
        } catch (Exception e) {
            return false;
        }
    }

    private InteractionResult placeAt(UseOnContext context, BlockPos targetPos, Direction dir) {

        Direction hitFace = dir;
        BlockPos againstPos = targetPos.relative(dir.opposite());
        Vec3d hitPos = new Vec3d(
                againstPos.x() + 0.5 + hitFace.stepX() * 0.5,
                againstPos.y() + 0.5 + hitFace.stepY() * 0.5,
                againstPos.z() + 0.5 + hitFace.stepZ() * 0.5
        );
        BlockHitResult newHit = new BlockHitResult(hitPos, hitFace, againstPos, false);
        BlockPlaceContext newContext = new BlockPlaceContext(
                context.getLevel(), context.getPlayer(), context.getHand(), context.getItem(), newHit
        );
        return this.place(newContext);
    }

    public static void register() {
        ItemBehaviors.register(Key.of("papersdelight:rope"), FACTORY);
    }

    private static class Factory implements ItemBehaviorFactory<RopeItemBehavior> {
        @Override
        public RopeItemBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            ConfigValue blockValue = section.getNonNullValue("block", ConfigConstants.ARGUMENT_SECTION);
            if (blockValue.is(Map.class)) {
                BukkitBlockManager.instance().blockParser()
                        .addPendingConfigSection(new PendingConfigSection(pack, path, key, blockValue.getAsSection()));
                return new RopeItemBehavior(key);
            } else {
                return new RopeItemBehavior(blockValue.getAsIdentifier());
            }
        }
    }
}
