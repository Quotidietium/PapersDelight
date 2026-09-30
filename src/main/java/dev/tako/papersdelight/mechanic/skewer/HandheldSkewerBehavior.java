package dev.tako.papersdelight.mechanic.skewer;

import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviors;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.context.UseOnContext;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class HandheldSkewerBehavior extends ItemBehavior {
    public static final ItemBehaviorFactory<HandheldSkewerBehavior> FACTORY = new Factory();

    private final Settings settings;

    private HandheldSkewerBehavior(Settings settings) {
        this.settings = settings;
    }

    @Override
    public InteractionResult useOnBlock(UseOnContext context) {
        Player player = context.getPlayer();
        return player == null ? InteractionResult.PASS : start(player, context.getHand());
    }

    @Override
    public InteractionResult use(World world, @Nullable Player player, InteractionHand hand) {
        return player == null ? InteractionResult.PASS : start(player, hand);
    }

    private InteractionResult start(Player player, InteractionHand hand) {
        HandheldSkewerManager manager = HandheldSkewerManager.instance;
        if (manager == null) return InteractionResult.PASS;
        org.bukkit.entity.Player bukkit = (org.bukkit.entity.Player) player.platformPlayer();
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        return manager.tryStart(bukkit, slot, settings)
                ? InteractionResult.SUCCESS_AND_CANCEL : InteractionResult.PASS;
    }

    public static void register() {
        ItemBehaviors.register(Key.of("papersdelight:skewer_item"), FACTORY);
    }

    record Settings(String cookingProxy, String result, int cookTicks) {
        Settings {
            if (cookingProxy == null || cookingProxy.isEmpty()) {
                throw new IllegalArgumentException("cooking_proxy must be configured");
            }
            if (result == null || result.isEmpty()) {
                throw new IllegalArgumentException("result must be configured");
            }
            cookTicks = Math.max(1, cookTicks);
        }
    }

    private static final class Factory implements ItemBehaviorFactory<HandheldSkewerBehavior> {
        @Override
        public HandheldSkewerBehavior create(Pack pack, Path path, Key key, ConfigSection section) {
            String cookingProxy = section.getNonNullValue("cooking_proxy", ConfigConstants.ARGUMENT_SECTION)
                    .getAsIdentifier().toString();
            String result = section.getNonNullValue("result", ConfigConstants.ARGUMENT_SECTION)
                    .getAsIdentifier().toString();
            return new HandheldSkewerBehavior(HandheldSkewerSettingsParser.parse(
                    cookingProxy, result, section.getInt("cook_ticks", 120)));
        }
    }
}
