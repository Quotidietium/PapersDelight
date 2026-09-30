package dev.tako.papersdelight.mechanic.skillet;

import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.bukkit.item.behavior.BlockItemBehavior;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;

public final class SkilletItemBehavior extends BlockItemBehavior {
    public static final ItemBehaviorFactory<SkilletItemBehavior> FACTORY = new Factory();

    private SkilletItemBehavior(Key blockId) {
        super(blockId);
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        SkilletManager manager = SkilletManager.instance;
        if (manager == null || !manager.isHandheldCookingSupported()) {
            return super.useOnBlock(context);
        }
        if (player.isSneaking()) {
            return super.useOnBlock(context);
        }
        return startCooking(player, context.getHand());
    }

    @Override
    public InteractionResult use(World world, @Nullable Player player, InteractionHand hand) {
        if (player == null) return InteractionResult.FAIL;
        return startCooking(player, hand);
    }

    private static InteractionResult startCooking(Player player, InteractionHand hand) {
        SkilletManager manager = SkilletManager.instance;
        if (manager == null || !manager.isHandheldCookingSupported()) return InteractionResult.PASS;

        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        return manager.tryStartHandheldCooking(bukkitPlayer, slot)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    public static void register() {
        ItemBehaviors.register(Key.of("papersdelight:skillet_item"), FACTORY);
    }

    private static final class Factory implements ItemBehaviorFactory<SkilletItemBehavior> {
        @Override
        public SkilletItemBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            ConfigValue blockValue = section.getNonNullValue("block", ConfigConstants.ARGUMENT_SECTION);
            if (blockValue.is(Map.class)) {
                BukkitBlockManager.instance().blockParser()
                        .addPendingConfigSection(new PendingConfigSection(pack, path, key, blockValue.getAsSection()));
                return new SkilletItemBehavior(key);
            }
            return new SkilletItemBehavior(blockValue.getAsIdentifier());
        }
    }
}
