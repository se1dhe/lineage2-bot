package com.lineage2bot.web;

import com.lineage2bot.craft.CraftNode;
import com.lineage2bot.craft.CraftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/craft")
public class CraftController {
    private final CraftService craftService;

    public CraftController(CraftService craftService) {
        this.craftService = craftService;
    }

    @GetMapping("/grades")
    public List<CraftService.GradeGroup> grades() {
        return craftService.gradeGroups();
    }

    @GetMapping("/recipes")
    public List<CraftService.RecipeCard> recipes(
            @RequestParam(defaultValue = "") String grade,
            @RequestParam(defaultValue = "") String q
    ) {
        return craftService.recipes(grade, q);
    }

    @GetMapping("/tree/{itemId}")
    public CraftNode tree(@PathVariable int itemId, @RequestParam(defaultValue = "1") long count) {
        return craftService.tree(itemId, count);
    }
}
