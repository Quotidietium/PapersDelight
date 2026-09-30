package dev.tako.papersdelight.cookingpot;

public final class CookingPotLayout {

    private CookingPotLayout() {}

    public static final int SIZE = 27;
    public static final int EXPANDED_SIZE = 54;

    public static final int STATUS = 20;
    public static final int WAITING_OUTPUT = 7;
    public static final int UTENSIL = 23;
    public static final int FINAL_OUTPUT = 25;

    public static final int START_BUTTON = 14;
    public static final int RECIPE_BOOK_BUTTON = 9;
    public static final int CLOSE_BUTTON = -1;

    public static final int[] INGREDIENTS = {1, 2, 3, 10, 11, 12};
    public static final int[] PROGRESS = {5};
    public static final int[] FLOW_ARROWS = {};
    public static final int[] LOCKED_DECORATION = {
            0, 4, 6, 8, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 26
    };

    public static final int RECIPE_LIST_PREVIOUS_PAGE = 27;
    public static final int RECIPE_LIST_PAGE_INFO = 31;
    public static final int RECIPE_LIST_HEAT_ICON = 32;
    public static final int RECIPE_LIST_FILTER_TOGGLE = 33;
    public static final int RECIPE_LIST_NEXT_PAGE = 35;

    public static final int RECIPE_LIST_START = 36;
    public static final int RECIPES_PER_PAGE = 18;

    public static final int[] RECIPE_LIST_BORDER = {28, 29, 30, 34};

    public static final int RECIPE_DETAIL_BACK = 27;
    public static final int RECIPE_DETAIL_AUTO_FILL = 29;
    public static final int[] RECIPE_DETAIL_BORDER_ROW3 = {28, 30, 31, 32, 33, 34, 35};
    public static final int[] RECIPE_DETAIL_INGREDIENTS = {37, 38, 39, 46, 47, 48};
    public static final int RECIPE_DETAIL_COOK_INFO = 41;
    public static final int RECIPE_DETAIL_RESULT = 43;
    public static final int RECIPE_DETAIL_CONTAINER = 50;
    public static final int[] RECIPE_DETAIL_BORDER_ROW45 = {36, 40, 42, 44, 45, 49, 51, 52, 53};
}
