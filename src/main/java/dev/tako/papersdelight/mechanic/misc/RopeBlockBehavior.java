package dev.tako.papersdelight.mechanic.misc;

import dev.tako.papersdelight.bridge.NMSHelper;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.bukkit.util.*;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.BlockKeys;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;
import net.momirealms.craftengine.core.world.context.UseOnContext;

import java.util.Optional;

public class RopeBlockBehavior extends BukkitBlockBehavior {

    private final Property<Boolean> tiedToBellProperty;
    private final int bellSearchRange;
    private final Key reelItemKey;

    private static final String[] TIED_TO_BELL_PROPERTY = {"tied_to_bell_property", "tied-to-bell-property"};
    private static final String[] BELL_SEARCH_RANGE = {"bell_search_range", "bell-search-range"};
    private static final String[] REEL_ITEM = {"reel_item", "reel-item"};

    private RopeBlockBehavior(
            BlockDefinition blockDefinition,
            Property<Boolean> tiedToBellProperty,
            int bellSearchRange,
            Key reelItemKey
    ) {
        super(blockDefinition);
        this.tiedToBellProperty = tiedToBellProperty;
        this.bellSearchRange = bellSearchRange;
        this.reelItemKey = reelItemKey;
    }

    @Override
    public ImmutableBlockState updateStateForPlacement(BlockPlaceContext context, ImmutableBlockState state) {
        if (this.tiedToBellProperty != null) {
            boolean hasBell = checkBellAbove(context);
            state = state.with(this.tiedToBellProperty, hasBell);
        }
        return state;
    }

    private boolean checkBellAbove(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        Object nmsLevel = context.getLevel().minecraftWorld();
        Object nmsPos = LocationUtils.toBlockPos(above.x(), above.y(), above.z());
        Object aboveState = NMSHelper.getBlockState(nmsLevel, nmsPos);
        Key owner = BlockStateUtils.getBlockOwnerIdFromState(aboveState);
        return BlockKeys.BELL.equals(owner);
    }

    @Override
    public Object updateShape(Object thisBlock, Object[] args) {
        if (this.tiedToBellProperty == null) {
            return args[0];
        }

        Direction direction = DirectionUtils.fromNMSDirection(args[updateShape$direction]);
        if (direction != Direction.UP) {
            return args[0];
        }

        Object neighborState = args[updateShape$neighborState];
        Key owner = BlockStateUtils.getBlockOwnerIdFromState(neighborState);
        boolean isBell = BlockKeys.BELL.equals(owner);

        Optional<ImmutableBlockState> customState =
                BlockStateUtils.getOptionalCustomBlockState(args[0]);
        if (customState.isPresent()) {
            boolean currentBell = customState.get().get(this.tiedToBellProperty) == Boolean.TRUE;
            if (currentBell != isBell) {
                ImmutableBlockState newState = customState.get().with(this.tiedToBellProperty, isBell);
                return newState.customBlockState().minecraftState();
            }
        }
        return args[0];
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context, ImmutableBlockState state) {
        Player player = context.getPlayer();
        if (player == null || player.isAdventureMode()) {
            return InteractionResult.PASS;
        }

        boolean reeling = player.isSneaking();
        if (reeling) {

            if (!tryReelRope(context)) {
                return InteractionResult.PASS;
            }
        } else {

            if (!tryRingBell(context)) {
                return InteractionResult.PASS;
            }
        }
        player.swingHand(context.getHand());
        return RopeInteractionResult.afterSuccessfulAction(reeling);
    }

