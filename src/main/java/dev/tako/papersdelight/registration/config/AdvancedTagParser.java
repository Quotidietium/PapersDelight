package dev.tako.papersdelight.registration.config;

import dev.tako.papersdelight.registration.config.GenerationAwareIdSectionConfigParser;
import dev.tako.papersdelight.registration.PapersDelightLoadingStages;
import dev.tako.papersdelight.registration.PapersDelightParserTypes;
import dev.tako.papersdelight.registration.config.ParserGeneration;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ResourceException;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStage;
import net.momirealms.craftengine.core.plugin.config.lifecycle.LoadingStages;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.UniqueKey;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class AdvancedTagParser extends GenerationAwareIdSectionConfigParser {

    private static final PendingPublication UNPUBLISHED =
            new PendingPublication(AdvancedTagSnapshot.empty(), 0L);
    private static final Object PUBLICATION_LOCK = new Object();
    private static final AtomicReference<AdvancedTagSnapshot> ACTIVE_SNAPSHOT =
            new AtomicReference<>(AdvancedTagSnapshot.empty());
    private static final AtomicReference<PendingPublication> PENDING_PUBLICATION =
            new AtomicReference<>(UNPUBLISHED);
    private static final AtomicLong NEXT_PUBLICATION_EPOCH = new AtomicLong();

    private final AdvancedTagDefinitions definitions = new AdvancedTagDefinitions();
    private boolean fatalError;

    public AdvancedTagParser() {
        this(ParserGeneration.active());
    }

    public AdvancedTagParser(ParserGeneration generation) {
        super(generation);
    }

    AdvancedTagParser(ParserGeneration generation, Runnable beforeCommit) {
        super(generation, beforeCommit);
    }

    public static AdvancedTagSnapshot snapshot() {
        return activeSnapshot();
    }

    public static AdvancedTagSnapshot activeSnapshot() {
        return ACTIVE_SNAPSHOT.get();
    }

    public static void restoreActiveSnapshot(AdvancedTagSnapshot snapshot) {
        synchronized (PUBLICATION_LOCK) {
            ACTIVE_SNAPSHOT.set(snapshot == null ? AdvancedTagSnapshot.empty() : snapshot);
        }
    }

    public static AdvancedTagSnapshot pendingSnapshot() {
        return PENDING_PUBLICATION.get().snapshot();
    }

    public static List<Key> resolve(Key id) {
        return activeSnapshot().resolve(id);
    }

    public static List<String> resolve(String id) {
        return activeSnapshot().resolve(id);
    }

    public static boolean hasSuccessfulPublication() {
        return publicationEpoch() > 0L;
    }

    public static long publicationEpoch() {
        return PENDING_PUBLICATION.get().epoch();
    }

    public static boolean commitPending(AdvancedTagSnapshot snapshot, long epoch) {
        synchronized (PUBLICATION_LOCK) {
            PendingPublication pending = PENDING_PUBLICATION.get();
            if (!pending.matches(snapshot, epoch)) return false;
            ACTIVE_SNAPSHOT.set(pending.snapshot());
            PENDING_PUBLICATION.set(UNPUBLISHED);
            return true;
        }
    }

    public static boolean discardPending(AdvancedTagSnapshot snapshot, long epoch) {
        synchronized (PUBLICATION_LOCK) {
            PendingPublication pending = PENDING_PUBLICATION.get();
            if (!pending.matches(snapshot, epoch)) return false;
            PENDING_PUBLICATION.set(UNPUBLISHED);
            return true;
        }
    }

    public static void resetSnapshot() {
        synchronized (PUBLICATION_LOCK) {
            ACTIVE_SNAPSHOT.set(AdvancedTagSnapshot.empty());
            PENDING_PUBLICATION.set(UNPUBLISHED);
            NEXT_PUBLICATION_EPOCH.set(0L);
        }
    }

    @Override
    public Key type() {
        return PapersDelightParserTypes.ADVANCED_TAG;
    }

    @Override
    public String[] sectionId() {
        return new String[] {"advanced_tags"};
    }

    @Override
    public LoadingStage loadingStage() {
        return PapersDelightLoadingStages.ADVANCED_TAG;
    }

    @Override
    public List<LoadingStage> dependencies() {
        return List.of(LoadingStages.ITEM);
    }

    @Override
    public void setErrorHandler(Consumer<ResourceException> errorHandler) {
        Consumer<ResourceException> delegate = Objects.requireNonNull(errorHandler, "errorHandler");
        super.setErrorHandler(error -> {
            if (!generationActive()) return;
            fatalError = true;
            delegate.accept(error);
        });
    }

    @Override
    public void preProcess() {
        this.definitions.reset();
        this.fatalError = false;
        runIfGenerationActive(() -> {
            synchronized (PUBLICATION_LOCK) {
                PENDING_PUBLICATION.set(UNPUBLISHED);
            }
        });
    }

    @Override
    public void postProcess() {
        boolean committed = commitIfGenerationActive(() -> {
            if (fatalError) {
                warn("CraftEngine advanced tag parsing failed; keeping the previous successful snapshot");
                return;
            }
            AdvancedTagSnapshot candidate = this.definitions.compile(createLookup(), this::warn);
            synchronized (PUBLICATION_LOCK) {
                long epoch = NEXT_PUBLICATION_EPOCH.incrementAndGet();
                PENDING_PUBLICATION.set(new PendingPublication(candidate, epoch));
            }
        });
        if (!committed) this.definitions.reset();
    }

    @Override
    protected boolean checkDuplicated() {
        return false;
    }

    @Override
    public void clearConfigs() {
        super.clearConfigs();
        this.definitions.reset();
        this.fatalError = false;
    }

    @Override
    protected void parseSection(@NotNull Pack pack, @NotNull Path path, @NotNull Key id, @NotNull ConfigSection section) {
        if (!generationActive()) return;
        this.definitions.collect(pack, path, id, section, this::warn);
    }

    protected AdvancedTagDefinitions.Lookup createLookup() {
        ItemManager itemManager = CraftEngine.instance().itemManager();
        return new AdvancedTagDefinitions.Lookup() {
            @Override
            public boolean itemExists(Key item) {
                return itemManager.getBuildableItem(item).isPresent();
            }

            @Override
            public List<Key> itemsByTag(Key tag) {
                return itemManager.itemIdsByTag(tag).stream().map(UniqueKey::key).toList();
            }
        };
    }

    protected void warn(String message) {
        CraftEngine.instance().logger().warn(message);
    }

    private record PendingPublication(AdvancedTagSnapshot snapshot, long epoch) {
        private boolean matches(AdvancedTagSnapshot expectedSnapshot, long expectedEpoch) {
            return epoch > 0L && epoch == expectedEpoch && snapshot == expectedSnapshot;
        }
    }
}
