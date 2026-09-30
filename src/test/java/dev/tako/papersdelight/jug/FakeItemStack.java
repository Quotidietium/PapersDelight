package dev.tako.papersdelight.jug;

import org.bukkit.inventory.ItemStack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class FakeItemStack {

    private FakeItemStack() {
    }

    static ItemStack of(String kind, int amount) {
        ItemStack stack = mock(ItemStack.class);
        int[] held = {amount};

        when(stack.getAmount()).thenAnswer(call -> held[0]);
        when(stack.getMaxStackSize()).thenReturn(64);
        when(stack.isEmpty()).thenAnswer(call -> held[0] <= 0);
        doAnswer(call -> {
            held[0] = call.getArgument(0);
            return null;
        }).when(stack).setAmount(org.mockito.ArgumentMatchers.anyInt());
        when(stack.clone()).thenAnswer(call -> of(kind, held[0]));
        when(stack.isSimilar(any())).thenAnswer(call -> {
            ItemStack other = call.getArgument(0);
            return other != null && kind.equals(kindOf(other));
        });
        when(stack.toString()).thenReturn(kind);

        return stack;
    }

    static String kindOf(ItemStack stack) {
        return stack == null ? null : stack.toString();
    }
}
