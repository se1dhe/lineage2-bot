package com.lineage2bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(String botToken) {
    public boolean enabled() {
        return botToken != null && !botToken.isBlank();
    }
}
