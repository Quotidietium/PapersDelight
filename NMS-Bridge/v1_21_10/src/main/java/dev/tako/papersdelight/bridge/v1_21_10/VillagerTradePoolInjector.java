package dev.tako.papersdelight.bridge.v1_21_10;

import dev.tako.papersdelight.bridge.api.VillagerTradePool;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VillagerTradePoolInjector {

    private VillagerTradePoolInjector() {
    }

    public static boolean install(VillagerTradePool pool) {
        if (pool == null || pool.isEmpty()) return false;

        injectFarmerBuys(VillagerTrades.TRADES, pool.farmerBuys());
        injectFarmerBuys(VillagerTrades.EXPERIMENTAL_TRADES, pool.farmerBuys());
        injectWanderingTraderSells(pool.wanderingTraderSells());
        return true;
    }

    private static void injectFarmerBuys(
            java.util.Map<net.minecraft.resources.ResourceKey<VillagerProfession>,
                    Int2ObjectMap<VillagerTrades.ItemListing[]>> table,
            List<VillagerTradePool.BuyEntry> entries) {
        if (entries.isEmpty() || table == null) return;
        Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel = table.get(VillagerProfession.FARMER);
        if (byLevel == null) return;

        for (int level : entries.stream().mapToInt(VillagerTradePool.BuyEntry::level).distinct().toArray()) {
            VillagerTrades.ItemListing[] existing = byLevel.get(level);
            if (existing == null) continue;

            List<VillagerTrades.ItemListing> merged = withoutOurListings(existing);
            for (VillagerTradePool.BuyEntry entry : entries) {
                if (entry.level() != level) continue;
                ItemStack ingredient = toNms(entry.ingredient());
                if (ingredient.isEmpty()) continue;
                merged.add(buyListing(entry, ingredient));
            }
            byLevel.put(level, merged.toArray(new VillagerTrades.ItemListing[0]));
        }
    }

    private static void injectWanderingTraderSells(List<VillagerTradePool.SellEntry> entries) {
        if (entries.isEmpty()) return;

        List<Pair<VillagerTrades.ItemListing[], Integer>> groups = VillagerTrades.WANDERING_TRADER_TRADES;
        if (groups.isEmpty()) return;

        Pair<VillagerTrades.ItemListing[], Integer> generic = groups.get(0);
        List<VillagerTrades.ItemListing> merged = withoutOurListings(generic.getLeft());
        for (VillagerTradePool.SellEntry entry : entries) {
            ItemStack result = toNms(entry.result());
            if (result.isEmpty()) continue;
            merged.add(sellListing(entry, result));
        }

        List<Pair<VillagerTrades.ItemListing[], Integer>> replacement = new ArrayList<>(groups);
        replacement.set(0, Pair.of(merged.toArray(new VillagerTrades.ItemListing[0]), generic.getRight()));
        replaceWanderingTraderTrades(replacement);
    }

    private static void replaceWanderingTraderTrades(
            List<Pair<VillagerTrades.ItemListing[], Integer>> replacement) {
        try {
            java.lang.reflect.Field field =
                    VillagerTrades.class.getDeclaredField("WANDERING_TRADER_TRADES");
            sun.misc.Unsafe unsafe = unsafe();
            if (unsafe == null) return;
            Object base = unsafe.staticFieldBase(field);
            long offset = unsafe.staticFieldOffset(field);
            unsafe.putObject(base, offset, List.copyOf(replacement));
        } catch (ReflectiveOperationException | RuntimeException e) {

        }
    }

    private static sun.misc.Unsafe unsafe() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static PapersDelightListing buyListing(VillagerTradePool.BuyEntry entry, ItemStack ingredient) {

        ItemCost cost = new ItemCost(
                ingredient.getItemHolder(),
                ingredient.getCount(),
                net.minecraft.core.component.DataComponentExactPredicate.allOf(ingredient.getComponents())
        );
        return (trader, random) -> new MerchantOffer(
                cost,
                new ItemStack(net.minecraft.world.item.Items.EMERALD, entry.emeraldAmount()),
                entry.maxUses(),
                entry.villagerXp(),
                entry.priceMultiplier()
        );
    }

    private static PapersDelightListing sellListing(VillagerTradePool.SellEntry entry, ItemStack result) {
        return (trader, random) -> new MerchantOffer(
                new ItemCost(net.minecraft.world.item.Items.EMERALD, entry.emeraldCost()),

                result.copy(),
                entry.maxUses(),
                entry.villagerXp(),
                entry.priceMultiplier()
        );
    }

    private static List<VillagerTrades.ItemListing> withoutOurListings(VillagerTrades.ItemListing[] existing) {
        List<VillagerTrades.ItemListing> result = new ArrayList<>(Arrays.asList(existing));
        result.removeIf(listing -> listing instanceof PapersDelightListing);
        return result;
    }

    private static ItemStack toNms(org.bukkit.inventory.ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : CraftItemStack.asNMSCopy(stack);
    }

    private interface PapersDelightListing extends VillagerTrades.ItemListing {
    }
}
