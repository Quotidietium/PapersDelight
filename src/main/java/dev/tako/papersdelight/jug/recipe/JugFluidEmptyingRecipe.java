package dev.tako.papersdelight.jug.recipe;

public record JugFluidEmptyingRecipe(
        String id,
        Object fluidExpression,
        int amount,
        String filledInput,
        String emptyResult,
        String source
) {
    public JugFluidEmptyingRecipe {
        fluidExpression = JugFluidExpression.snapshot(fluidExpression);
    }
}
