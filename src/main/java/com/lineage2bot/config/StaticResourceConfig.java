package com.lineage2bot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    private final AppProperties properties;

    public StaticResourceConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String iconsLocation = Path.of(properties.data().iconsDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/icons/**").addResourceLocations(iconsLocation);
    }
}
