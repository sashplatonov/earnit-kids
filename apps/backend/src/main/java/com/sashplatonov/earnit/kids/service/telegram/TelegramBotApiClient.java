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

    // EXPLAIN: UX-01 — send a message with a persistent reply keyboard instead of
    // EXPLAIN: inline keyboard. Used for /start and home-screen messages.
    public Long sendMessageWithReplyKeyboard(long chatId, String text, TelegramReplyKeyboard replyKeyboard)
        throws Exception {
        com.fasterxml.jackson.databind.JsonNode result = call("sendMessage",
            replyKeyboardPayload(chatId, null, text, replyKeyboard));
        return result.path("result").path("message_id").isNumber()
            ? result.path("result").path("message_id").longValue() : null;
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
        if (buttons != null && !buttons.isEmpty()) {
            payload.put("reply_markup", Map.of("inline_keyboard", keyboardRows(buttons)));
        }
        return payload;
    }

    // EXPLAIN: Builds the payload for a message with ReplyKeyboardMarkup.
    private Map<String, Object> replyKeyboardPayload(long chatId, Long messageId, String text,
                                                     TelegramReplyKeyboard replyKeyboard) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("chat_id", chatId);
        if (messageId != null) {
            payload.put("message_id", messageId);
        }
        payload.put("text", text);
        payload.put("reply_markup", replyKeyboardPayload(replyKeyboard));
        return payload;
    }

    // EXPLAIN: Serialises a TelegramReplyKeyboard to the Telegram `reply_markup` map.
    private Map<String, Object> replyKeyboardPayload(TelegramReplyKeyboard replyKeyboard) {
        List<List<Map<String, Object>>> keyboardRows = replyKeyboard.rows().stream()
            .map(row -> row.buttons().stream()
                .map(this::replyKeyboardButtonPayload)
                .toList())
            .toList();
        return Map.of(
            "keyboard", keyboardRows,
            "is_persistent", replyKeyboard.isPersistent(),
            "resize_keyboard", replyKeyboard.resizeKeyboard(),
            "one_time_keyboard", replyKeyboard.oneTimeKeyboard()
        );
    }

    // EXPLAIN: A web_app button opens the Mini App client-side; a plain button
    // EXPLAIN: only sends its label as a message (used for nav actions).
    // EXPLAIN: Telegram KeyboardButton has no `url` field — only web_app.
    private Map<String, Object> replyKeyboardButtonPayload(TelegramReplyKeyboard.Button button) {
        if (button.webAppUrl() != null) {
            return Map.of("text", button.label(), "web_app", Map.of("url", button.webAppUrl()));
        }
        return Map.of("text", button.label());
    }

    // EXPLAIN: Adjacent buttons sharing a non-null rowId render on one Telegram
    // EXPLAIN: keyboard row (2-column grids); null rowId renders a full-width row.
    private List<List<Map<String, Object>>> keyboardRows(List<InlineButton> buttons) {
        List<List<Map<String, Object>>> rows = new java.util.ArrayList<>();
        List<InlineButton> current = new java.util.ArrayList<>();
        for (InlineButton button : buttons) {
            if (button.rowId() == null) {
                flushRow(current, rows);
                current = new java.util.ArrayList<>();
                rows.add(List.of(buttonPayload(button)));
            } else if (current.isEmpty() || button.rowId().equals(current.get(0).rowId())) {
                current.add(button);
            } else {
                flushRow(current, rows);
                current = new java.util.ArrayList<>(List.of(button));
            }
        }
        flushRow(current, rows);
        return rows;
    }

    private void flushRow(List<InlineButton> current, List<List<Map<String, Object>>> rows) {
        if (!current.isEmpty()) {
            rows.add(current.stream().map(this::buttonPayload).toList());
        }
    }

    private Map<String, Object> buttonPayload(InlineButton button) {
        if (button.callbackData() != null) {
            return Map.of("text", button.text(), "callback_data", button.callbackData());
        }
        if (button.urlKind() != null && "url".equals(button.urlKind())) {
            return Map.of("text", button.text(), "url", button.url());
        }
        return Map.of("text", button.text(), "web_app", Map.of("url", button.url()));
    }

    public record InlineButton(String text, String url, String callbackData, String rowId, String urlKind) {
        public InlineButton(String text, String url) {
            this(text, url, null, null, null);
        }

        public InlineButton(String text, String url, String rowId) {
            this(text, url, null, rowId, null);
        }

        public static InlineButton callback(String text, String data) {
            return new InlineButton(text, null, data, null, null);
        }

        public static InlineButton callback(String text, String data, String rowId) {
            return new InlineButton(text, null, data, rowId, null);
        }

        public static InlineButton webApp(String text, String url) {
            return new InlineButton(text, url, null, null, null);
        }

        public static InlineButton webApp(String text, String url, String rowId) {
            return new InlineButton(text, url, null, rowId, null);
        }

        public static InlineButton url(String text, String url, String rowId) {
            return new InlineButton(text, url, null, rowId, "url");
        }
    }
}
