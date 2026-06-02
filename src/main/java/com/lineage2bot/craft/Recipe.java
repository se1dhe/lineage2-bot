package com.lineage2bot.craft;

import java.util.List;

public record Recipe(
        int id,
        String alias,
        int recipeItemId,
        int productItemId,
        long productCount,
        int level,
        int mpConsume,
        int successRate,
        boolean dwarven,
        List<Material> materials
) {
}
