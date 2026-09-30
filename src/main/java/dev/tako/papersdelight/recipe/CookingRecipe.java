package dev.tako.papersdelight.recipe;

import java.util.List;

public class CookingRecipe {

    public final String type;
    public final String result;
    public final int resultCount;
    public final String container;
    public final int cookingTime;
    public final float experience;
    public final List<IngredientDef> ingredients;
    public final String source;

    public CookingRecipe(String type, String result, int resultCount, String container,
                         int cookingTime, float experience, List<IngredientDef> ingredients, String source) {
        this.type = type;
        this.result = result;
        this.resultCount = resultCount;
        this.container = container;
        this.cookingTime = cookingTime;
        this.experience = experience;
        this.ingredients = ingredients;
        this.source = source;
    }
}
