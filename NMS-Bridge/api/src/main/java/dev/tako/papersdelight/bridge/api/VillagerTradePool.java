package dev.tako.papersdelight.bridge.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class VillagerTradePool {

    public record BuyEntry(
            int level,
            ItemStack ingredient,
            int emeraldAmount,
            int maxUses,
            int villagerXp,
            float priceMultiplier
    ) {}

    public record SellEntry(
            ItemStack result,
            int emeraldCost,
            int maxUses,
            int villagerXp,
            float priceMultiplier
    ) {}

    private final List<BuyEntry> farmerBuys;
    private final List<SellEntry> wanderingTraderSells;

    public VillagerTradePool(List<BuyEntry> farmerBuys, List<SellEntry> wanderingTraderSells) {
        this.farmerBuys = List.copyOf(farmerBuys);
        this.wanderingTraderSells = List.copyOf(wanderingTraderSells);
    }

    public List<BuyEntry> farmerBuys() { return farmerBuys; }

    public List<SellEntry> wanderingTraderSells() { return wanderingTraderSells; }

    public boolean isEmpty() { return farmerBuys.isEmpty() && wanderingTraderSells.isEmpty(); }
}