    private boolean tryRingBell(UseOnContext context) {
        Object nmsLevel = context.getLevel().minecraftWorld();
        org.bukkit.World world = NMSHelper.bukkitWorldOf(nmsLevel);
        if (world == null) return false;

        int x = context.getClickedPos().x();
        int y = context.getClickedPos().y();
        int z = context.getClickedPos().z();

        for (int i = 0; i < this.bellSearchRange; i++) {
            y++;
            Object nmsPos = LocationUtils.toBlockPos(x, y, z);
            Object aboveState = NMSHelper.getBlockState(nmsLevel, nmsPos);
            Key owner = BlockStateUtils.getBlockOwnerIdFromState(aboveState);

            if (BlockKeys.BELL.equals(owner)) {
                if (world.getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Bell bell) {
                    bell.ring();
                    return true;
                }
                return false;
            }

            Optional<ImmutableBlockState> aboveCustom =
                    BlockStateUtils.getOptionalCustomBlockState(aboveState);
            if (aboveCustom.isPresent()) {
                Key aboveKey = aboveCustom.get().owner().keyOptional()
                        .map(h -> h.location())
                        .orElse(null);
                if (aboveKey != null && aboveKey.equals(this.blockDefinition.id())) {
                    continue;
                }
            }
            break;
        }
        return false;
    }

    private boolean tryReelRope(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        try {
            Object nmsLevel = context.getLevel().minecraftWorld();
            BlockPos searchPos = context.getClickedPos().below();
            int minY = context.getLevel().worldHeight().getMinBuildHeight();
            Key blockKey = this.blockDefinition.id();

            while (searchPos.y() >= minY) {
                Key owner = getCEBlockOwner(context, searchPos);
                if (blockKey.equals(owner)) {
                    searchPos = searchPos.below();
                } else {

                    BlockPos target = searchPos.above();

                    if (player.isCreativeMode()) {
                        destroyBlock(context, target);
                        return true;
                    }

                    if (giveRopeItem(player)) {
                        destroyBlock(context, target);
                        return true;
                    }

                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private Key getCEBlockOwner(UseOnContext context, BlockPos cePos) {
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

    private boolean giveRopeItem(Player cePlayer) {
        try {
            org.bukkit.entity.Player bukkitPlayer =
                    (org.bukkit.entity.Player) cePlayer.platformPlayer();

            java.util.List<Key> candidates = new java.util.ArrayList<>();
            if (this.reelItemKey != null) {
                candidates.add(this.reelItemKey);

                String val = this.reelItemKey.value();
                if (val.endsWith("_block")) {
                    candidates.add(Key.of(this.reelItemKey.namespace(),
                            val.substring(0, val.length() - "_block".length())));
                }
            }

            for (Key key : candidates) {
                BukkitItemDefinition itemDef = CraftEngineItems.byId(key);
                if (itemDef == null) {
                    org.bukkit.Bukkit.getLogger().info(
                            "[PapersDelight] Rope reel: no item found for key " + key);
                    continue;
                }
                org.bukkit.inventory.ItemStack bukkitStack = itemDef.buildBukkitItem();
                if (bukkitPlayer.getInventory().addItem(bukkitStack).isEmpty()) {
                    return true;
                }

                return false;
            }
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().warning(
                    "[PapersDelight] Rope reel giveItem error: " + e.getMessage());
        }
        return false;
    }

    private void destroyBlock(UseOnContext context, BlockPos cePos) {
        Object nmsLevel = context.getLevel().minecraftWorld();
        Object nmsPos = LocationUtils.toBlockPos(cePos.x(), cePos.y(), cePos.z());
        NMSHelper.setBlockState(nmsLevel, nmsPos, NMSHelper.airStateObj(), 3);
    }

    private static final Factory FACTORY = new Factory();

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:rope_block"), FACTORY);
    }

    private static final class Factory implements BlockBehaviorFactory<RopeBlockBehavior> {

        @Override
        public RopeBlockBehavior create(BlockDefinition block, ConfigSection section) {
            Property<Boolean> tiedToBell = BlockBehaviorFactory.getProperty(
                    section.path(), block,
                    section.getString(TIED_TO_BELL_PROPERTY, (String) null),
                    Boolean.class
            );
            int searchRange = section.getInt(BELL_SEARCH_RANGE, 24);
            if (searchRange < 1) searchRange = 24;

            String reelItemStr = section.getString(REEL_ITEM, (String) null);
            Key reelItemKey;
            if (reelItemStr != null) {
                reelItemKey = Key.of(reelItemStr);
            } else {

                reelItemKey = block.id();
            }

            return new RopeBlockBehavior(block, tiedToBell, searchRange, reelItemKey);
        }
    }
}
