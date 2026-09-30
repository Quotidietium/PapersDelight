package dev.tako.papersdelight.jug;

import dev.tako.libuid.api.FluidStack;
import dev.tako.libuid.api.FluidStackCodec;
import dev.tako.libuid.api.FluidTank;
import dev.tako.libuid.api.item.ItemFluidData;
import dev.tako.libuid.api.item.ItemFluidDataReadResult;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public final class JugFluidItemData {

    public static final NamespacedKey KEY_LEGACY_FLUID =
            new NamespacedKey("papersdelight", "jug_fluid");
    public static final NamespacedKey KEY_INPUT =
            new NamespacedKey("papersdelight", "jug_input");
    public static final NamespacedKey KEY_OUTPUT =
            new NamespacedKey("papersdelight", "jug_output");

    private JugFluidItemData() {
    }

    public static void writeTo(ItemStack item, FluidTank tank,
                               @Nullable ItemStack input, @Nullable ItemStack output) {
        if (item == null || item.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        writeItem(pdc, KEY_INPUT, input);
        writeItem(pdc, KEY_OUTPUT, output);
        item.setItemMeta(meta);

        ItemFluidDataReadResult.Status status = ItemFluidData.read(item).status();
        if (shouldWriteFluid(status)) {
            ItemFluidData.write(item, tank.fluid());
            removeLegacyFluid(item);
        }
    }

    public static void writeOpaqueFluid(ItemStack item, byte[] opaqueFluid,
                                       @Nullable ItemStack input, @Nullable ItemStack output) {
        if (item == null || item.isEmpty() || isAbsent(opaqueFluid)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        writeItem(pdc, KEY_INPUT, input);
        writeItem(pdc, KEY_OUTPUT, output);
        pdc.set(ItemFluidData.KEY_FLUID_STACK, PersistentDataType.BYTE_ARRAY, copyOpaqueFluidForDrop(opaqueFluid));
        pdc.remove(KEY_LEGACY_FLUID);
        item.setItemMeta(meta);
    }

    public static ItemFluidDataReadResult.Status readInto(ItemStack item, FluidTank tank,
                                                            SlotWriter inputWriter, SlotWriter outputWriter) {
        if (item == null || item.isEmpty()) {
            tank.setFluid(FluidStack.EMPTY);
            return ItemFluidDataReadResult.Status.EMPTY;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            tank.setFluid(FluidStack.EMPTY);
            return ItemFluidDataReadResult.Status.EMPTY;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        ItemFluidDataReadResult result = ItemFluidData.read(item);
        ItemFluidDataReadResult.Status status = restoreFluid(tank, result);
        if (status == ItemFluidDataReadResult.Status.EMPTY && !pdc.has(ItemFluidData.KEY_FLUID_STACK)) {
            status = restoreLegacyFluid(tank, false, pdc.get(KEY_LEGACY_FLUID, PersistentDataType.BYTE_ARRAY));
        }
        inputWriter.accept(readItem(pdc, KEY_INPUT));
        outputWriter.accept(readItem(pdc, KEY_OUTPUT));
        return status;
    }

    @FunctionalInterface
    public interface SlotWriter {
        void accept(@Nullable ItemStack stack);
    }

    static boolean shouldWriteFluid(ItemFluidDataReadResult.Status status) {
        return status != ItemFluidDataReadResult.Status.INVALID;
    }

    static ItemFluidDataReadResult.Status restoreFluid(FluidTank tank, ItemFluidDataReadResult result) {
        switch (result.status()) {
            case EMPTY -> tank.setFluid(FluidStack.EMPTY);
            case PRESENT -> tank.setFluid(result.fluid().limitSize(tank.capacity()));
            case INVALID -> {

            }
        }
        return result.status();
    }

    static ItemFluidDataReadResult.Status restoreLegacyFluid(FluidTank tank, boolean hasLibuidFluid,
                                                               @Nullable byte[] legacyBytes) {
        if (hasLibuidFluid || isAbsent(legacyBytes)) return ItemFluidDataReadResult.Status.EMPTY;
        try {
            FluidStack legacy = FluidStackCodec.fromBinaryOptional(legacyBytes);
            if (legacy.isEmpty()) return ItemFluidDataReadResult.Status.EMPTY;
            tank.setFluid(legacy.limitSize(tank.capacity()));
            return ItemFluidDataReadResult.Status.PRESENT;
        } catch (Throwable ignored) {
            return ItemFluidDataReadResult.Status.EMPTY;
        }
    }

    @Nullable
    static byte[] readInvalidFluidBytes(ItemStack item) {
        if (item == null || item.isEmpty() || ItemFluidData.read(item).status() != ItemFluidDataReadResult.Status.INVALID) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        byte[] bytes = meta.getPersistentDataContainer().get(ItemFluidData.KEY_FLUID_STACK, PersistentDataType.BYTE_ARRAY);
        return isAbsent(bytes) ? null : bytes.clone();
    }

    static byte[] copyOpaqueFluidForDrop(byte[] bytes) {
        return bytes.clone();
    }

    private static void removeLegacyFluid(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(KEY_LEGACY_FLUID);
        item.setItemMeta(meta);
    }

    private static void writeItem(PersistentDataContainer pdc, NamespacedKey key,
                                  @Nullable ItemStack stack) {
        byte[] bytes = encodeItem(stack);
        if (isAbsent(bytes)) {
            pdc.remove(key);
            return;
        }
        pdc.set(key, PersistentDataType.BYTE_ARRAY, bytes);
    }

    static boolean isAbsent(@Nullable byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    @Nullable
    static byte[] encodeItem(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.serializeAsBytes();
    }

    @Nullable
    private static ItemStack readItem(PersistentDataContainer pdc, NamespacedKey key) {
        return decodeItem(pdc.get(key, PersistentDataType.BYTE_ARRAY));
    }

    @Nullable
    static ItemStack decodeItem(@Nullable byte[] bytes) {
        return decodeItem(bytes, ItemStack::deserializeBytes);
    }

    @Nullable
    static ItemStack decodeItem(@Nullable byte[] bytes, ItemDecoder decoder) {
        if (isAbsent(bytes)) return null;
        try {
            ItemStack stack = decoder.decode(bytes);
            return stack == null || stack.isEmpty() ? null : stack;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    interface ItemDecoder {
        @Nullable ItemStack decode(byte[] bytes);
    }
}
