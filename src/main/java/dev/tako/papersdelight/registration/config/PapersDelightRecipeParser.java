package dev.tako.papersdelight.registration.config;

import dev.tako.papersdelight.registration.config.GenerationAwareIdSectionConfigParser;
import dev.tako.papersdelight.api.recipe.RecipeTypeHandler;
import dev.tako.papersdelight.api.recipe.RecipeTypeRegistry;
import dev.tako.papersdelight.jug.recipe.JugFluidEmptyingRecipe;
import dev.tako.papersdelight.jug.recipe.JugFluidFillingRecipe;
import dev.tako.papersdelight.jug.recipe.JugSoakingRecipe;
import dev.tako.papersdelight.mechanic.cutting.CuttingRecipe;
import dev.tako.papersdelight.recipe.CookingRecipe;
import dev.tako.papersdelight.recipe.CustomRecipe;
import dev.tako.papersdelight.registration.PapersDelightLoadingStages;
import dev.tako.papersdelight.registration.PapersDelightParserTypes;
import dev.tako.papersdelight.registration.config.ParserGeneration;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ResourceException;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class PapersDelightRecipeParser extends GenerationAwareIdSectionConfigParser {

    private static final AtomicReference<RecipeSnapshot> SNAPSHOT = new AtomicReference<>(RecipeSnapshot.empty());
    private static final AtomicLong PUBLICATION_EPOCH = new AtomicLong();

    private final Logger logger;
    private final Consumer<String> warningSink;
    private RecipeDecoder decoder;
    private Map<String, CookingRecipe> pendingCooking;
    private Map<String, CuttingRecipe> pendingCutting;
    private Map<String, CustomRecipe.Single> pendingSingle;
    private Map<String, CustomRecipe.Decomposition> pendingDecomposition;
    private Map<String, JugFluidFillingRecipe> pendingFluidFilling;
    private Map<String, JugFluidEmptyingRecipe> pendingFluidEmptying;
    private Map<String, JugSoakingRecipe> pendingSoaking;
    private Set<String> seenIds;

    private int skippedCount;

    public PapersDelightRecipeParser(Plugin plugin) {
        this(plugin.getLogger(), null, ParserGeneration.active());
    }

    public PapersDelightRecipeParser(Plugin plugin, ParserGeneration generation) {
        this(plugin.getLogger(), null, generation);
    }

    PapersDelightRecipeParser(Logger logger, Consumer<String> warningSink) {
        this(logger, warningSink, ParserGeneration.active());
    }

    PapersDelightRecipeParser(Logger logger, Consumer<String> warningSink, ParserGeneration generation) {
        this(logger, warningSink, generation, () -> { });
    }

    PapersDelightRecipeParser(
            Logger logger,
            Consumer<String> warningSink,
            ParserGeneration generation,
            Runnable beforeCommit
    ) {
        super(generation, beforeCommit);
        this.logger = logger;
        this.warningSink = warningSink;
    }

    public static RecipeSnapshot snapshot() {
        return SNAPSHOT.get();
    }

    public static boolean hasSuccessfulPublication() {
        return PUBLICATION_EPOCH.get() > 0L;
    }

    public static long publicationEpoch() {
        return PUBLICATION_EPOCH.get();
    }

    public static void resetSnapshot() {
        SNAPSHOT.set(RecipeSnapshot.empty());
        PUBLICATION_EPOCH.set(0L);
    }

    @Override
    public @NotNull Key type() {
        return PapersDelightParserTypes.RECIPE;
    }

    @Override
    public String @NotNull [] sectionId() {
        return new String[]{"papersdelight_recipes"};
    }

    @Override
    public @NotNull LoadingStage loadingStage() {
        return PapersDelightLoadingStages.RECIPE;
    }

    @Override
    public @NotNull List<LoadingStage> dependencies() {
        return List.of(PapersDelightLoadingStages.ADVANCED_TAG);
    }

    @Override
    public boolean async() {

        return false;
    }

    @Override
    public void setErrorHandler(Consumer<ResourceException> errorHandler) {
        Consumer<ResourceException> delegate = Objects.requireNonNull(errorHandler, "errorHandler");
        super.setErrorHandler(error -> {
            if (!generationActive()) return;
            skippedCount++;
            delegate.accept(error);
        });
    }

    @Override
    public void preProcess() {
        clearPending();
        skippedCount = 0;
        if (!generationActive()) return;
        decoder = new RecipeDecoder(this::warn);
        pendingCooking = new LinkedHashMap<>();
        pendingCutting = new LinkedHashMap<>();
        pendingSingle = new LinkedHashMap<>();
        pendingDecomposition = new LinkedHashMap<>();
        pendingFluidFilling = new LinkedHashMap<>();
        pendingFluidEmptying = new LinkedHashMap<>();
        pendingSoaking = new LinkedHashMap<>();
        seenIds = new HashSet<>();
        skippedCount = 0;
        forEachExtensionHandler("beginParse", RecipeTypeHandler::beginParse);
    }

    private void forEachExtensionHandler(String phase, Consumer<RecipeTypeHandler> action) {
        for (RecipeTypeHandler handler : RecipeTypeRegistry.handlers()) {
            try {
                action.accept(handler);
            } catch (Throwable t) {
                logger.warning("Recipe type handler '" + handler.typeId()
                        + "' threw during " + phase + ": " + t);
            }
        }
    }

    @Override
    protected void parseSection(@NotNull Pack pack, @NotNull Path path, @NotNull Key id, @NotNull ConfigSection section) {
        if (!generationActive()) return;
        String recipeId = id.toString();

        if (seenIds.contains(recipeId)) {
            warn("Recipe " + recipeId + " at " + path + " is duplicate, keeping first occurrence");
            return;
        }
        seenIds.add(recipeId);

        String type = section.getString("type");
        if (type == null || type.isBlank()) {
            skippedCount++;
            warn("Recipe " + recipeId + " at " + path + " missing type field, skipping");
            return;
        }

        String source = path.toString();

        switch (type) {
            case "cooking" -> parseCooking(recipeId, source, section);
            case "cutting" -> parseCutting(recipeId, source, section);
            case "single", "info" -> parseSingle(recipeId, source, section);
            case "decomposition" -> parseDecomposition(recipeId, source, section);
            case "fluid_filling" -> parseFluidFilling(recipeId, source, section);
            case "fluid_emptying" -> parseFluidEmptying(recipeId, source, section);
            case "soaking" -> parseSoaking(recipeId, source, section);
            default -> parseExtension(recipeId, source, path, type, section);
        }
    }

    private void parseExtension(String recipeId, String source, Path path,
                                String type, ConfigSection section) {
        RecipeTypeHandler handler = RecipeTypeRegistry.find(type);
        if (handler == null) {
            skippedCount++;
            warn("Recipe " + recipeId + " at " + path + " has unknown type '" + type + "', skipping");
            return;
        }
        try {
            if (!handler.parse(recipeId, source, section)) {
                skippedCount++;
            }
        } catch (Throwable t) {

            skippedCount++;
            warn("Recipe " + recipeId + " at " + path + " failed in handler for type '"
                    + type + "': " + t);
        }
    }

    @Override
    public void postProcess() {
        commitIfGenerationActive(() -> {
            RecipeSnapshot candidate = new RecipeSnapshot(
                    pendingCooking,
                    pendingCutting,
                    pendingSingle,
                    pendingDecomposition,
                    pendingFluidFilling,
                    pendingFluidEmptying,
                    pendingSoaking
            );
            SNAPSHOT.set(candidate);
            PUBLICATION_EPOCH.incrementAndGet();

            int total = candidate.totalCount();
            logger.info("Parsed " + total + " recipes from CraftEngine config ("
                    + pendingCooking.size() + " cooking, "
                    + pendingCutting.size() + " cutting, "
                    + pendingSingle.size() + " single, "
                    + pendingDecomposition.size() + " decomposition, "
                    + pendingFluidFilling.size() + " fluid filling, "
                    + pendingFluidEmptying.size() + " fluid emptying, "
                    + pendingSoaking.size() + " soaking)");
            if (skippedCount > 0) {

                logger.warning("Skipped " + skippedCount
                        + " invalid recipe(s); the remaining recipes were loaded normally");
            }

            forEachExtensionHandler("publish", RecipeTypeHandler::publish);
        });
        clearPending();
    }

    @Override
    public void clearConfigs() {
        super.clearConfigs();
        clearPending();
        skippedCount = 0;
        forEachExtensionHandler("reset", RecipeTypeHandler::reset);
    }

    private void clearPending() {
        decoder = null;
        pendingCooking = null;
        pendingCutting = null;
        pendingSingle = null;
        pendingDecomposition = null;
        pendingFluidFilling = null;
        pendingFluidEmptying = null;
        pendingSoaking = null;
        seenIds = null;
    }

    private void parseCooking(String id, String source, ConfigSection section) {
        CookingRecipe recipe = decoder.decodeCooking(id, source, section);
        if (recipe != null) {
            pendingCooking.put(id, recipe);
        } else {
            skippedCount++;
        }
    }

    private void parseCutting(String id, String source, ConfigSection section) {
        CuttingRecipe recipe = decoder.decodeCutting(id, source, section);
        if (recipe != null) {
            pendingCutting.put(id, recipe);
        } else {
            skippedCount++;
        }
    }

    private void parseSingle(String id, String source, ConfigSection section) {
        CustomRecipe.Single recipe = decoder.decodeSingle(id, source, section);
        if (recipe != null) {
            pendingSingle.put(id, recipe);
        } else {
            skippedCount++;
        }
    }

    private void parseDecomposition(String id, String source, ConfigSection section) {
        CustomRecipe.Decomposition recipe = decoder.decodeDecomposition(id, source, section);
        if (recipe != null) {
            pendingDecomposition.put(id, recipe);
        } else {
            skippedCount++;
        }
    }

    private void parseFluidFilling(String id, String source, ConfigSection section) {
        JugFluidFillingRecipe recipe = decoder.decodeFluidFilling(id, source, section);
        if (recipe != null) {
            pendingFluidFilling.put(id, recipe);
        } else {
            skippedCount++;
        }
    }

    private void parseFluidEmptying(String id, String source, ConfigSection section) {
        JugFluidEmptyingRecipe recipe = decoder.decodeFluidEmptying(id, source, section);
        if (recipe != null) {
            pendingFluidEmptying.put(id, recipe);
        } else {
            skippedCount++;
        }
    }

    private void parseSoaking(String id, String source, ConfigSection section) {
        JugSoakingRecipe recipe = decoder.decodeSoaking(id, source, section);
        if (recipe != null) {
            pendingSoaking.put(id, recipe);
        } else {
            skippedCount++;
        }
    }

    protected void warn(String message) {
        if (warningSink != null) {
            warningSink.accept(message);
        } else {
            logger.warning(message);
        }
    }
}
