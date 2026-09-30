package dev.tako.papersdelight.gui.module.cookingpot;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class ExpandedCookingPotHolder implements InventoryHolder {

    enum View { LIST, DETAIL }

    final View view;
    final int page;
    final int recipeIndex;
    final Location potLocation;
    final boolean filterEnabled;
    Inventory inventory;

    private ExpandedCookingPotHolder(View view, int page, int recipeIndex, Location potLocation, boolean filterEnabled) {
        this.view = view;
        this.page = page;
        this.recipeIndex = recipeIndex;
        this.potLocation = potLocation;
        this.filterEnabled = filterEnabled;
    }

    static ExpandedCookingPotHolder list(int page, Location potLocation, boolean filterEnabled) {
        return new ExpandedCookingPotHolder(View.LIST, page, -1, potLocation, filterEnabled);
    }

    static ExpandedCookingPotHolder detail(int page, int recipeIndex, Location potLocation, boolean filterEnabled) {
        return new ExpandedCookingPotHolder(View.DETAIL, page, recipeIndex, potLocation, filterEnabled);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
