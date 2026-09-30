package dev.tako.papersdelight.jug;

import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

final class JugHopperTransfer {

    private JugHopperTransfer() {
    }

    static boolean transferOne(Container source, JugBlockEntityController controller) {
        for (int slot = 0; slot < source.getInventory().getSize(); slot++) {
            ItemStack sourceStack = source.getInventory().getItem(slot);
            if (isEmpty(sourceStack)) continue;

            ItemStack transferred = one(sourceStack);
            if (!canInsert(controller.input(), transferred)) continue;
            controller.input(insert(controller.input(), transferred));

            ItemStack remainder = sourceStack.clone();
            remainder.setAmount(remainingAfterSingleTransfer(remainder.getAmount()));
            source.getInventory().setItem(slot, remainder.getAmount() <= 0 ? null : remainder);
            return true;
        }
        return false;
    }

    static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    static int remainingAfterSingleTransfer(int amount) {
        return Math.max(0, amount - 1);
    }

    static ItemStack one(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

    static boolean canInsert(ItemStack current, ItemStack incoming) {
        return !isEmpty(incoming)
                && (isEmpty(current)
                || (current.isSimilar(incoming)
                && current.getAmount() + incoming.getAmount() <= current.getMaxStackSize()));
    }

    static ItemStack insert(ItemStack current, ItemStack incoming) {
        if (isEmpty(current)) return incoming.clone();
        ItemStack next = current.clone();
        next.setAmount(next.getAmount() + incoming.getAmount());
        return next;
    }
}
