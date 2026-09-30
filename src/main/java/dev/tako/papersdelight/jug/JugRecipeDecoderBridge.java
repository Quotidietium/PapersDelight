package dev.tako.papersdelight.jug;

import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.jetbrains.annotations.Nullable;

public final class JugRecipeDecoderBridge {

    @Nullable
    private static volatile JugRecipeDecoder decoder;

    private JugRecipeDecoderBridge() {
    }

    public static void install(@Nullable JugRecipeDecoder implementation) {
        decoder = implementation;
    }

    @Nullable
    public static JugFluidFillingRecipe decodeFluidFilling(String id, String source, ConfigSection section) {
        JugRecipeDecoder current = decoder;
        return current == null ? null : current.decodeFluidFilling(id, source, section);
    }

    @Nullable
    public static JugFluidEmptyingRecipe decodeFluidEmptying(String id, String source, ConfigSection section) {
        JugRecipeDecoder current = decoder;
        return current == null ? null : current.decodeFluidEmptying(id, source, section);
    }

    @Nullable
    public static JugSoakingRecipe decodeSoaking(String id, String source, ConfigSection section) {
        JugRecipeDecoder current = decoder;
        return current == null ? null : current.decodeSoaking(id, source, section);
    }

    public interface JugRecipeDecoder {
        @Nullable JugFluidFillingRecipe decodeFluidFilling(String id, String source, ConfigSection section);

        @Nullable JugFluidEmptyingRecipe decodeFluidEmptying(String id, String source, ConfigSection section);

        @Nullable JugSoakingRecipe decodeSoaking(String id, String source, ConfigSection section);
    }
}
