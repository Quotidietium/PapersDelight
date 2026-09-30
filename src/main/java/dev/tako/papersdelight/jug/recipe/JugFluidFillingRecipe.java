package dev.tako.papersdelight.jug.recipe;

public record JugFluidFillingRecipe(
        String id,
        Object fluidExpression,
        int amount,
        String emptyInput,
        String filledResult,
        String source
) {
    public JugFluidFillingRecipe {
        fluidExpression = JugFluidExpression.snapshot(fluidExpression);
    }
}
