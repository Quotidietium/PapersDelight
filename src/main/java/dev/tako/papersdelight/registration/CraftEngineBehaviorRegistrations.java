package dev.tako.papersdelight.registration;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.cookingpot.CookingPotBlockBehavior;
import dev.tako.papersdelight.jug.JugBlockBehavior;
import dev.tako.papersdelight.jug.JugItemBehavior;
import dev.tako.papersdelight.mechanic.basket.BasketBlockBehavior;
import dev.tako.papersdelight.mechanic.cutting.CuttingBoardBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.AdvancedCropBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.DoubleCropBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.FarmlandBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.OrganicCompostBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.RichSoilBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.RopedCropBlockBehavior;
import dev.tako.papersdelight.mechanic.farm.WildRiceBlockBehavior;
import dev.tako.papersdelight.mechanic.misc.RedstoneComparatorBlockBehavior;
import dev.tako.papersdelight.mechanic.misc.RopeBlockBehavior;
import dev.tako.papersdelight.mechanic.misc.RopeItemBehavior;
import dev.tako.papersdelight.mechanic.skillet.SkilletBlockBehavior;
import dev.tako.papersdelight.mechanic.skillet.SkilletItemBehavior;
import dev.tako.papersdelight.mechanic.skewer.HandheldSkewerBehavior;
import dev.tako.papersdelight.mechanic.stove.HighTemperatureBlockBehavior;
import dev.tako.papersdelight.mechanic.stove.StoveBlockBehavior;
import dev.tako.papersdelight.mechanic.villager.VillagerFoodPointSetting;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CraftEngineBehaviorRegistrations {

    private CraftEngineBehaviorRegistrations() {}

    public record Entry(String id, Runnable registration) {}

    private static final String ID_ADVANCED_CROP = "papersdelight:advanced_crop";
    private static final String ID_ROPED_CROP = "papersdelight:roped_crop";
    private static final String ID_ORGANIC_COMPOST = "papersdelight:organic_compost";
    private static final String ID_RICH_SOIL = "papersdelight:rich_soil";
    private static final String ID_FARMLAND = "papersdelight:farmland";
    private static final String ID_WILD_RICE = "papersdelight:wild_rice";
    private static final String ID_DOUBLE_CROP = "papersdelight:double_crop";
    private static final String ID_HIGH_TEMPERATURE = "papersdelight:high_temperature";
    private static final String ID_ROPE_BLOCK = "papersdelight:rope_block";
    private static final String ID_ROPE = "papersdelight:rope";
    private static final String ID_CUTTING_BOARD = "papersdelight:cutting_board";
    private static final String ID_SKILLET = "papersdelight:skillet";
    private static final String ID_SKILLET_ITEM = "papersdelight:skillet_item";
    private static final String ID_SKEWER_ITEM = "papersdelight:skewer_item";
    private static final String ID_STOVE = "papersdelight:stove";
    private static final String ID_BASKET = "papersdelight:basket";
    private static final String ID_COMPARATOR_SIGNAL = "papersdelight:comparator_signal";
    private static final String ID_COOKING_POT = "papersdelight:cooking_pot";
    private static final String ID_JUG = "papersdelight:jug";
    private static final String ID_JUG_ITEM = "papersdelight:jug_item";

    private static final String ID_VILLAGER_FOOD_POINT = "papersdelight:villager_food_point";

    private static final List<String> IDS = List.of(
            ID_ADVANCED_CROP,
            ID_ROPED_CROP,
            ID_ORGANIC_COMPOST,
            ID_RICH_SOIL,
            ID_FARMLAND,
            ID_WILD_RICE,
            ID_DOUBLE_CROP,
            ID_HIGH_TEMPERATURE,
            ID_ROPE_BLOCK,
            ID_ROPE,
            ID_CUTTING_BOARD,
            ID_SKILLET,
            ID_SKILLET_ITEM,
            ID_SKEWER_ITEM,
            ID_STOVE,
            ID_BASKET,
            ID_COMPARATOR_SIGNAL,
            ID_COOKING_POT,
            ID_JUG,
            ID_JUG_ITEM,
            ID_VILLAGER_FOOD_POINT
    );

    public static List<String> ids() {
        return IDS;
    }

    public static List<Entry> entries() {
        return List.of(
                new Entry(ID_ADVANCED_CROP, AdvancedCropBlockBehavior::register),
                new Entry(ID_ROPED_CROP, RopedCropBlockBehavior::register),
                new Entry(ID_ORGANIC_COMPOST, OrganicCompostBlockBehavior::register),
                new Entry(ID_RICH_SOIL, RichSoilBlockBehavior::register),
                new Entry(ID_FARMLAND, FarmlandBlockBehavior::register),
                new Entry(ID_WILD_RICE, WildRiceBlockBehavior::register),
                new Entry(ID_DOUBLE_CROP, DoubleCropBlockBehavior::register),
                new Entry(ID_HIGH_TEMPERATURE, HighTemperatureBlockBehavior::register),
                new Entry(ID_ROPE_BLOCK, RopeBlockBehavior::register),
                new Entry(ID_ROPE, RopeItemBehavior::register),
                new Entry(ID_CUTTING_BOARD, CuttingBoardBlockBehavior::register),
                new Entry(ID_SKILLET, SkilletBlockBehavior::register),
                new Entry(ID_SKILLET_ITEM, SkilletItemBehavior::register),
                new Entry(ID_SKEWER_ITEM, HandheldSkewerBehavior::register),
                new Entry(ID_STOVE, StoveBlockBehavior::register),
                new Entry(ID_BASKET, BasketBlockBehavior::register),
                new Entry(ID_COMPARATOR_SIGNAL, RedstoneComparatorBlockBehavior::register),
                new Entry(ID_COOKING_POT, CookingPotBlockBehavior::register),
                new Entry(ID_JUG, JugBlockBehavior::register),
                new Entry(ID_JUG_ITEM, JugItemBehavior::register),
                new Entry(ID_VILLAGER_FOOD_POINT, VillagerFoodPointSetting::register)
        );
    }

    public static void registerAll(Logger logger) {
        for (Entry entry : entries()) {
            try {
                entry.registration().run();
                logger.info(ConfigManager.getOr("behavior_reg", "Registered behavior: %id%")
                        .replace("%id%", entry.id()));
            } catch (Throwable t) {
                logger.log(Level.SEVERE, ConfigManager.getOr("behavior_reg_fail",
                        "Failed to register %id%").replace("%id%", entry.id()), t);
            }
        }
    }
}
