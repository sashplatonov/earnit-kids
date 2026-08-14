package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.sashplatonov.earnit.kids.config.TelegramConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class TelegramBotApiClient {
    private final TelegramConfig config;
    private final ObjectReader jsonReader;
    private final ObjectWriter jsonWriter;
    private final HttpClient httpClient;

    @Inject
    public TelegramBotApiClient(TelegramConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.jsonReader = objectMapper.reader();
        this.jsonWriter = objectMapper.writer();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Long sendMessage(long chatId, String text, List<InlineButton> buttons) throws Exception {
        com.fasterxml.jackson.databind.JsonNode result = call("sendMessage", messagePayload(chatId, null, text, buttons));
        return result.path("result").path("message_id").isNumber()
            ? result.path("result").path("message_id").longValue() : null;
    }

    public void editMessageText(long chatId, long messageId, String text, List<InlineButton> buttons) throws Exception {
        call("editMessageText", messagePayload(chatId, messageId, text, buttons));
    }

    public void answerCallbackQuery(String callbackQueryId) throws Exception {
        call("answerCallbackQuery", Map.of("callback_query_id", callbackQueryId));
    }

    public void registerWebhook(URI webhookUrl, String secret) throws Exception {
        call("setWebhook", Map.of(
            "url", webhookUrl.toString(),
            "secret_token", secret,
            "allowed_updates", List.of("message", "callback_query")));
    }

    private com.fasterxml.jackson.databind.JsonNode call(String method, Object payload) throws Exception {
        String token = config.botToken().orElseThrow();
        String body = jsonWriter.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https:" + "/" + "/api.telegram.org/bot" + token + "/" + method))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        com.fasterxml.jackson.databind.JsonNode result = jsonReader.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300
            || !result.path("ok").asBoolean(false)) {
            throw new TelegramApiException(response.statusCode(),
                result.path("description").asText("unknown error"),
                result.path("parameters").path("retry_after").asInt(0));
        }
        return result;
    }

    private Map<String, Object> messagePayload(long chatId, Long messageId, String text,
                                               List<InlineButton> buttons) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("chat_id", chatId);
        if (messageId != null) {
            payload.put("message_id", messageId);
        }
        payload.put("text", text);
        payload.put("reply_markup", Map.of("inline_keyboard", buttons.stream()
            .map(button -> List.of(buttonPayload(button))).toList()));
        return payload;
    }

    private Map<String, Object> buttonPayload(InlineButton button) {
        if (button.callbackData() != null) {
            return Map.of("text", button.text(), "callback_data", button.callbackData());
        }
        return Map.of("text", button.text(), "web_app", Map.of("url", button.url()));
    }

    public record InlineButton(String text, String url, String callbackData) {
        public InlineButton(String text, String url) {
            this(text, url, null);
        }

        public static InlineButton callback(String text, String data) {
            return new InlineButton(text, null, data);
        }

        public static InlineButton webApp(String text, String url) {
            return new InlineButton(text, url, null);
        }
    }
}
