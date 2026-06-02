package com.lineage2bot.craft;

import java.util.List;

public record CraftNode(
        Item item,
        long count,
        boolean craftable,
        RecipeSummary recipe,
        List<CraftNode> materials
) {
}
