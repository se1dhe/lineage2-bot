package com.lineage2bot.web;

import com.lineage2bot.craft.CraftNode;
import com.lineage2bot.craft.CraftService;
import com.lineage2bot.telegram.TelegramBotService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/craft")
public class CraftController {
    private final CraftService craftService;
    private final TelegramBotService telegramBotService;

    public CraftController(CraftService craftService, TelegramBotService telegramBotService) {
        this.craftService = craftService;
        this.telegramBotService = telegramBotService;
    }

    @GetMapping("/grades")
    public List<CraftService.GradeGroup> grades(@RequestParam(defaultValue = "") String category) {
        return craftService.gradeGroups(category);
    }

    @GetMapping("/recipes")
    public List<CraftService.RecipeCard> recipes(
            @RequestParam(defaultValue = "") String grade,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String q
    ) {
        return craftService.recipes(grade, category, q);
    }

    @GetMapping("/tree/{itemId}")
    public CraftNode tree(@PathVariable int itemId, @RequestParam(defaultValue = "1") long count) {
        return craftService.tree(itemId, count);
    }

    @GetMapping("/tree/recipe/{recipeId}")
    public CraftNode treeByRecipe(@PathVariable int recipeId, @RequestParam(defaultValue = "1") long count) {
        try {
            return craftService.treeByRecipe(recipeId, count);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @PostMapping("/telegram/missing")
    public Map<String, Boolean> sendMissing(@RequestBody MissingRequest request) {
        try {
            long userId = telegramBotService.verifiedUserId(request.initData());
            telegramBotService.sendMissingReport(userId, request.text());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
        }
        return Map.of("ok", true);
    }

    public record MissingRequest(String initData, String text) {
    }
}
