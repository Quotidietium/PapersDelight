package dev.tako.papersdelight.jug;

import net.momirealms.craftengine.core.item.component.DataComponentKeys;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JugCapacityBar {
    public static final int MAX_DAMAGE = 1000;
    private static final int BAR_PIXELS = 13;
    private static final Environment RUNTIME_ENVIRONMENT = new Environment() {
        @Override public boolean supportsTooltip() { return supportsTooltipDisplay(); }
        @Override public boolean hideTooltip(ItemStack item) { return hideDurabilityTooltip(item); }
    };
    private static volatile Environment environment = RUNTIME_ENVIRONMENT;

    private JugCapacityBar() {
    }

    static int barWidth(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) return 0;
        long clamped = Math.min((long) amount, capacity);
        return Math.min(BAR_PIXELS, Math.max(1, (int) Math.ceil(clamped * BAR_PIXELS / (double) capacity)));
    }

    public static int damage(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) return 0;
        int width = barWidth(amount, capacity);
        int visibleAmount = (int) Math.floor((double) width * MAX_DAMAGE / BAR_PIXELS);
        return Math.max(1, MAX_DAMAGE - visibleAmount);
    }

    public static void apply(ItemStack item, int amount, int capacity) {
        if (item == null || item.isEmpty()) return;
        ItemMeta original = item.getItemMeta();
        if (!(original instanceof Damageable)) return;
        ItemMeta snapshot = original.clone();
        int barDamage = damage(amount, capacity);
        try {
            Damageable damageable = (Damageable) original;
            if (barDamage <= 0) {
                damageable.resetDamage();
                damageable.setMaxDamage(null);
                if (!setMeta(item, original)) restore(item, snapshot);
                return;
            }
            Environment active = environment;
            if (!active.supportsTooltip()) return;
            damageable.setMaxDamage(MAX_DAMAGE);
            damageable.setDamage(barDamage);
            if (!setMeta(item, original) || !active.hideTooltip(item)) restore(item, snapshot);
        } catch (Throwable ignored) {
            restore(item, snapshot);
        }
    }

    private static boolean setMeta(ItemStack item, ItemMeta meta) {
        try {
            return item.setItemMeta(meta);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void restore(ItemStack item, ItemMeta snapshot) {
        try {
            if (!item.setItemMeta(snapshot.clone())) {

            }
        } catch (Throwable ignored) {

        }
    }

    private static boolean hideDurabilityTooltip(ItemStack item) {
        try {
            var manager = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance();
            if (manager == null) return false;
            var wrapped = manager.wrap(item);
            if (wrapped == null) return false;
            TooltipAccess access = new TooltipAccess() {
                @Override public Object get() {
                    return wrapped.getComponentAsJava(DataComponentKeys.TOOLTIP_DISPLAY);
                }
                @Override public void set(Map<String, Object> tooltip) {
                    wrapped.setJavaComponent(DataComponentKeys.TOOLTIP_DISPLAY, tooltip);
                }
            };
            if (!applyTooltip(access)) return false;
            ItemStack result = wrapped.getBukkitItem();
            if (result == null || result.isEmpty()) return false;
            if (result != item) {
                ItemMeta resultMeta = result.getItemMeta();
                if (resultMeta == null || !setMeta(item, resultMeta)) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    interface Environment {
        boolean supportsTooltip();
        boolean hideTooltip(ItemStack item);
    }

    static AutoCloseable installEnvironmentForTest(Environment replacement) {
        Environment previous = environment;
        environment = replacement;
        return () -> environment = previous;
    }

    @FunctionalInterface
    interface TooltipAccess {
        Object get();

        default void set(Map<String, Object> tooltip) {
            throw new UnsupportedOperationException();
        }
    }

    static boolean applyTooltip(TooltipAccess access) {
        try {
            Object component = access.get();
            Map<String, Object> existing = new LinkedHashMap<>();
            if (component instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> existing.put(String.valueOf(key), value));
            }
            access.set(mergeTooltip(existing));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Map<String, Object> mergeTooltip(Map<String, Object> existing) {
        Map<String, Object> copy = new LinkedHashMap<>(existing);
        List<Object> hidden = new ArrayList<>();
        Object old = copy.get("hidden_components");
        if (!(old instanceof List<?> list)) {
            if (old != null) hidden.add(old);
        } else {
            hidden.addAll(list);
        }
        addHidden(hidden, DataComponentKeys.DAMAGE.asString());
        addHidden(hidden, DataComponentKeys.MAX_DAMAGE.asString());
        copy.put("hidden_components", hidden);
        return copy;
    }

    private static void addHidden(List<Object> hidden, String key) {
        if (!hidden.contains(key)) hidden.add(key);
    }

    private static boolean supportsTooltipDisplay() {
        try {
            String[] parts = Bukkit.getMinecraftVersion().split("\\.");
            int major = versionPart(parts, 0);
            int minor = versionPart(parts, 1);
            int patch = versionPart(parts, 2);
            return major > 1 || (major == 1 && (minor > 21 || (minor == 21 && patch >= 2)));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int versionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
