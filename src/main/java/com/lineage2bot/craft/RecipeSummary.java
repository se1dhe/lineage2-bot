package com.lineage2bot.craft;

public record RecipeSummary(
        int id,
        String alias,
        int recipeItemId,
        int level,
        int mpConsume,
        int successRate,
        boolean dwarven
) {
    static RecipeSummary from(Recipe recipe) {
        return new RecipeSummary(
                recipe.id(),
                recipe.alias(),
                recipe.recipeItemId(),
                recipe.level(),
                recipe.mpConsume(),
                recipe.successRate(),
                recipe.dwarven()
        );
    }
}
