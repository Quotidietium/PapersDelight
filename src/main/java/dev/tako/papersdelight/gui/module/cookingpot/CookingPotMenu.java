package dev.tako.papersdelight.gui.module.cookingpot;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.cookingpot.CookingPotLayout;
import dev.tako.papersdelight.api.menu.Menu;
import dev.tako.papersdelight.api.menu.MenuItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class CookingPotMenu {

    private CookingPotMenu() {}

    public static Menu create() {

        Menu menu = new Menu("<shift:-8><white><image:farmersdelight:cooking_pot></white>"
                + "<shift:-152><reset><lang:container.farmersdelight.cooking_pot>",
                CookingPotLayout.SIZE);

        ItemStack border = ConfigManager.buildGuiItem(

                "cooking_pot.buttons.border", null, "<white> </white>", List.of()
        );
        List<Integer> lockedSlots = ConfigManager.getIntegerList("cooking_pot.locked_slots");
        if (lockedSlots.isEmpty()) lockedSlots = List.of(0, 4, 6, 8, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 26);
        for (int slot : lockedSlots) {
            menu.setItem(slot, new MenuItem(border, "border"));
        }

        for (int slot : CookingPotLayout.INGREDIENTS) {
            menu.setItem(slot, new MenuItem(new ItemStack(Material.AIR), "ingredient_slot", true));
        }
        menu.setItem(CookingPotLayout.UTENSIL,
                new MenuItem(new ItemStack(Material.AIR), "utensil_slot", true));

        menu.setItem(5, new MenuItem(
                new ItemStack(Material.AIR),
                "progress"
        ));

        if (ConfigManager.getBoolean("cooking_pot.recipe_book", true)) {
            menu.setItem(9, new MenuItem(
                    ConfigManager.buildGuiItem("cooking_pot.buttons.recipe_book", "cooking_pot_recipe_book",
                            ConfigManager.getOr("cooking_pot_recipe_book_name", "<!i><green><bold>配方书"), List.of()),
                    "recipe_book"
            ));
        }

        menu.setItem(7, new MenuItem(
                new ItemStack(Material.AIR), "output_waiting", false
        ));

        menu.setItem(20, new MenuItem(
                ConfigManager.buildGuiItem("cooking_pot.buttons.heat_indicator.unheated",
                        null,
                        "<!i><white><lang:container.farmersdelight.cooking_pot.not_heated>", List.of()),
                "heat_indicator"
        ));

        menu.setItem(25, new MenuItem(
                new ItemStack(Material.AIR), "output_final", false
        ));

        return menu;
    }
}
