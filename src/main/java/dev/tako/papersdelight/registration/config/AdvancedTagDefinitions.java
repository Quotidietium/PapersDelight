package dev.tako.papersdelight.registration.config;

import net.momirealms.craftengine.core.pack.Identifier;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.util.Key;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class AdvancedTagDefinitions {

    public interface Lookup {
        boolean itemExists(Key item);

        List<Key> itemsByTag(Key tag);
    }

    private final LinkedHashMap<Key, PendingDefinition> definitions = new LinkedHashMap<>();

    public void reset() {
        this.definitions.clear();
    }

    public void collect(Pack pack, Path sourcePath, Key id, ConfigSection section, Consumer<String> warningConsumer) {
        Key normalizedId = normalizeIdentifier(id.asString(), pack.namespace());
        if (normalizedId == null) {
            warn(warningConsumer, sourcePath, section.path(), dev.tako.papersdelight.config.ConfigManager.getOr(
                    "advanced_tag_id_invalid", "高级标签 ID 非法：%id%").replace("%id%", id.asString()));
            return;
        }

        PendingDefinition definition = this.definitions.computeIfAbsent(normalizedId, ignored -> new PendingDefinition());
        if (readReplace(section, sourcePath, warningConsumer)) {
            definition.clear();
        }
        definition.markSource(sourcePath, section.path());

        ConfigValue values = section.getValue("values");
        if (values == null) {
            warn(warningConsumer, sourcePath, section.assemblePath("values"), dev.tako.papersdelight.config.ConfigManager.getOr(
                    "advanced_tag_values_missing", "缺少 values，已保留为空高级标签"));
            return;
        }

        for (ConfigValue value : values.getAsValueList()) {
            RawMember member = parseMember(value, pack.namespace(), sourcePath, warningConsumer);
            if (member != null) {
                definition.add(member);
            }
        }
    }

    public AdvancedTagSnapshot compile(Lookup lookup, Consumer<String> warningConsumer) {
        if (this.definitions.isEmpty()) {
            return AdvancedTagSnapshot.empty();
        }

        ResolutionContext context = new ResolutionContext(lookup, warningConsumer);
        LinkedHashMap<Key, List<Key>> resolved = new LinkedHashMap<>(this.definitions.size());
        for (Key id : this.definitions.keySet()) {
            List<Key> items = context.resolve(id);
            if (items.isEmpty()) {
                PendingDefinition definition = this.definitions.get(id);
                String node = definition == null || definition.firstNode == null ? id.asString() : definition.firstNode;
                Path source = definition == null || definition.firstSource == null ? Path.of(id.asString()) : definition.firstSource;
                warn(warningConsumer, source, node, dev.tako.papersdelight.config.ConfigManager.getOr(
                        "advanced_tag_empty", "高级标签最终为空：%id%").replace("%id%", id.asString()));
            }
            resolved.put(id, items);
        }
        return new AdvancedTagSnapshot(resolved);
    }

    private boolean readReplace(ConfigSection section, Path sourcePath, Consumer<String> warningConsumer) {
        ConfigValue replace = section.getValue("replace");
        if (replace == null) {
            return false;
        }
        try {
            return replace.getAsBoolean();
        } catch (RuntimeException ex) {
            warn(warningConsumer, sourcePath, replace.path(), dev.tako.papersdelight.config.ConfigManager.getOr(
                    "advanced_tag_replace_invalid", "replace 不是合法布尔值，已按 false 处理：%value%").replace("%value%", String.valueOf(replace.value())));
            return false;
        }
    }

    private RawMember parseMember(
            ConfigValue value,
            String defaultNamespace,
            Path sourcePath,
            Consumer<String> warningConsumer
    ) {
        String raw = value.getAsString().trim();
        if (raw.isEmpty()) {
            warn(warningConsumer, sourcePath, value.path(), dev.tako.papersdelight.config.ConfigManager.getOr(
                    "advanced_tag_member_empty", "空成员已忽略"));
            return null;
        }

        if (raw.startsWith("advtag:")) {
            Key target = normalizeIdentifier(raw.substring("advtag:".length()), defaultNamespace);
            if (target == null) {
                warn(warningConsumer, sourcePath, value.path(), dev.tako.papersdelight.config.ConfigManager.getOr(
                        "advanced_tag_reference_invalid", "非法高级标签引用：%value%").replace("%value%", raw));
                return null;
            }
            return new RawMember(MemberType.ADVANCED_TAG, target, raw, sourcePath, value.path());
        }
        if (raw.startsWith("#")) {
            Key target = normalizeIdentifier(raw.substring(1), defaultNamespace);
            if (target == null) {
                warn(warningConsumer, sourcePath, value.path(), dev.tako.papersdelight.config.ConfigManager.getOr(
                        "advanced_tag_normal_reference_invalid", "非法普通标签引用：%value%").replace("%value%", raw));
                return null;
            }
            return new RawMember(MemberType.NORMAL_TAG, target, raw, sourcePath, value.path());
        }

        Key target = normalizeIdentifier(raw, defaultNamespace);
        if (target == null) {
            warn(warningConsumer, sourcePath, value.path(), dev.tako.papersdelight.config.ConfigManager.getOr(
                    "advanced_tag_item_id_invalid", "非法物品 ID：%value%").replace("%value%", raw));
            return null;
        }
        return new RawMember(MemberType.ITEM, target, raw, sourcePath, value.path());
    }

    private static Key normalizeIdentifier(String raw, String defaultNamespace) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!Identifier.isValid(normalized)) {
            return null;
        }
        return Key.withDefaultNamespace(normalized, defaultNamespace);
    }

    private static void warn(Consumer<String> warningConsumer, Path sourcePath, String node, String message) {
        warningConsumer.accept(sourcePath + " @ " + node + ": " + message);
    }

    private enum MemberType {
        ITEM,
        NORMAL_TAG,
        ADVANCED_TAG
    }

    private record RawMember(MemberType type, Key target, String raw, Path sourcePath, String node) {
        private String canonical() {
            return switch (type) {
                case ITEM -> target.asString();
                case NORMAL_TAG -> "#" + target.asString();
                case ADVANCED_TAG -> "advtag:" + target.asString();
            };
        }
    }

    private static final class PendingDefinition {
        private final LinkedHashMap<String, RawMember> members = new LinkedHashMap<>();
        private Path firstSource;
        private String firstNode;

        private void clear() {
            this.members.clear();
            this.firstSource = null;
            this.firstNode = null;
        }

        private void markSource(Path source, String node) {
            if (this.firstSource == null) {
                this.firstSource = source;
            }
            if (this.firstNode == null) {
                this.firstNode = node;
            }
        }

        private void add(RawMember member) {
            this.members.putIfAbsent(member.canonical(), member);
            markSource(member.sourcePath(), member.node());
        }

        private List<RawMember> orderedMembers() {
            return List.copyOf(this.members.values());
        }
    }

    private final class ResolutionContext {
        private final Lookup lookup;
        private final Consumer<String> warnings;
        private final Map<Key, ResolutionState> states = new LinkedHashMap<>();
        private final Map<Key, List<Key>> cache = new LinkedHashMap<>();
        private final Deque<Key> stack = new ArrayDeque<>();

        private ResolutionContext(Lookup lookup, Consumer<String> warnings) {
            this.lookup = lookup;
            this.warnings = warnings;
        }

        private List<Key> resolve(Key id) {
            List<Key> cached = this.cache.get(id);
            if (cached != null) {
                return cached;
            }

            PendingDefinition definition = definitions.get(id);
            if (definition == null) {
                return List.of();
            }

            this.states.put(id, ResolutionState.RESOLVING);
            this.stack.addLast(id);
            LinkedHashSet<Key> resolved = new LinkedHashSet<>();
            try {
                for (RawMember member : definition.orderedMembers()) {
                    switch (member.type()) {
                        case ITEM -> resolveItem(member, resolved);
                        case NORMAL_TAG -> resolveNormalTag(member, resolved);
                        case ADVANCED_TAG -> resolveAdvancedTag(member, resolved);
                    }
                }
            } finally {
                this.stack.removeLast();
                this.states.put(id, ResolutionState.RESOLVED);
            }

            List<Key> result = List.copyOf(resolved);
            this.cache.put(id, result);
            return result;
        }

        private void resolveItem(RawMember member, Set<Key> resolved) {
            if (!this.lookup.itemExists(member.target())) {
                warn(this.warnings, member.sourcePath(), member.node(), dev.tako.papersdelight.config.ConfigManager.getOr(
                        "advanced_tag_item_unknown", "未知物品已忽略：%id%").replace("%id%", member.target().asString()));
                return;
            }
            resolved.add(member.target());
        }

        private void resolveNormalTag(RawMember member, Set<Key> resolved) {
            List<Key> items = this.lookup.itemsByTag(member.target());
            if (items.isEmpty()) {
                warn(this.warnings, member.sourcePath(), member.node(), dev.tako.papersdelight.config.ConfigManager.getOr(
                        "advanced_tag_normal_tag_empty", "普通标签为空或不存在，已忽略：#%id%").replace("%id%", member.target().asString()));
                return;
            }
            resolved.addAll(items);
        }

        private void resolveAdvancedTag(RawMember member, Set<Key> resolved) {
            if (!definitions.containsKey(member.target())) {
                warn(this.warnings, member.sourcePath(), member.node(), dev.tako.papersdelight.config.ConfigManager.getOr(
                        "advanced_tag_reference_unknown", "未知高级标签已忽略：advtag:%id%").replace("%id%", member.target().asString()));
                return;
            }

            if (this.states.get(member.target()) == ResolutionState.RESOLVING) {
                List<Key> chain = new ArrayList<>(this.stack);
                chain.add(member.target());
                String chainText = chain.stream().map(Key::asString).reduce((left, right) -> left + " -> " + right).orElse(member.target().asString());
                warn(this.warnings, member.sourcePath(), member.node(),
                        dev.tako.papersdelight.config.ConfigManager.getOr(
                                "advanced_tag_cycle_detected", "检测到高级标签循环引用，已忽略该边：%chain%").replace("%chain%", chainText));
                return;
            }

            resolved.addAll(resolve(member.target()));
        }
    }

    private enum ResolutionState {
        RESOLVING,
        RESOLVED
    }
}
