package dev.tako.papersdelight.gui.module.cookingpot;

import dev.tako.papersdelight.cookingpot.CookingPotManager;
import dev.tako.papersdelight.api.menu.ActionMapEventHandler;
import dev.tako.papersdelight.api.menu.MenuItem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class CookingPotEventHandler extends ActionMapEventHandler {

    public CookingPotEventHandler(CookingPotRecipeBook recipeBook, CookingPotManager cookingPotManager) {

        bind("progress", (player, item, event) -> event.setCancelled(true));
        bind("ingredient_slot", (player, item, event) -> {

        });
        bind("utensil_slot", (player, item, event) -> {});

        bind("recipe_book", (player, item, event) -> {
            event.setCancelled(true);

            if (event.isShiftClick()) return;
            if (event.getCursor() != null && !event.getCursor().isEmpty()) return;

            Location potLoc = cookingPotManager.findSessionLocation(player);
            if (potLoc != null) {
                Block potBlock = potLoc.getBlock();
                recipeBook.openExpanded(player, potBlock);
            }

        });

        bind("heat_indicator", (player, item, event) -> event.setCancelled(true));
        bind("output_waiting", (player, item, event) -> event.setCancelled(true));

        bind("output_final", (player, item, event) -> {
            event.setCancelled(true);
            cookingPotManager.takeFinalOutputToCursor(player, event);
        });

        bind("close_menu", (player, item, event) -> {
            event.setCancelled(true);
            player.closeInventory();
        });

        bind("border", (player, item, event) -> event.setCancelled(true));
    }

    @Override
    public void handleDrag(Player player, MenuItem item, InventoryDragEvent event) {

    }

    @Override
    public boolean canQuickMove(Player player, MenuItem target, ItemStack moving) {
        return true;
    }
}
