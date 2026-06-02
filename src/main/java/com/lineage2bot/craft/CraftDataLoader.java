package com.lineage2bot.craft;

import com.lineage2bot.config.AppProperties;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class CraftDataLoader {
    private final AppProperties properties;

    public CraftDataLoader(AppProperties properties) {
        this.properties = properties;
    }

    public CraftData load() {
        List<Recipe> recipes = loadRecipes();
        Set<Integer> recipeItemIds = recipeItemIds(recipes);
        Map<Integer, Item> xmlItems = loadXmlItems();
        Map<Integer, Item> wikiItems = loadWikiItems();
        Map<Integer, Item> mergedItems = new LinkedHashMap<>(xmlItems);
        wikiItems.forEach((id, wiki) -> mergedItems.merge(id, wiki, (xml, enriched) -> new Item(
                xml.id(),
                choose(enriched.name(), xml.name()),
                choose(enriched.grade(), xml.grade()),
                choose(enriched.typeMain(), xml.typeMain()),
                choose(enriched.typeSub(), xml.typeSub()),
                choose(enriched.typeSlot(), xml.typeSlot()),
                choose(enriched.icon(), xml.icon())
        )));
        mergedItems.keySet().retainAll(recipeItemIds);

        Map<Integer, List<Recipe>> byProduct = new HashMap<>();
        for (Recipe recipe : recipes) {
            byProduct.computeIfAbsent(recipe.productItemId(), ignored -> new ArrayList<>()).add(recipe);
        }
        byProduct.values().forEach(list -> list.sort(Comparator.comparingInt(Recipe::successRate).reversed()));
        return new CraftData(Map.copyOf(mergedItems), List.copyOf(recipes), Map.copyOf(byProduct));
    }

    private Set<Integer> recipeItemIds(List<Recipe> recipes) {
        Set<Integer> ids = new HashSet<>();
        for (Recipe recipe : recipes) {
            ids.add(recipe.recipeItemId());
            ids.add(recipe.productItemId());
            recipe.materials().forEach(material -> ids.add(material.itemId()));
        }
        return ids;
    }

    private Map<Integer, Item> loadXmlItems() {
        Map<Integer, Item> result = new HashMap<>();
        Path itemsDir = Path.of(properties.data().itemsDir());
        try (Stream<Path> files = Files.list(itemsDir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".xml")).sorted().toList()) {
                Document doc = parseXml(file);
                NodeList nodes = doc.getElementsByTagName("item");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element item = (Element) nodes.item(i);
                    int id = intAttr(item, "id", 0);
                    String type = item.getAttribute("type");
                    result.put(id, new Item(id, item.getAttribute("name"), "NG", type, "", "", defaultIcon(type)));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read item XML directory: " + itemsDir, e);
        }
        return result;
    }

    private Map<Integer, Item> loadWikiItems() {
        Map<Integer, Item> result = new HashMap<>();
        Path db = Path.of(properties.data().wikiDbPath());
        if (!Files.exists(db)) {
            return result;
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             ResultSet rows = connection.createStatement().executeQuery("""
                     select id, name, grade, type_main, type_sub, type_slot, icon_url
                     from items
                     """)) {
            while (rows.next()) {
                int id = rows.getInt("id");
                result.put(id, new Item(
                        id,
                        rows.getString("name"),
                        normalizeGrade(rows.getString("grade")),
                        value(rows.getString("type_main")),
                        value(rows.getString("type_sub")),
                        value(rows.getString("type_slot")),
                        localIcon(rows.getString("icon_url"))
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read wiki SQLite database: " + db, e);
        }
        return result;
    }

    private List<Recipe> loadRecipes() {
        Path recipesPath = Path.of(properties.data().recipesPath());
        Document doc = parseXml(recipesPath);
        NodeList nodes = doc.getElementsByTagName("recipe");
        List<Recipe> recipes = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            Material product = parsePair(node.getAttribute("product"));
            recipes.add(new Recipe(
                    intAttr(node, "id", 0),
                    node.getAttribute("alias"),
                    intAttr(node, "itemId", 0),
                    product.itemId(),
                    product.count(),
                    intAttr(node, "level", 0),
                    intAttr(node, "mpConsume", 0),
                    intAttr(node, "successRate", 100),
                    Boolean.parseBoolean(node.getAttribute("isDwarven")),
                    parseMaterials(node.getAttribute("material"))
            ));
        }
        recipes.sort(Comparator.comparingInt(Recipe::id));
        return recipes;
    }

    private List<Material> parseMaterials(String raw) {
        List<Material> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(";")) {
            result.add(parsePair(part));
        }
        return result;
    }

    private Material parsePair(String raw) {
        String[] parts = raw.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Bad item-count pair: " + raw);
        }
        return new Material(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
    }

    private Document parseXml(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(path.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse XML: " + path, e);
        }
    }

    private int intAttr(Element element, String name, int fallback) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private String normalizeGrade(String grade) {
        String value = value(grade);
        return value.isBlank() ? "NG" : value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String choose(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? value(fallback) : preferred;
    }

    private String localIcon(String iconUrl) {
        if (iconUrl == null || iconUrl.isBlank()) {
            return "/icons/etc_question_mark_i00.png";
        }
        int slash = iconUrl.lastIndexOf('/');
        return "/icons/" + iconUrl.substring(slash + 1);
    }

    private String defaultIcon(String type) {
        return switch (value(type)) {
            case "Weapon" -> "/icons/weapon_small_sword_i00.png";
            case "Armor" -> "/icons/armor_leather_shirt_i00.png";
            default -> "/icons/etc_question_mark_i00.png";
        };
    }
}
