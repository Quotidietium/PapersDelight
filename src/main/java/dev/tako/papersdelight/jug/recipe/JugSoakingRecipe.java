package dev.tako.papersdelight.jug.recipe;

public record JugSoakingRecipe(
        String id,
        String ingredient,
        Object fluidExpression,
        int amount,
        String result,
        int time,
        boolean consumeFluid,
        String source
) {
    public JugSoakingRecipe {
        fluidExpression = JugFluidExpression.snapshot(fluidExpression);
    }
}
