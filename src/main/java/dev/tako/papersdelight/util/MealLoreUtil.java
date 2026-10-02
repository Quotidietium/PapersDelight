package dev.tako.papersdelight.util;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.font.Image;
import net.momirealms.craftengine.core.item.component.DataComponentKeys;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MealLoreUtil {

    private static final String SINGLE_SERVING_KEY = "tooltip.farmersdelight.cooking_pot.single_serving";

    private static final String MANY_SERVINGS_KEY = "tooltip.farmersdelight.cooking_pot.many_servings";

    private static final int BAR_MAX = 64;

    private MealLoreUtil() {
    }

    public static void applyMealLore(ItemStack container, @Nullable ItemStack meal) {
        if (container == null || container.isEmpty()) return;
        if (meal == null || meal.isEmpty()) return;

        ItemMeta meta = container.getItemMeta();
        if (meta == null) return;

        int servings = meal.getAmount();
        List<Component> lore = new ArrayList<>();

        lore.add(buildServingsLine(servings));
        lore.add(buildNameLine(meal));

        meta.lore(lore);

        // R7：份量条并入同一遍 meta（原实现先 setItemMeta，再在 applyServingsBar 里
        // 重新 getItemMeta/setItemMeta，每次上菜多付一对 meta 复制）
        boolean barApplied = false;
        if (supportsTooltipDisplay() && meta instanceof Damageable damageable) {
            int clamped = Math.max(1, Math.min(BAR_MAX, servings));
            damageable.setMaxDamage(BAR_MAX);
            damageable.setDamage(Math.max(1, BAR_MAX - clamped));
            barApplied = true;
        }
        container.setItemMeta(meta);
        if (barApplied) hideDurabilityLine(container);
    }

    private static Component buildNameLine(ItemStack meal) {
        Component name = itemDisplayComponent(meal)
                .colorIfAbsent(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);

        Component glyph = resolveIconGlyph(meal);
        if (glyph == null) return name;

        return Component.empty()
                .append(glyph.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.space())
                .append(name)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component buildServingsLine(int servings) {
        Component line = servings <= 1
                ? Component.translatable(SINGLE_SERVING_KEY)
                : Component.translatable(MANY_SERVINGS_KEY, Component.text(servings));
        return line.color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Nullable
    private static Component resolveIconGlyph(ItemStack meal) {
        String itemId = CraftEngineUtil.getItemIdentifier(meal);
        if (itemId == null || itemId.isEmpty()) return null;

        try {
            Key iconKey = Key.of(itemId);
            Image image = CraftEngine.instance().fontManager().imageById(iconKey).orElse(null);
            if (image == null) return null;

            String miniMessage = image.miniMessageAt(0, 0);
            if (miniMessage == null || miniMessage.isEmpty()) return null;

            return MiniMessage.miniMessage().deserialize(miniMessage);
        } catch (Throwable ignored) {

            return null;
        }
    }

    private static Component itemDisplayComponent(ItemStack meal) {
        try {
            var def = CraftEngineItems.byItemStack(meal);
            if (def != null) {
                String key = def.translationKey();
                if (key != null && !key.isEmpty()) return Component.translatable(key);
            }
        } catch (Throwable ignored) {

        }
        return Component.translatable(meal.getType().translationKey());
    }

    /** 版本判定结果运行期不变（Bukkit.getMinecraftVersion 恒定），首次调用后缓存（R7）。 */
    private static volatile Boolean tooltipDisplaySupported;

    private static boolean supportsTooltipDisplay() {
        Boolean cached = tooltipDisplaySupported;
        if (cached == null) {
            String[] parts = Bukkit.getMinecraftVersion().split("\\.");
            cached = versionAtLeast(versionPart(parts, 0), versionPart(parts, 1), versionPart(parts, 2));
            tooltipDisplaySupported = cached;
        }
        return cached;
    }

    private static boolean versionAtLeast(int major, int minor, int patch) {
        return major > 1 || (major == 1 && (minor > 21 || (minor == 21 && patch >= 2)));
    }

    private static void hideDurabilityLine(ItemStack container) {
        try {
            var itemManager = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance();
            if (itemManager == null) return;

            net.momirealms.craftengine.bukkit.item.BukkitItem wrapped = itemManager.wrap(container);
            if (wrapped == null) return;

            List<String> hidden = List.of(
                    DataComponentKeys.DAMAGE.asString(),
                    DataComponentKeys.MAX_DAMAGE.asString()
            );
            wrapped.setJavaComponent(DataComponentKeys.TOOLTIP_DISPLAY,
                    Map.of("hidden_components", hidden));

            ItemStack result = wrapped.getBukkitItem();
            if (result != null && !result.isEmpty() && result != container) {
                container.setItemMeta(result.getItemMeta());
            }
        } catch (Throwable ignored) {

        }
    }

    public static void overrideMaxStackSize(ItemStack display, int size) {
        if (display == null || display.isEmpty()) return;
        int clamped = Math.max(1, Math.min(99, size));
        try {
            var itemManager = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance();
            if (itemManager == null) return;

            net.momirealms.craftengine.bukkit.item.BukkitItem wrapped = itemManager.wrap(display);
            if (wrapped == null) return;

            wrapped.setJavaComponent(DataComponentKeys.MAX_STACK_SIZE, clamped);

            ItemStack result = wrapped.getBukkitItem();
            if (result != null && !result.isEmpty() && result != display) {
                display.setItemMeta(result.getItemMeta());
            }
        } catch (Throwable ignored) {

        }
    }

    private static int versionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
