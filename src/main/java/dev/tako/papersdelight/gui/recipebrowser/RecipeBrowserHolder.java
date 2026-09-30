package dev.tako.papersdelight.gui.recipebrowser;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class RecipeBrowserHolder implements InventoryHolder {

    final BrowserPage page;
    final int listPage;
    final int recipeIndex;
    Inventory inventory;

    private RecipeBrowserHolder(BrowserPage page, int listPage, int recipeIndex) {
        this.page = page;
        this.listPage = listPage;
        this.recipeIndex = recipeIndex;
    }

    public static RecipeBrowserHolder home() {
        return new RecipeBrowserHolder(BrowserPage.HOME, 0, -1);
    }

    public static RecipeBrowserHolder list(BrowserPage page, int listPage) {
        return new RecipeBrowserHolder(page, listPage, -1);
    }

    public static RecipeBrowserHolder detail(BrowserPage page, int listPage, int recipeIndex) {
        return new RecipeBrowserHolder(page, listPage, recipeIndex);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
