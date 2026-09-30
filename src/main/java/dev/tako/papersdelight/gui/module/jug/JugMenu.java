package dev.tako.papersdelight.gui.module.jug;

import dev.tako.papersdelight.api.menu.Menu;
import dev.tako.papersdelight.api.menu.MenuItem;
import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.jug.JugLayout;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class JugMenu {
    private JugMenu() {
    }

    public static Menu create() {

        Menu menu = new Menu("<shift:-8><white><image:farmersdelight:jug></white>"
                + "<shift:-98><reset><lang:container.farmersdelight.jug>",
                JugLayout.SIZE);
        ItemStack border = ConfigManager.buildGuiItem("jug.buttons.border", null, "<white> </white>", List.of());
        for (int slot = 0; slot < JugLayout.SIZE; slot++) {
            menu.setItem(slot, new MenuItem(border, "border"));
        }
        menu.setItem(JugLayout.INPUT, new MenuItem(new ItemStack(Material.AIR), "input_slot", true));
        menu.setItem(JugLayout.OUTPUT, new MenuItem(new ItemStack(Material.AIR), "output_slot"));
        menu.setItem(JugLayout.FLUID, new MenuItem(new ItemStack(Material.AIR), "fluid"));
        menu.setItem(JugLayout.PROGRESS, new MenuItem(border, "progress"));
        menu.setItem(JugLayout.CAPACITY_BUCKETS, new MenuItem(new ItemStack(Material.AIR), "capacity_buckets"));
        menu.setItem(JugLayout.CAPACITY_BOTTLES, new MenuItem(new ItemStack(Material.AIR), "capacity_bottles"));
        return menu;
    }

    public static ItemStack emptyDecoration() {
        return ConfigManager.buildGuiItem("jug.buttons.empty", null, "<white> </white>", List.of());
    }

    public static ItemStack borderDecoration() {
        return ConfigManager.buildGuiItem("jug.buttons.border", null, "<white> </white>", List.of());
    }
}
