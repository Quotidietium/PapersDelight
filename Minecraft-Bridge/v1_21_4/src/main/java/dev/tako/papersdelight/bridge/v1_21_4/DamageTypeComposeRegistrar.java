package dev.tako.papersdelight.bridge.v1_21_4;

import dev.tako.papersdelight.api.damage.DamageTypeDefinition;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.DamageTypeRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.event.WritableRegistry;
import io.papermc.paper.registry.tag.TagKey;
import io.papermc.paper.tag.PostFlattenTagRegistrar;
import net.kyori.adventure.key.Key;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DamageTypeComposeRegistrar {

    private static final Logger LOGGER = Logger.getLogger("PapersDelight-DamageTypes");

    private DamageTypeComposeRegistrar() {
        throw new UnsupportedOperationException("DamageTypeComposeRegistrar is a utility class");
    }

    public static void register(@NotNull BootstrapContext context, @NotNull DamageTypeDefinition definition) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(definition, "definition");
        LifecycleEventManager<BootstrapContext> manager = context.getLifecycleManager();
        TypedKey<DamageType> typedKey = TypedKey.create(RegistryKey.DAMAGE_TYPE, definition.key());

        manager.registerEventHandler(RegistryEvents.DAMAGE_TYPE.freeze(), event ->
                runSafely("注册伤害类型 " + definition.key().asString(), () -> {
                    WritableRegistry<DamageType, DamageTypeRegistryEntry.Builder> registry = event.registry();
                    registry.register(typedKey, builder -> {
                        builder.messageId(definition.messageId());
                        builder.exhaustion(definition.exhaustion());
                        builder.damageScaling(definition.scaling());
                        DamageEffect effect = definition.effect();
                        if (effect != null) {
                            builder.damageEffect(effect);
                        }
                        builder.deathMessageType(definition.deathMessageType());
                    });
                }));

        Set<Key> tags = definition.tags();
        if (tags.isEmpty()) {
            return;
        }

        manager.registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DAMAGE_TYPE), event ->
                runSafely("注入伤害类型标签 " + definition.key().asString(), () -> {
                    PostFlattenTagRegistrar<DamageType> registrar = event.registrar();
                    for (Key tagKeyId : tags) {
                        TagKey<DamageType> tagKey = TagKey.create(RegistryKey.DAMAGE_TYPE, tagKeyId);
                        if (registrar.hasTag(tagKey)) {
                            registrar.addToTag(tagKey, Set.of(typedKey));
                        }
                    }
                }));
    }

    static void runSafely(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            LOGGER.log(Level.SEVERE, operation + " 失败，已跳过并保留注册表现有内容", error);
        }
    }
}
