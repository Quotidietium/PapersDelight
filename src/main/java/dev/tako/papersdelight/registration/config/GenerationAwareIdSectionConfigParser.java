package dev.tako.papersdelight.registration.config;

import net.momirealms.craftengine.core.pack.CachedConfigSection;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.IdSectionConfigParser;

import java.util.Objects;


public abstract class GenerationAwareIdSectionConfigParser extends IdSectionConfigParser {
    private volatile ParserGeneration generation;
    private final Runnable beforeCommit;

    protected GenerationAwareIdSectionConfigParser(ParserGeneration generation) {
        this(generation, () -> { });
    }


    protected GenerationAwareIdSectionConfigParser(ParserGeneration generation, Runnable beforeCommit) {
        this.generation = Objects.requireNonNull(generation, "generation");
        this.beforeCommit = Objects.requireNonNull(beforeCommit, "beforeCommit");
    }


    public final void rebindGeneration(ParserGeneration next) {
        Objects.requireNonNull(next, "next");
        ParserGeneration.runExclusive(() -> this.generation = next);
    }


    public final boolean generationActive() {
        return generation.isActive();
    }


    public final boolean runIfGenerationActive(Runnable mutation) {
        return generation.commitIfActive(mutation);
    }

    public final boolean commitIfGenerationActive(Runnable commit) {
        return runIfGenerationActive(() -> {
            beforeCommit.run();
            commit.run();
        });
    }

    @Override
    public void addConfig(CachedConfigSection section) {
        if (generationActive()) super.addConfig(section);
    }

    @Override
    public synchronized void addPendingConfigSection(PendingConfigSection section) {
        if (generationActive()) super.addPendingConfigSection(section);
    }

    @Override
    public void loadAll() {
        if (generationActive()) super.loadAll();
    }

    @Override
    public void clearConfigs() {


        this.configStorage.clear();
        this.pendingConfigSections.clear();
        if (checkDuplicated()) clearIdToPath();
    }
}
