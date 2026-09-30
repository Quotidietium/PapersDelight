package dev.tako.papersdelight.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;

public final class ItemMetaUtil {

    private static final MethodHandle ITEM_MODEL_SETTER = findItemModelSetter();
    private static final MethodHandle ITEM_MODEL_GETTER = findItemModelGetter();

    private ItemMetaUtil() {
    }

    public static boolean setItemModel(ItemMeta meta, NamespacedKey model) {
        if (meta == null || ITEM_MODEL_SETTER == null) return false;
        try {
            ITEM_MODEL_SETTER.invokeExact(meta, model);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static NamespacedKey getItemModel(ItemMeta meta) {
        if (meta == null || ITEM_MODEL_GETTER == null) return null;
        try {
            return (NamespacedKey) ITEM_MODEL_GETTER.invokeExact(meta);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static MethodHandle findItemModelGetter() {
        try {
            Method method = ItemMeta.class.getMethod("getItemModel");
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(NamespacedKey.class, ItemMeta.class));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static MethodHandle findItemModelSetter() {
        try {
            Method method = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(void.class, ItemMeta.class, NamespacedKey.class));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void applyCustomModelData(ItemMeta meta, int customModelData) {
        if (meta == null || customModelData == 0) return;
        meta.setCustomModelData(customModelData);
    }

    public static void setDisplayName(ItemMeta meta, String displayName) {
        if (meta == null) return;
        meta.displayName(TextUtil.parse(displayName));
    }

    public static void setLore(ItemMeta meta, List<String> lore) {
        if (meta == null) return;
        meta.lore(TextUtil.parseList(lore));
    }

    public static boolean applyColor(ItemMeta meta, @Nullable Integer color) {
        if (meta == null || color == null) return false;
        org.bukkit.Color rgb = org.bukkit.Color.fromRGB(color);
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leather) {
            leather.setColor(rgb);
            return true;
        }
        if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            potion.setColor(rgb);
            return true;
        }
        return false;
    }
}
