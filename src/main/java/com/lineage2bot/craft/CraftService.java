package com.lineage2bot.craft;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CraftService {
    private final CraftData data;

    public CraftService(CraftDataLoader loader) {
        this.data = loader.load();
    }

    public List<RecipeCard> recipes(String grade, String query) {
        String gradeFilter = normalize(grade);
        String q = normalize(query);
        return data.recipes().stream()
                .map(recipe -> card(recipe, data.items().get(recipe.productItemId())))
                .filter(card -> card.item() != null)
                .filter(card -> gradeFilter.isBlank() || normalize(card.item().grade()).equals(gradeFilter))
                .filter(card -> q.isBlank() || card.item().searchable().contains(q) || normalize(card.recipe().alias()).contains(q))
                .sorted(Comparator
                        .comparing((RecipeCard card) -> gradeRank(card.item().grade()))
                        .thenComparing(card -> card.item().typeMain())
                        .thenComparing(card -> card.item().name()))
                .limit(250)
                .toList();
    }

    public List<GradeGroup> gradeGroups() {
        Map<String, Long> counts = data.recipes().stream()
                .map(recipe -> data.items().get(recipe.productItemId()))
                .filter(item -> item != null && !item.grade().isBlank())
                .collect(Collectors.groupingBy(Item::grade, Collectors.counting()));

        return counts.entrySet().stream()
                .map(entry -> new GradeGroup(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(group -> gradeRank(group.grade())))
                .toList();
    }

    public CraftNode tree(int itemId, long count) {
        return tree(itemId, Math.max(1, count), new HashSet<>());
    }

    public Item item(int id) {
        return data.items().get(id);
    }

    private CraftNode tree(int itemId, long count, Set<Integer> path) {
        Item item = data.items().getOrDefault(itemId, new Item(itemId, "Item " + itemId, "", "", "", "", "/icons/etc_question_mark_i00.png"));
        List<Recipe> recipes = data.recipesByProduct().getOrDefault(itemId, List.of());
        if (recipes.isEmpty() || path.contains(itemId)) {
            return new CraftNode(item, count, false, null, List.of());
        }

        Recipe recipe = recipes.getFirst();
        long crafts = divCeil(count, recipe.productCount());
        Set<Integer> nextPath = new HashSet<>(path);
        nextPath.add(itemId);
        List<CraftNode> materials = recipe.materials().stream()
                .map(material -> tree(material.itemId(), material.count() * crafts, nextPath))
                .toList();

        return new CraftNode(item, count, true, RecipeSummary.from(recipe), materials);
    }

    private RecipeCard card(Recipe recipe, Item item) {
        return new RecipeCard(item, RecipeSummary.from(recipe), recipe.productCount());
    }

    private long divCeil(long value, long divisor) {
        return (value + divisor - 1) / divisor;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int gradeRank(String grade) {
        return switch (normalize(grade)) {
            case "ng", "none" -> 0;
            case "d" -> 1;
            case "c" -> 2;
            case "b" -> 3;
            case "a" -> 4;
            case "s" -> 5;
            case "s80" -> 6;
            case "s84" -> 7;
            case "r" -> 8;
            case "r95" -> 9;
            case "r99" -> 10;
            default -> 99;
        };
    }

    public record RecipeCard(Item item, RecipeSummary recipe, long productCount) {
    }

    public record GradeGroup(String grade, long count) {
    }
}
