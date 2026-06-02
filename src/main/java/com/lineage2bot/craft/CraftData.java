package com.lineage2bot.craft;

import java.util.List;
import java.util.Map;

public record CraftData(
        Map<Integer, Item> items,
        List<Recipe> recipes,
        Map<Integer, List<Recipe>> recipesByProduct
) {
}
