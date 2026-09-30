package dev.tako.papersdelight.mechanic.farm;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class CropBonemealFix implements Listener {

    private static final Set<String> CROP_BLOCK_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void registerCropBlockId(String id) {
        CROP_BLOCK_IDS.add(id);
    }

    public static Set<String> getCropBlockIds() {
        return CROP_BLOCK_IDS;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.BONE_MEAL) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        String customId = CraftEngineUtil.getCustomBlockId(block);
        if (customId == null || !CROP_BLOCK_IDS.contains(customId)) return;

        event.setUseItemInHand(Event.Result.DENY);
    }
}
