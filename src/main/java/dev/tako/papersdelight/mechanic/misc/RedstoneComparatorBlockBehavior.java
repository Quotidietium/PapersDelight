package dev.tako.papersdelight.mechanic.misc;

import net.momirealms.craftengine.bukkit.block.behavior.BukkitBlockBehavior;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.block.property.IntegerProperty;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;

import java.util.*;

public final class RedstoneComparatorBlockBehavior extends BukkitBlockBehavior {

    public static final BlockBehaviorFactory<RedstoneComparatorBlockBehavior> FACTORY = new Factory();

    public static void register() {
        BlockBehaviors.register(Key.of("papersdelight:comparator_signal"), FACTORY);
    }

    private final boolean hasComparator;
    private final String propertyName;
    private final IntegerProperty property;
    private final Map<Integer, Integer> signalMap;

    private RedstoneComparatorBlockBehavior(BlockDefinition block,
                                            boolean hasComparator,
                                            String propertyName,
                                            IntegerProperty property,
                                            Map<Integer, Integer> signalMap) {
        super(block);
        this.hasComparator = hasComparator;
        this.propertyName = propertyName;
        this.property = property;
        this.signalMap = Collections.unmodifiableMap(signalMap);
    }

    @Override
    public boolean hasAnalogOutputSignal(Object thisBlock, Object[] args) {
        return this.hasComparator;
    }

    @Override
    public int getAnalogOutputSignal(Object thisBlock, Object[] args) {
        if (!this.hasComparator || this.property == null) return 0;
        Object state = args[0];
        Optional<ImmutableBlockState> optState = BlockStateUtils.getOptionalCustomBlockState(state);
        if (optState.isEmpty()) return 0;
        ImmutableBlockState customState = optState.get();
        Object val = customState.get(this.property);
        int intVal = val instanceof Number n ? n.intValue() : 0;
        return this.signalMap.getOrDefault(intVal, 0);
    }

    private static class Factory implements BlockBehaviorFactory<RedstoneComparatorBlockBehavior> {

        private static final String[] HAS_COMPARATOR = {"has_comparator", "has-comparator"};
        private static final String[] PROPERTY     = {"property"};
        private static final String  SIGNAL_MAP    = "signal_map";

        @Override
        public RedstoneComparatorBlockBehavior create(BlockDefinition block, ConfigSection section) {
            boolean hasComparator = section.getBoolean(HAS_COMPARATOR, true);
            String propertyName = section.getString(PROPERTY, (String) null);

            IntegerProperty property = null;
            if (propertyName != null && !propertyName.isEmpty()) {

                for (Property<?> prop : block.properties()) {
                    if (prop.name().equals(propertyName) && prop instanceof IntegerProperty ip) {
                        property = ip;
                        break;
                    }
                }
            }

            Map<Integer, Integer> signalMap = new LinkedHashMap<>();
            ConfigSection mapSection = section.getSection(SIGNAL_MAP);
            if (mapSection != null) {
                for (String key : mapSection.keySet()) {
                    try {
                        int propValue = Integer.parseInt(key);
                        int signal = mapSection.getInt(key, 0);
                        signal = Math.clamp(signal, 0, 15);
                        signalMap.put(propValue, signal);
                    } catch (NumberFormatException ignored) {}
                }
            }

            return new RedstoneComparatorBlockBehavior(block, hasComparator, propertyName, property, signalMap);
        }
    }
}
