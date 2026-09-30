package dev.tako.papersdelight.gui.module.jug;

import dev.tako.papersdelight.api.menu.ActionMapEventHandler;
import dev.tako.papersdelight.api.menu.MenuItem;
import dev.tako.papersdelight.jug.JugManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class JugEventHandler extends ActionMapEventHandler {
    private final JugManager jugManager;

    public JugEventHandler(JugManager jugManager) {
        this.jugManager = jugManager;
        bind("input_slot", (player, item, event) -> {

        });
        bind("output_slot", (player, item, event) -> jugManager.takeOutputToCursor(player, event));
        bind("fluid", (player, item, event) -> event.setCancelled(true));
        bind("progress", (player, item, event) -> event.setCancelled(true));
        bind("capacity_buckets", (player, item, event) -> event.setCancelled(true));
        bind("capacity_bottles", (player, item, event) -> event.setCancelled(true));
        bind("border", (player, item, event) -> event.setCancelled(true));
    }

    @Override
    public void handleDrag(Player player, MenuItem item, InventoryDragEvent event) {

        if (!"input_slot".equals(item.getActionId())) event.setCancelled(true);
    }

    @Override
    public boolean canQuickMove(Player player, MenuItem target, ItemStack moving) {
        return "input_slot".equals(target.getActionId());
    }
}
