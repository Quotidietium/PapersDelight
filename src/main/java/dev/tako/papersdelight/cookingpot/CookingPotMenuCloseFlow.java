package dev.tako.papersdelight.cookingpot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class CookingPotMenuCloseFlow {
    private CookingPotMenuCloseFlow() { }

    record EditableSnapshot(ItemStack[] ingredients, ItemStack utensil) {
        EditableSnapshot {
            ingredients = ingredients == null ? new ItemStack[6] : ingredients.clone();
        }
        static EditableSnapshot read(Inventory inventory) {
            ItemStack[] ingredients = new ItemStack[6];
            for (int i = 0; i < CookingPotLayout.INGREDIENTS.length; i++) {
                ItemStack item = inventory.getItem(CookingPotLayout.INGREDIENTS[i]);
                ingredients[i] = copy(item);
            }
            return new EditableSnapshot(ingredients, copy(inventory.getItem(CookingPotLayout.UTENSIL)));
        }
        private static ItemStack copy(ItemStack item) { return item == null || item.isEmpty() ? null : item.clone(); }
    }

    interface Scheduler {
        void entity(Runnable task);
        void region(Runnable task);
        default void entity(Runnable task, Runnable retired) { entity(task); }
        default void region(Runnable task, Runnable retired) { region(task); }
    }

    static void requestClose(Scheduler scheduler, BooleanSupplier current,
                             BooleanSupplier ownsTop, Runnable close) {
        scheduler.entity(() -> {
            if (current.getAsBoolean() && ownsTop.getAsBoolean()) close.run();
        });
    }

    static void captureAndPersist(Scheduler scheduler, BooleanSupplier current,
                                  Inventory inventory, Consumer<EditableSnapshot> write,
                                  Runnable release) {
        captureAndPersist(scheduler, current, inventory, write, release, release, ignored -> release.run());
    }

    static void captureAndPersist(Scheduler scheduler, BooleanSupplier current,
                                  Inventory inventory, Consumer<EditableSnapshot> write,
                                  Runnable release, Runnable retired) {
        captureAndPersist(scheduler, current, inventory, write, release, retired, ignored -> retired.run());
    }

    static void captureAndPersist(Scheduler scheduler, BooleanSupplier current,
                                  Inventory inventory, Consumer<EditableSnapshot> write,
                                  Runnable release, Runnable entityRetired,
                                  Consumer<EditableSnapshot> regionRetired) {
        scheduler.entity(() -> {
            if (!current.getAsBoolean()) return;
            final EditableSnapshot snapshot;
            try {
                snapshot = EditableSnapshot.read(inventory);
            } catch (Throwable failure) {
                entityRetired.run();
                return;
            }
            try {
                scheduler.region(() -> {
                    if (!current.getAsBoolean()) return;
                    try {
                        write.accept(snapshot);
                        release.run();
                    } catch (Throwable failure) {
                        regionRetired.accept(snapshot);
                    }
                }, () -> regionRetired.accept(snapshot));
            } catch (Throwable failure) {
                regionRetired.accept(snapshot);
            }
        }, entityRetired);
    }
}
