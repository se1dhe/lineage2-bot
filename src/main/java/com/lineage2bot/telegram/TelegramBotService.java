package com.lineage2bot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lineage2bot.config.AppProperties;
import com.lineage2bot.config.TelegramProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TelegramBotService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final TelegramProperties telegram;
    private final AppProperties app;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private RestClient client;
    private long offset = 0;

    public TelegramBotService(TelegramProperties telegram, AppProperties app, ObjectMapper mapper) {
        this.telegram = telegram;
        this.app = app;
        this.mapper = mapper;
    }

    @PostConstruct
    void start() {
        if (!telegram.enabled()) {
            log.info("Telegram bot token is empty, bot polling is disabled.");
            return;
        }
        client = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + telegram.botToken())
                .build();
        executor.scheduleWithFixedDelay(this::pollSafely, 1, 2, TimeUnit.SECONDS);
        log.info("Telegram bot polling started.");
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    private void pollSafely() {
        try {
            JsonNode result = client.get()
                    .uri(uri -> uri.path("/getUpdates")
                            .queryParam("timeout", 20)
                            .queryParam("offset", offset)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (result == null || !result.path("ok").asBoolean(false)) {
                return;
            }
            for (JsonNode update : result.path("result")) {
                offset = update.path("update_id").asLong() + 1;
                handle(update);
            }
        } catch (Exception e) {
            log.warn("Telegram polling failed: {}", e.getMessage());
        }
    }

    private void handle(JsonNode update) {
        JsonNode message = update.path("message");
        if (message.isMissingNode()) {
            return;
        }
        long chatId = message.path("chat").path("id").asLong();
        String text = message.path("text").asText("");
        if (text.startsWith("/start") || text.startsWith("/craft") || text.isBlank()) {
            sendCraftButton(chatId);
        } else {
            sendText(chatId, "Открой мини-приложение через кнопку ниже. Первая функция: калькулятор крафта.");
            sendCraftButton(chatId);
        }
    }

    private void sendCraftButton(long chatId) {
        String webAppUrl = app.publicUrl().replaceAll("/+$", "") + "/";
        Map<String, Object> payload = Map.of(
                "chat_id", chatId,
                "text", "Калькулятор крафта Lineage 2 готов к работе.",
                "reply_markup", Map.of("inline_keyboard", new Object[][]{
                        {Map.of(
                                "text", "Открыть калькулятор",
                                "web_app", Map.of("url", webAppUrl)
                        )}
                })
        );
        post("/sendMessage", payload);
    }

    private void sendText(long chatId, String text) {
        post("/sendMessage", Map.of("chat_id", chatId, "text", text));
    }

    private void post(String method, Map<String, Object> payload) {
        try {
            client.post()
                    .uri(method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(payload))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Telegram API call {} failed: {}", method, e.getMessage());
        }
    }
}
