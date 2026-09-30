package dev.tako.papersdelight.mechanic.cutting;

import dev.tako.papersdelight.config.ConfigManager.SoundConfig;
import dev.tako.papersdelight.api.item.ItemMatcher;
import dev.tako.papersdelight.api.item.ItemResult;

import javax.annotation.Nullable;
import java.util.List;

public record CuttingRecipe(String input, List<String> tools, List<ItemResult> results, @Nullable SoundConfig sound,
                            String source, ItemMatcher inputMatcher, ItemMatcher toolsMatcher) {

    public CuttingRecipe {
        tools = tools == null ? List.of() : List.copyOf(tools);
        results = results == null ? List.of() : List.copyOf(results);
        if (inputMatcher == null) inputMatcher = ItemMatcher.of(input);
        if (toolsMatcher == null) toolsMatcher = ItemMatcher.anyOf(tools);
    }

    public CuttingRecipe(String input, List<String> tools, List<ItemResult> results, @Nullable SoundConfig sound,
                         String source) {
        this(input, tools, results, sound, source, null, null);
    }
}
