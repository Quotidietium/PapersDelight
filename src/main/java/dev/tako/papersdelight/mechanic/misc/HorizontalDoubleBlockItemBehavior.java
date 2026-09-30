package dev.tako.papersdelight.mechanic.misc;

import net.momirealms.craftengine.bukkit.item.behavior.BlockItemBehavior;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.BlockPlaceContext;

import java.nio.file.Path;
import java.util.Map;

public final class HorizontalDoubleBlockItemBehavior extends BlockItemBehavior {

    public static final ItemBehaviorFactory<HorizontalDoubleBlockItemBehavior> FACTORY = new Factory();

    private HorizontalDoubleBlockItemBehavior(Key blockId) {
        super(blockId);
    }

    public static void register() {
        ItemBehaviors.register(Key.of("papersdelight:horizontal_double_block_item"), FACTORY);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, ImmutableBlockState state) {

        if (!super.canPlace(context, state)) return false;

        HorizontalDoubleBlockBlockBehavior behavior = state.behavior()
                .getFirst(HorizontalDoubleBlockBlockBehavior.class);
        if (behavior == null) return false;

        BlockPos innerPos = context.getClickedPos();
        World level = context.getLevel();

        return behavior.canPlaceMultiState(level, innerPos, state);
    }

    private static class Factory implements ItemBehaviorFactory<HorizontalDoubleBlockItemBehavior> {
        @SuppressWarnings("rawtypes")
        @Override
        public HorizontalDoubleBlockItemBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            ConfigValue blockValue = section.getNonNullValue("block", ConfigConstants.ARGUMENT_SECTION);
            if (blockValue.is(Map.class)) {

                BukkitBlockManager.instance().blockParser().addPendingConfigSection(
                        new PendingConfigSection(pack, path, key, blockValue.getAsSection()));
                return new HorizontalDoubleBlockItemBehavior(key);
            } else {
                return new HorizontalDoubleBlockItemBehavior(blockValue.getAsIdentifier());
            }
        }
    }
}
