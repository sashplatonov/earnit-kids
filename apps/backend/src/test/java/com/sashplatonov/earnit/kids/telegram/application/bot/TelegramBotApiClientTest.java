package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sashplatonov.earnit.kids.telegram.config.TelegramConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import javax.net.ssl.SSLSession;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramBotApiClientTest {
    @Test
    void sendMessageUsesTelegramResponseAndBuildsRows() throws Exception {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.botToken()).thenReturn(Optional.of("token"));
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = response(200, "{\"ok\":true,\"result\":{\"message_id\":42}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        TelegramBotApiClient client = new TelegramBotApiClient(config, new ObjectMapper(), http);

        assertThat(client.sendMessage(7, "hello", List.of(
            TelegramBotApiClient.InlineButton.callback("A", "a", "row"),
            TelegramBotApiClient.InlineButton.url("B", "https://example.test", "row"),
            TelegramBotApiClient.InlineButton.webApp("C", "https://app.test"))))
            .isEqualTo(42L);
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void nonSuccessfulTelegramResponseBecomesTelegramApiException() throws Exception {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.botToken()).thenReturn(Optional.of("token"));
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response(400, "{\"ok\":false,\"description\":\"bad request\",\"parameters\":{\"retry_after\":3}}"));
        TelegramBotApiClient client = new TelegramBotApiClient(config, new ObjectMapper(), http);

        assertThatThrownBy(() -> client.answerCallbackQuery("callback"))
            .isInstanceOf(TelegramApiException.class)
            .hasMessageContaining("bad request");
    }

    @Test
    void replyKeyboardAndWebhookOperationsAreSent() throws Exception {
        TelegramConfig config = mock(TelegramConfig.class);
        when(config.botToken()).thenReturn(Optional.of("token"));
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response(200, "{\"ok\":true,\"result\":{\"message_id\":9}}"));
        TelegramBotApiClient client = new TelegramBotApiClient(config, new ObjectMapper(), http);
        TelegramReplyKeyboard keyboard = new TelegramReplyKeyboard(List.of(
            new TelegramReplyKeyboard.Row(List.of(
                new TelegramReplyKeyboard.Button("Open", "https://app.test"),
                new TelegramReplyKeyboard.Button("Tasks", null)))));

        assertThat(client.sendMessageWithReplyKeyboard(7, "home", keyboard)).isEqualTo(9L);
        client.removeReplyKeyboard(7);
        client.registerWebhook(java.net.URI.create("https://app.test/hook"), "secret");
        verify(http, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return null; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
            public String body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public java.net.URI uri() { return java.net.URI.create("https://api.telegram.org"); }
            public Version version() { return Version.HTTP_1_1; }
        };
    }
}
