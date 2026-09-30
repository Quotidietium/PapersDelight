package dev.tako.papersdelight.mechanic.villager;

import dev.tako.papersdelight.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VillagerConfig {

    public record BuyTrade(int level, String itemId, int amount, int maxUses, int villagerXp,
                           double priceMultiplier) {}

    public record SellTrade(String itemId, int emeraldCost, int amount, int maxUses,
                            int villagerXp, double priceMultiplier) {}

    private final boolean buyEnabled;
    private final List<BuyTrade> buyTrades;

    private final boolean sellEnabled;
    private final List<SellTrade> sellTrades;

    private final boolean harvestEnabled;

    private final boolean pickupEnabled;
    private final int pickupIntervalTicks;

    private final boolean breedEnabled;
    private final int breedIntervalTicks;

    private VillagerConfig(
            boolean buyEnabled, List<BuyTrade> buyTrades,
            boolean sellEnabled, List<SellTrade> sellTrades,
            boolean harvestEnabled,
            boolean pickupEnabled, int pickupIntervalTicks,
            boolean breedEnabled, int breedIntervalTicks
    ) {
        this.buyEnabled = buyEnabled;
        this.buyTrades = List.copyOf(buyTrades);
        this.sellEnabled = sellEnabled;
        this.sellTrades = List.copyOf(sellTrades);
        this.harvestEnabled = harvestEnabled;
        this.pickupEnabled = pickupEnabled;
        this.pickupIntervalTicks = pickupIntervalTicks;
        this.breedEnabled = breedEnabled;
        this.breedIntervalTicks = breedIntervalTicks;
    }

    public boolean buyEnabled() { return buyEnabled; }
    public List<BuyTrade> buyTrades() { return buyTrades; }
    public boolean sellEnabled() { return sellEnabled; }
    public List<SellTrade> sellTrades() { return sellTrades; }
    public boolean harvestEnabled() { return harvestEnabled; }
    public boolean pickupEnabled() { return pickupEnabled; }
    public int pickupIntervalTicks() { return pickupIntervalTicks; }
    public boolean breedEnabled() { return breedEnabled; }
    public int breedIntervalTicks() { return breedIntervalTicks; }

    public static VillagerConfig load() {

        boolean master = ConfigManager.getBoolean("villager.enable", true);
        return new VillagerConfig(
                master && ConfigManager.getBoolean("villager.farmers_buy_crops.enable", true),
                parseBuyTrades(),
                master && ConfigManager.getBoolean("villager.wandering_trader_sells.enable", true),
                parseSellTrades(),
                master && ConfigManager.getBoolean("villager.harvest.enable", true),
                master && ConfigManager.getBoolean("villager.pickup.enable", true),
                Math.max(1, ConfigManager.getInt("villager.pickup.scan_interval_ticks", 20)),
                master && ConfigManager.getBoolean("villager.breed.enable", true),
                Math.max(1, ConfigManager.getInt("villager.breed.scan_interval_ticks", 40))
        );
    }

    private static List<BuyTrade> parseBuyTrades() {
        List<BuyTrade> result = new ArrayList<>();
        List<?> raw = ConfigManager.getConfig().getList("villager.farmers_buy_crops.trades");
        if (raw == null) return result;
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            String itemId = asString(map.get("item"));
            if (itemId == null) continue;
            result.add(new BuyTrade(
                    asInt(map.get("level"), 1),
                    itemId,
                    Math.max(1, asInt(map.get("amount"), 1)),
                    Math.max(1, asInt(map.get("max_uses"), 16)),
                    Math.max(0, asInt(map.get("villager_xp"), 2)),
                    asDouble(map.get("price_multiplier"), 0.05D)
            ));
        }
        return result;
    }

    private static List<SellTrade> parseSellTrades() {
        List<SellTrade> result = new ArrayList<>();
        List<?> raw = ConfigManager.getConfig().getList("villager.wandering_trader_sells.items");
        if (raw == null) return result;
        for (Object entry : raw) {
            if (entry instanceof String id) {
                result.add(new SellTrade(id, 1, 1, 12, 1, 0.05D));
                continue;
            }
            if (!(entry instanceof Map<?, ?> map)) continue;
            String itemId = asString(map.get("item"));
            if (itemId == null) continue;
            result.add(new SellTrade(
                    itemId,
                    Math.max(1, asInt(map.get("emerald_cost"), 1)),
                    Math.max(1, asInt(map.get("amount"), 1)),
                    Math.max(1, asInt(map.get("max_uses"), 12)),
                    Math.max(0, asInt(map.get("villager_xp"), 1)),
                    asDouble(map.get("price_multiplier"), 0.05D)
            ));
        }
        return result;
    }

    private static String asString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }

    private static int asInt(Object value, int defaultVal) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return defaultVal;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static double asDouble(Object value, double defaultVal) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return defaultVal;
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
