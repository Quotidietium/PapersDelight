package dev.tako.papersdelight.cookingpot;

import org.bukkit.inventory.ItemStack;

public class CookingPotData {

    public final ItemStack[] ingredients = new ItemStack[6];

    public ItemStack waitingOutput;

    public ItemStack utensil;

    public ItemStack finalOutput;

    public int progress;

    public int cookTime;
    public int cookTimeTotal = 200;

    public long lastSaveTick;

    public int cookDurationTicks = 200;

    public boolean isCooking;

    public String recipeResult;

    public int recipeResultCount = 1;

    public String recipeContainer;

    public float storedExperience;

    public transient int progressStage;

    public transient ItemStack renderedWaitingOutput;
    public transient ItemStack renderedWaitingSource;
    public transient String renderedWaitingContainer;

    public boolean isEmpty() {
        for (ItemStack ing : ingredients) {
            if (ing != null && !ing.isEmpty()) return false;
        }
        return (waitingOutput == null || waitingOutput.isEmpty())
                && (utensil == null || utensil.isEmpty())
                && (finalOutput == null || finalOutput.isEmpty());
    }
}
