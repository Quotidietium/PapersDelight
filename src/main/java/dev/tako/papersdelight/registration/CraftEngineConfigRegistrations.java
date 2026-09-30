package dev.tako.papersdelight.registration;

import dev.tako.papersdelight.registration.config.ParserGeneration;
import dev.tako.papersdelight.registration.config.AdvancedTagParser;
import dev.tako.papersdelight.registration.config.GenerationAwareIdSectionConfigParser;
import dev.tako.papersdelight.registration.config.PapersDelightRecipeParser;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.core.plugin.config.ConfigParser;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CraftEngineConfigRegistrations {

    private static final String SECTION_ADVANCED_TAGS = "advanced_tags";
    private static final String SECTION_RECIPES = "papersdelight_recipes";
    private static final List<String> SECTION_IDS = List.of(
            SECTION_ADVANCED_TAGS,
            SECTION_RECIPES
    );

    private static final Map<String, GenerationAwareIdSectionConfigParser> PARSER_POOL = new LinkedHashMap<>();
    private static final Map<String, ConfigParser> REGISTERED_PARSERS = new LinkedHashMap<>();
    private static ParserLifecycle lifecycle;
    private static ParserGeneration activeGeneration;
    private static boolean upstreamRegistered;

    private CraftEngineConfigRegistrations() {
    }

    public enum RegistrationOutcome {
        CRAFTENGINE_NOT_INSTALLED,
        PACK_MANAGER_NOT_READY,
        REGISTERED,
        REGISTRATION_FAILED,
        ALREADY_REGISTERED
    }

    @FunctionalInterface
    interface ParserRegistrar {
        boolean register(ConfigParser parser);
    }

    interface ParserLifecycle {
        boolean register(ConfigParser parser);

        boolean unregister(String sectionId);
    }

    public static List<String> sectionIds() {
        return SECTION_IDS;
    }

    private static Map<String, GenerationAwareIdSectionConfigParser> ensurePool(
            Plugin plugin, ParserGeneration generation) {
        if (PARSER_POOL.isEmpty()) {
            PARSER_POOL.put(SECTION_ADVANCED_TAGS, new AdvancedTagParser(generation));
            PARSER_POOL.put(SECTION_RECIPES, new PapersDelightRecipeParser(plugin, generation));
        }
        return PARSER_POOL;
    }

    static synchronized ConfigParser residentParser(String sectionId) {
        return PARSER_POOL.get(sectionId);
    }

    public static Map<String, RegistrationOutcome> registerAll(Plugin plugin) {
        if (!CraftEngineUtil.isCraftEngineInstalled(plugin)) {
            return uniformOutcomes(RegistrationOutcome.CRAFTENGINE_NOT_INSTALLED);
        }

        var packManager = CraftEngineUtil.getPackManagerIfReady(plugin);
        if (packManager == null) {
            return uniformOutcomes(RegistrationOutcome.PACK_MANAGER_NOT_READY);
        }

        ParserLifecycle packLifecycle = new ParserLifecycle() {
            @Override
            public boolean register(ConfigParser parser) {
                return packManager.registerConfigSectionParser(parser);
            }

            @Override
            public boolean unregister(String sectionId) {
                return packManager.unregisterConfigSectionParser(sectionId);
            }
        };
        return registerAll(plugin, packLifecycle);
    }

    static Map<String, RegistrationOutcome> registerAll(Plugin plugin, ParserRegistrar registrar) {
        return registerAll(plugin, new ParserLifecycle() {
            @Override
            public boolean register(ConfigParser parser) {
                return registrar.register(parser);
            }

            @Override
            public boolean unregister(String sectionId) {
                return false;
            }
        });
    }

    static synchronized Map<String, RegistrationOutcome> registerAll(Plugin plugin, ParserLifecycle registrar) {
        Logger logger = plugin.getLogger();
        if (isFullyRegistered()) {
            return uniformOutcomes(RegistrationOutcome.ALREADY_REGISTERED);
        }
        if (!REGISTERED_PARSERS.isEmpty() || activeGeneration != null) {
            logger.severe("Refusing parser registration because an incomplete local generation is still present");
            return uniformOutcomes(RegistrationOutcome.REGISTRATION_FAILED);
        }

        ParserGeneration candidateGeneration = ParserGeneration.candidate();
        Map<String, GenerationAwareIdSectionConfigParser> pool = ensurePool(plugin, candidateGeneration);
        for (GenerationAwareIdSectionConfigParser parser : pool.values()) {
            parser.rebindGeneration(candidateGeneration);
        }

        if (!upstreamRegistered) {
            List<String> registeredSections = new ArrayList<>();
            try {
                for (Map.Entry<String, GenerationAwareIdSectionConfigParser> entry : pool.entrySet()) {
                    if (!registrar.register(entry.getValue())) {
                        logger.warning("Config parser transaction failed at section: " + entry.getKey());
                        rollbackCandidate(logger, registrar, candidateGeneration, pool, registeredSections);
                        return uniformOutcomes(RegistrationOutcome.REGISTRATION_FAILED);
                    }
                    registeredSections.add(entry.getKey());
                }
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Config parser transaction threw while registering", throwable);
                rollbackCandidate(logger, registrar, candidateGeneration, pool, registeredSections);
                return uniformOutcomes(RegistrationOutcome.REGISTRATION_FAILED);
            }
            upstreamRegistered = true;
        } else {
            logger.info("Reusing parsers already present in the CraftEngine parser registry; "
                    + "only the generation is rotated");
        }

        candidateGeneration.activate();
        activeGeneration = candidateGeneration;
        lifecycle = registrar;
        REGISTERED_PARSERS.putAll(pool);
        for (String sectionId : SECTION_IDS) {
            logger.info("Registered config parser: " + sectionId + " (generation "
                    + candidateGeneration.id() + ")");
        }
        return uniformOutcomes(RegistrationOutcome.REGISTERED);
    }

    static synchronized RegistrationOutcome registerAdvancedTagParser(Logger logger, ParserRegistrar registrar) {
        if (REGISTERED_PARSERS.containsKey(SECTION_ADVANCED_TAGS)) {
            return RegistrationOutcome.ALREADY_REGISTERED;
        }
        ParserGeneration candidate = ParserGeneration.candidate();
        GenerationAwareIdSectionConfigParser parser = PARSER_POOL.computeIfAbsent(
                SECTION_ADVANCED_TAGS, ignored -> new AdvancedTagParser(candidate));
        parser.rebindGeneration(candidate);
        try {
            if (!registrar.register(parser)) {
                candidate.invalidate();
                parser.clearConfigs();
                return RegistrationOutcome.REGISTRATION_FAILED;
            }
        } catch (Throwable throwable) {
            candidate.invalidate();
            parser.clearConfigs();
            logger.log(Level.SEVERE, "Advanced tag parser registration threw", throwable);
            return RegistrationOutcome.REGISTRATION_FAILED;
        }
        candidate.activate();
        activeGeneration = candidate;
        REGISTERED_PARSERS.put(SECTION_ADVANCED_TAGS, parser);
        lifecycle = new ParserLifecycle() {
            @Override
            public boolean register(ConfigParser ignored) {
                return registrar.register(ignored);
            }

            @Override
            public boolean unregister(String sectionId) {
                return false;
            }
        };
        return RegistrationOutcome.REGISTERED;
    }

    public static synchronized boolean unregisterAll(Logger logger) {
        ParserGeneration generation = activeGeneration;

        invalidateAndResetSnapshots(generation);

        for (String sectionId : SECTION_IDS) {
            ConfigParser parser = REGISTERED_PARSERS.get(sectionId);
            if (parser == null) continue;
            parser.clearConfigs();
            logger.info("Deactivated config parser section: " + sectionId
                    + "; the parser object stays in the CraftEngine registry by design");
        }

        clearLocalRegistrationState();
        return true;
    }

    static synchronized void resetRegistrationState() {
        invalidateAndResetSnapshots(activeGeneration);
        for (ConfigParser parser : REGISTERED_PARSERS.values()) parser.clearConfigs();
        clearLocalRegistrationState();

        PARSER_POOL.clear();
        upstreamRegistered = false;
    }

    private static void rollbackCandidate(
            Logger logger,
            ParserLifecycle registrar,
            ParserGeneration candidateGeneration,
            Map<String, ? extends ConfigParser> candidates,
            List<String> registeredSections
    ) {
        candidateGeneration.invalidate();
        for (int i = registeredSections.size() - 1; i >= 0; i--) {
            String sectionId = registeredSections.get(i);
            try {
                if (!registrar.unregister(sectionId)) {
                    logger.warning("Failed to roll back config parser section map: " + sectionId
                            + "; inactive generation prevents retained parser callbacks from publishing");
                }
            } catch (Throwable throwable) {
                logger.log(Level.WARNING, "Exception while rolling back config parser section: " + sectionId,
                        throwable);
            }
        }
        for (ConfigParser parser : candidates.values()) parser.clearConfigs();
    }

    private static boolean isFullyRegistered() {
        return activeGeneration != null && activeGeneration.isActive()
                && REGISTERED_PARSERS.keySet().containsAll(SECTION_IDS)
                && REGISTERED_PARSERS.size() == SECTION_IDS.size();
    }

    private static void clearLocalRegistrationState() {
        REGISTERED_PARSERS.clear();
        lifecycle = null;
        activeGeneration = null;
    }

    private static void invalidateAndResetSnapshots(ParserGeneration generation) {
        ParserGeneration.runExclusive(() -> {
            if (generation != null) generation.invalidate();
            resetSnapshots();
        });
    }

    private static void resetSnapshots() {
        AdvancedTagParser.resetSnapshot();
        PapersDelightRecipeParser.resetSnapshot();
    }

    private static Map<String, RegistrationOutcome> uniformOutcomes(RegistrationOutcome outcome) {
        LinkedHashMap<String, RegistrationOutcome> outcomes = new LinkedHashMap<>();
        for (String sectionId : SECTION_IDS) outcomes.put(sectionId, outcome);
        return Collections.unmodifiableMap(outcomes);
    }
}
