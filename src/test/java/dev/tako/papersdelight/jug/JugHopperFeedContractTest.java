package dev.tako.papersdelight.jug;

import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JugHopperFeedContractTest {

    @Test
    void feedsIntoEmptyInputSlot() {
        InputSlot slot = new InputSlot(null);
        Hopper hopper = Hopper.holding(FakeItemStack.of("wheat", 5));

        assertTrue(        JugHopperTransfer.transferOne(hopper.container(), slot.controller()));

        assertNotNull(slot.value());
        assertEquals(1, slot.value().getAmount());
    }

    @Test
    void stacksIntoPartiallyFilledInputSlotOfSameItem() {
        InputSlot slot = new InputSlot(FakeItemStack.of("wheat", 10));
        Hopper hopper = Hopper.holding(FakeItemStack.of("wheat", 5));

        assertTrue(        JugHopperTransfer.transferOne(hopper.container(), slot.controller()),
                "输入槽存在同类部分堆叠时，漏斗仍应继续进料");

        assertEquals(11, slot.value().getAmount(), "进料应累加到已有堆叠上");
    }

    @Test
    void consumesExactlyOneFromHopperSlot() {
        InputSlot slot = new InputSlot(FakeItemStack.of("wheat", 10));
        Hopper hopper = Hopper.holding(FakeItemStack.of("wheat", 5));

        assertTrue(        JugHopperTransfer.transferOne(hopper.container(), slot.controller()));

        assertEquals(4, hopper.written(0).getAmount(), "漏斗槽位应只减少一个物品");
    }

    @Test
    void refusesWhenInputSlotIsFull() {
        InputSlot slot = new InputSlot(FakeItemStack.of("wheat", 64));
        Hopper hopper = Hopper.holding(FakeItemStack.of("wheat", 5));

        assertFalse(        JugHopperTransfer.transferOne(hopper.container(), slot.controller()),
                "输入槽已满时不应进料");

        assertEquals(64, slot.value().getAmount(), "满槽内容不应被改写");
    }

    @Test
    void refusesWhenInputSlotHoldsDifferentItem() {
        InputSlot slot = new InputSlot(FakeItemStack.of("wheat", 1));
        Hopper hopper = Hopper.holding(FakeItemStack.of("carrot", 5));

        assertFalse(        JugHopperTransfer.transferOne(hopper.container(), slot.controller()),
                "异类物品不应覆盖输入槽");

        assertEquals("wheat", FakeItemStack.kindOf(slot.value()), "原有物品不应被替换");
        assertEquals(1, slot.value().getAmount(), "原有数量不应变化");
    }

    @Test
    void skipsEmptyHopperSlotsBeforeFeeding() {
        InputSlot slot = new InputSlot(null);
        Hopper hopper = Hopper.holding(null, null, FakeItemStack.of("wheat", 2));

        assertTrue(        JugHopperTransfer.transferOne(hopper.container(), slot.controller()));

        assertNotNull(slot.value());
        assertEquals(1, slot.value().getAmount());
    }

    @Test
    void reportsFailureWhenHopperIsEmpty() {
        InputSlot slot = new InputSlot(null);
        Hopper hopper = Hopper.holding((ItemStack) null);

        assertFalse(        JugHopperTransfer.transferOne(hopper.container(), slot.controller()));
        assertNull(slot.value());
    }

    private static final class Hopper {
        private final Container container;
        private final Map<Integer, ItemStack> written = new HashMap<>();

        private Hopper(Container container) {
            this.container = container;
        }

        static Hopper holding(ItemStack... contents) {
            Inventory inventory = mock(Inventory.class);
            Container container = mock(Container.class);
            Hopper hopper = new Hopper(container);

            when(container.getInventory()).thenReturn(inventory);
            when(inventory.getSize()).thenReturn(contents.length);
            for (int slot = 0; slot < contents.length; slot++) {
                when(inventory.getItem(slot)).thenReturn(contents[slot]);
            }
            doAnswer(call -> {
                hopper.written.put(call.getArgument(0), call.getArgument(1));
                return null;
            }).when(inventory).setItem(anyInt(), any());

            return hopper;
        }

        Container container() {
            return container;
        }

        ItemStack written(int slot) {
            return written.get(slot);
        }
    }

    private static final class InputSlot {
        private ItemStack value;
        private final JugBlockEntityController controller;

        InputSlot(ItemStack initial) {
            this.value = initial;
            this.controller = mock(JugBlockEntityController.class);
            when(controller.input()).thenAnswer(call -> value);
            doAnswer(call -> {
                value = call.getArgument(0);
                return null;
            }).when(controller).input(any());
        }

        JugBlockEntityController controller() {
            return controller;
        }

        ItemStack value() {
            return value;
        }
    }
}
