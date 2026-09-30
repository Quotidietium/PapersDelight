package dev.tako.papersdelight.mechanic.villager;

import net.momirealms.craftengine.core.item.ItemDefinition;
import net.momirealms.craftengine.core.item.setting.CustomItemSettingType;
import net.momirealms.craftengine.core.item.setting.ItemSettingsModifier;
import net.momirealms.craftengine.core.item.setting.ItemSettingsModifiers;
import net.momirealms.craftengine.core.util.Key;

public final class VillagerFoodPointSetting {

    private VillagerFoodPointSetting() {
    }

    public static final Key SETTING_KEY = Key.of("papersdelight:villager_food_point");

    public static final CustomItemSettingType<Integer> TYPE = CustomItemSettingType.simple();

    public static void register() {
        ItemSettingsModifiers.register(SETTING_KEY, VillagerFoodPointSetting::create);
    }

    private static ItemSettingsModifier create(net.momirealms.craftengine.core.plugin.config.ConfigValue value) {
        int points = Math.max(0, value.getAsInt());
        return settings -> settings.addCustomData(TYPE, points);
    }

    public static int foodPoint(ItemDefinition def) {
        if (def == null) return 0;
        Integer v = def.settings().getCustomData(TYPE);
        return v == null ? 0 : v;
    }
}
