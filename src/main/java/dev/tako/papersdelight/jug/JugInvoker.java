package dev.tako.papersdelight.jug;

import dev.tako.libuid.api.FluidAction;
import dev.tako.libuid.api.FluidRegistry;
import dev.tako.libuid.api.FluidStack;
import dev.tako.libuid.api.FluidTank;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

final class JugInvoker {

    private JugInvoker() {
    }

    @Nullable
    static String keyOf(@Nullable FluidStack stack) {
        if (stack == null) return null;
        try {
            return stack.isEmpty() || stack.fluidKey() == null ? null : stack.fluidKey().toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static int amountOf(@Nullable FluidStack stack) {
        if (stack == null) return 0;
        try {
            return stack.isEmpty() ? 0 : Math.max(stack.amount(), 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static FluidStack empty() {
        return FluidStack.EMPTY;
    }

    static FluidStack stackOf(@Nullable String fluidKey, int amount) {
        if (fluidKey == null || fluidKey.isBlank() || amount <= 0) return empty();
        try {
            return FluidRegistry.get(fluidKey)
                    .map(type -> FluidStack.of(type, amount))
                    .orElseGet(JugInvoker::empty);
        } catch (Throwable ignored) {
            return empty();
        }
    }

    static int fill(@Nullable FluidTank tank, @Nullable FluidStack stack, @Nullable FluidAction action) {
        if (tank == null || stack == null || action == null) return 0;
        try {
            return stack.isEmpty() ? 0 : Math.max(tank.fill(stack, action), 0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static FluidStack drain(@Nullable FluidTank tank, int amount, @Nullable FluidAction action) {
        if (tank == null || amount <= 0 || action == null) return empty();
        try {
            FluidStack drained = tank.drain(amount, action);
            return drained == null ? empty() : drained;
        } catch (Throwable ignored) {
            return empty();
        }
    }

    static boolean isContainerCandidate(@Nullable ItemStack stack) {
        try {
            return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
