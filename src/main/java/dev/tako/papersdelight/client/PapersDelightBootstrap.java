package dev.tako.papersdelight.client;

import dev.tako.papersdelight.api.damage.DamageTypeDefinition;
import dev.tako.papersdelight.damage.DamageTypes;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import net.kyori.adventure.key.Key;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageScaling;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PapersDelightBootstrap implements PluginBootstrap {

    private static final Logger LOGGER = Logger.getLogger("PapersDelight");

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        registerStoveBurn(context);
    }

    private static void registerStoveBurn(@NotNull BootstrapContext context) {
        try {
            Set<Key> tags = new LinkedHashSet<>();
            for (String tag : StoveBurnDamageTypes.STOVE_BURN_TAG_KEYS) {
                tags.add(Key.key(tag));
            }

            DamageTypes.register(
                    context,
                    DamageTypeDefinition.builder(Key.key(StoveBurnDamageTypes.STOVE_BURN_KEY))
                            .fallback(Key.key(StoveBurnDamageTypes.STOVE_BURN_FALLBACK_KEY))
                            .messageId(StoveBurnDamageTypes.STOVE_BURN_MESSAGE_ID)
                            .exhaustion(StoveBurnDamageTypes.STOVE_BURN_EXHAUSTION)
                            .scaling(DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER)
                            .effect(DamageEffect.BURNING)
                            .tags(tags)
                            .build());
        } catch (Throwable t) {
            LOGGER.log(
                    Level.WARNING,
                    "注册伤害类型 " + StoveBurnDamageTypes.STOVE_BURN_KEY
                            + " 失败，将回退到 " + StoveBurnDamageTypes.STOVE_BURN_FALLBACK_KEY,
                    t);
        }
    }
}
