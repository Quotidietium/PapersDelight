package dev.tako.papersdelight.mechanic.villager;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class VillagerSupport {

    private VillagerSupport() {
    }

    private static final java.util.Set<java.util.UUID> TRACKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void track(java.util.UUID id) {
        if (id != null) TRACKED.add(id);
    }

    static java.util.Set<java.util.UUID> tracked() {
        return TRACKED;
    }

    private static final String FARMER_KEY = "farmer";

    public static boolean isFarmer(Villager villager) {
        if (villager == null) return false;
        try {
            Object profession = villager.getProfession();
            if (profession == null) return false;
            if (profession instanceof org.bukkit.Keyed keyed) {
                return FARMER_KEY.equals(keyed.getKey().getKey());
            }

            return FARMER_KEY.equalsIgnoreCase(profession.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ItemStack resolveItem(Plugin plugin, String id, int amount) {
        if (id == null || id.isBlank()) return null;

        try {
            var def = CraftEngineItems.byId(id);
            if (def != null) {
                ItemStack stack = def.buildBukkitItem();
                if (stack != null && !stack.getType().isAir()) {
                    stack.setAmount(amount);
                    return stack;
                }
            }
        } catch (Throwable ignored) {

        }

        Material material = Material.matchMaterial(id);
        if (material == null || material.isAir()) {
            if (plugin != null) {
                plugin.getLogger().warning(dev.tako.papersdelight.config.ConfigManager.getOr(
                        "villager_item_resolve_fail", "[villager] 无法解析物品 id：%id%").replace("%id%", id));
            }
            return null;
        }
        return new ItemStack(material, amount);
    }

    public static String customItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        try {
            var key = CraftEngineItems.getCustomItemId(stack);
            return key == null ? null : key.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String resolveSeedId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        String ce = customItemId(stack);
        if (ce != null) return ce;
        return stack.getType().getKey().asString();
    }

    public static boolean matchesItemId(ItemStack stack, String id) {
        if (stack == null || id == null) return false;
        String ceId = customItemId(stack);
        if (ceId != null) {
            return ceId.equals(id);
        }
        Material material = Material.matchMaterial(id);
        return material != null && stack.getType() == material;
    }
}
