package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.util.ItemMetaUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class JugLoreUtil {

    private JugLoreUtil() {
    }

    public static void applyFluidLore(ItemStack item,
                                      @Nullable String fluidKey,
                                      int amount,
                                      int capacity) {
        if (item == null || item.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        ItemMetaUtil.setLore(meta, buildLore(
                fluidKey,
                amount,
                capacity,
                ConfigManager.getOr("jug_fluid_empty_lore", ""),
                ConfigManager.getOr("jug_fluid_lore", ""),
                ConfigManager.getOr("jug_fluid_lore_buckets", "")));
        item.setItemMeta(meta);
    }

    static List<String> buildLore(@Nullable String fluidKey,
                                  int amount,
                                  int capacity,
                                  @Nullable String emptyTemplate,
                                  @Nullable String amountTemplate,
                                  @Nullable String containersTemplate) {
        List<String> lore = new ArrayList<>(2);
        if (fluidKey == null || amount <= 0) {
            if (emptyTemplate != null) lore.add(emptyTemplate);
            return lore;
        }
        if (amountTemplate != null) {
            lore.add(amountTemplate
                    .replace("%amount%", Integer.toString(amount))
                    .replace("%capacity%", Integer.toString(capacity)));
        }
        if (containersTemplate != null) {
            lore.add(containersTemplate
                    .replace("%buckets%", Integer.toString(JugFluidLevel.buckets(amount)))
                    .replace("%bottles%", Integer.toString(JugFluidLevel.bottles(amount))));
        }
        return lore;
    }
}
