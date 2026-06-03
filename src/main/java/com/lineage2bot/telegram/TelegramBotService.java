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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    public void sendMissingReport(long chatId, String text) {
        if (!telegram.enabled()) {
            throw new IllegalStateException("Telegram bot token is empty.");
        }
        String safeText = text == null || text.isBlank() ? "Список ресурсов пуст." : text;
        post("/sendMessage", Map.of(
                "chat_id", chatId,
                "text", safeText,
                "disable_web_page_preview", true
        ));
    }

    public long verifiedUserId(String initData) {
        if (!telegram.enabled()) {
            throw new IllegalStateException("Telegram bot token is empty.");
        }
        Map<String, String> params = parseInitData(initData);
        String hash = params.remove("hash");
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Telegram initData hash is missing.");
        }

        String dataCheckString = new TreeMap<>(params).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8), telegram.botToken().getBytes(StandardCharsets.UTF_8));
        String calculatedHash = HexFormat.of().formatHex(hmacSha256(secretKey, dataCheckString));
        if (!MessageDigest.isEqual(calculatedHash.getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Telegram initData signature is invalid.");
        }

        try {
            JsonNode user = mapper.readTree(params.getOrDefault("user", "{}"));
            long userId = user.path("id").asLong(0);
            if (userId <= 0) {
                throw new IllegalArgumentException("Telegram user id is missing.");
            }
            return userId;
        } catch (Exception e) {
            throw new IllegalArgumentException("Telegram user payload is invalid.", e);
        }
    }

    private Map<String, String> parseInitData(String initData) {
        if (initData == null || initData.isBlank()) {
            throw new IllegalArgumentException("Telegram initData is empty.");
        }
        Map<String, String> params = new HashMap<>();
        for (String pair : initData.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private byte[] hmacSha256(byte[] key, String value) {
        return hmacSha256(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmacSha256(byte[] key, byte[] value) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate Telegram signature.", e);
        }
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
