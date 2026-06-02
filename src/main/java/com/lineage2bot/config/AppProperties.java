package com.lineage2bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicUrl,
        Data data
) {
    public record Data(
            String recipesPath,
            String itemsDir,
            String wikiDbPath,
            String iconsDir
    ) {
    }
}
