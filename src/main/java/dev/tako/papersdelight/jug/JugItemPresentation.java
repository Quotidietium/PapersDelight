package dev.tako.papersdelight.jug;

import dev.tako.libuid.api.FluidRegistry;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import dev.tako.libuid.api.item.ItemFluidData;
import dev.tako.libuid.api.item.ItemFluidDataReadResult;
import dev.tako.papersdelight.util.TextUtil;
import dev.tako.papersdelight.util.ItemMetaUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.inventory.ItemStack;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

final class JugItemPresentation {
    private static final NamespacedKey ORIGINAL_MODEL =
            new NamespacedKey("papersdelight", "jug_original_item_model");
    private static final NamespacedKey ORIGINAL_LORE =
            new NamespacedKey("papersdelight", "jug_original_lore");
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private JugItemPresentation() {}

    static void refresh(ItemStack item, int capacity) {
        refresh(item, capacity, JugItemBehavior.modelPrefix(item));
    }

    static void refresh(ItemStack item, int capacity, String modelPrefix) {
        if (modelPrefix == null || modelPrefix.isBlank()) return;
        if (item == null || item.isEmpty()) return;
        ItemFluidDataReadResult data = ItemFluidData.read(item);
        if (data.status() != ItemFluidDataReadResult.Status.PRESENT
                && data.status() != ItemFluidDataReadResult.Status.EMPTY) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (data.status() == ItemFluidDataReadResult.Status.PRESENT && !data.fluid().isEmpty()) {
            var fluidType = FluidRegistry.get(data.fluid().fluidKey());
            Component fluidName = fluidType
                    .flatMap(t -> t.displayName())
                    .map(TextUtil::parse)
                    .orElse(Component.text(data.fluid().fluidKey().toString()));
            Component contains = TextUtil.parseList(List.of(
                    "<lang:tooltip.farmersdelight.jug.contains>")).get(0)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
            Component styledFluidName = fluidName
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
            Component fluidLore = Component.translatable("tooltip.farmersdelight.jug.fluid")
                    .args(List.of(styledFluidName, Component.text(Integer.toString(data.fluid().amount()))))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
            saveOriginalLore(meta);
            meta.lore(List.of(contains, fluidLore));
            saveOriginalModel(meta);
            setItemModel(meta, NamespacedKey.fromString(JugItemBehavior.modelPath(
                    modelPrefix,
                    fluidType.flatMap(t -> t.texture()).orElse(null),
                    Math.min(16, Math.max(1, (int) Math.round((double) data.fluid().amount() / 1000.0))))));
            applyTint(meta, fluidType.flatMap(t -> t.color()).orElse(null));
        } else {
            restoreOriginalLore(meta);
            restoreOriginalModel(meta);
            applyTint(meta, null);
        }
        item.setItemMeta(meta);
        JugCapacityBar.apply(item, data.status() == ItemFluidDataReadResult.Status.PRESENT
                ? data.fluid().amount() : 0, capacity);
    }

    private static void saveOriginalLore(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(ORIGINAL_LORE, PersistentDataType.STRING)) return;
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) return;
        pdc.set(ORIGINAL_LORE, PersistentDataType.STRING, serializeLore(lore));
    }

    private static void restoreOriginalLore(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        String stored = pdc.get(ORIGINAL_LORE, PersistentDataType.STRING);
        if (stored == null) return;
        List<Component> lore = deserializeLore(stored);
        if (lore != null) meta.lore(lore);
        pdc.remove(ORIGINAL_LORE);
    }

    private static String serializeLore(List<Component> lore) {
        return GSON.serialize(Component.text().append(lore).build());
    }

    private static List<Component> deserializeLore(String stored) {
        try {
            return List.copyOf(GSON.deserialize(stored).children());
        } catch (RuntimeException ignored) {

            return null;
        }
    }

    private static void saveOriginalModel(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(ORIGINAL_MODEL, PersistentDataType.STRING)) return;
        NamespacedKey original = ItemMetaUtil.getItemModel(meta);
        if (original != null) {
            pdc.set(ORIGINAL_MODEL, PersistentDataType.STRING, original.toString());
        }
    }

    private static void restoreOriginalModel(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        String original = pdc.get(ORIGINAL_MODEL, PersistentDataType.STRING);
        if (original == null) return;
        setItemModel(meta, NamespacedKey.fromString(original));
        pdc.remove(ORIGINAL_MODEL);
    }

    private static void setItemModel(ItemMeta meta, NamespacedKey model) {
        ItemMetaUtil.setItemModel(meta, model);
    }

    private static void applyTint(ItemMeta meta, Integer rgb) {
        if (!(meta instanceof LeatherArmorMeta leather)) return;
        leather.setColor(rgb == null ? null : Color.fromRGB(rgb));
    }
}
