package dev.tako.papersdelight.registration;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.mechanic.function.ChorusTeleportFunction;
import dev.tako.papersdelight.mechanic.function.EndermanGristleTeleportFunction;
import dev.tako.papersdelight.mechanic.function.IsSneakingCondition;
import dev.tako.papersdelight.mechanic.function.NourishmentFunction;
import dev.tako.papersdelight.mechanic.function.RandomRemoveEffectFunction;
import dev.tako.papersdelight.mechanic.function.RemoveEffectFunction;
import dev.tako.papersdelight.mechanic.function.UpgradeEffectFunction;
import net.momirealms.craftengine.core.plugin.context.CommonConditions;
import net.momirealms.craftengine.core.plugin.context.CommonFunctions;
import net.momirealms.craftengine.core.util.Key;

import java.util.List;
import java.util.logging.Logger;

public final class CraftEngineContextRegistrations {

    private CraftEngineContextRegistrations() {}

    public enum Kind {
        FUNCTION,
        CONDITION
    }

    public record Entry(String id, Kind kind, Runnable registration) {}

    private static final String ID_REMOVE_RANDOM_EFFECT = "papersdelight:remove_random_effect";
    private static final String ID_REMOVE_EFFECT = "papersdelight:remove_effect";
    private static final String ID_IS_SNEAKING = "papersdelight:is_sneaking";
    private static final String ID_CHORUS_TELEPORT = "papersdelight:chorus_teleport";
    private static final String ID_ENDERMAN_GRISTLE = "ends_delight:enderman_gristle";
    private static final String ID_NOURISHMENT_EFFECT = "papersdelight:nourishment_effect";
    private static final String ID_UPGRADE_EFFECT = "papersdelight:upgrade_effect";

    private static final List<String> FUNCTION_IDS = List.of(
            ID_REMOVE_RANDOM_EFFECT,
            ID_REMOVE_EFFECT,
            ID_CHORUS_TELEPORT,
            ID_ENDERMAN_GRISTLE,
            ID_NOURISHMENT_EFFECT,
            ID_UPGRADE_EFFECT
    );

    private static final List<String> CONDITION_IDS = List.of(
            ID_IS_SNEAKING
    );

    private static final List<String> ORDERED_IDS = List.of(
            ID_REMOVE_RANDOM_EFFECT,
            ID_REMOVE_EFFECT,
            ID_IS_SNEAKING,
            ID_CHORUS_TELEPORT,
            ID_ENDERMAN_GRISTLE,
            ID_NOURISHMENT_EFFECT,
            ID_UPGRADE_EFFECT
    );

    public static List<String> functionIds() {
        return FUNCTION_IDS;
    }

    public static List<String> conditionIds() {
        return CONDITION_IDS;
    }

    public static List<String> orderedIds() {
        return ORDERED_IDS;
    }

    public static List<Entry> entries() {
        return EntriesHolder.ENTRIES;
    }

    private static final class EntriesHolder {
        private static final List<Entry> ENTRIES = createEntries();
    }

    private static List<Entry> createEntries() {
        return List.of(
                new Entry(ID_REMOVE_RANDOM_EFFECT, Kind.FUNCTION, () ->
                        CommonFunctions.register(
                                Key.of(ID_REMOVE_RANDOM_EFFECT),
                                RandomRemoveEffectFunction.factory(CommonConditions::fromConfig)
                        )),
                new Entry(ID_REMOVE_EFFECT, Kind.FUNCTION, () ->
                        CommonFunctions.register(
                                Key.of(ID_REMOVE_EFFECT),
                                RemoveEffectFunction.factory(CommonConditions::fromConfig)
                        )),
                new Entry(ID_IS_SNEAKING, Kind.CONDITION, () ->
                        CommonConditions.register(
                                Key.of(ID_IS_SNEAKING),
                                IsSneakingCondition.factory()
                        )),
                new Entry(ID_CHORUS_TELEPORT, Kind.FUNCTION, () ->
                        CommonFunctions.register(
                                Key.of(ID_CHORUS_TELEPORT),
                                ChorusTeleportFunction.factory(CommonConditions::fromConfig)
                        )),
                new Entry(ID_ENDERMAN_GRISTLE, Kind.FUNCTION, () ->
                        CommonFunctions.register(
                                Key.of(ID_ENDERMAN_GRISTLE),
                                EndermanGristleTeleportFunction.factory(CommonConditions::fromConfig)
                        )),
                new Entry(ID_NOURISHMENT_EFFECT, Kind.FUNCTION, () ->
                        CommonFunctions.register(
                                Key.of(ID_NOURISHMENT_EFFECT),
                                NourishmentFunction.factory(CommonConditions::fromConfig)
                        )),
                new Entry(ID_UPGRADE_EFFECT, Kind.FUNCTION, () ->
                        CommonFunctions.register(
                                Key.of(ID_UPGRADE_EFFECT),
                                UpgradeEffectFunction.factory(CommonConditions::fromConfig)
                        ))
        );
    }

    public static void registerAll(Logger logger) {
        for (Entry entry : entries()) {
            entry.registration().run();
            String template = ConfigManager.getOr("ce_reg",
                    entry.kind() == Kind.FUNCTION
                        ? "Registered CE function: %id%"
                        : "Registered CE condition: %id%");
            logger.info(template.replace("%id%", entry.id()));
        }
    }
}
