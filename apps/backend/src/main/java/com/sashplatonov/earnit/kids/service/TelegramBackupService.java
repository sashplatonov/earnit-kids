package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@ApplicationScoped
public class TelegramBackupService {

    private final BackupTelegramSettingsService backupTelegramSettingsService;
    private final TimeProvider timeProvider;
    private final HttpClient httpClient;

    public TelegramBackupService(
        BackupTelegramSettingsService backupTelegramSettingsService,
        TimeProvider timeProvider
    ) {
        this.backupTelegramSettingsService = backupTelegramSettingsService;
        this.timeProvider = timeProvider;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }

    public boolean isConfigured() {
        return backupTelegramSettingsService.currentSettings().configured();
    }

    public OperationResult<Void> sendBackup(Path file, String filename) {
        TelegramBackupSettingsSnapshot settings = backupTelegramSettingsService.currentSettings();
        if (!settings.configured()) {
            return OperationResult.failure("TELEGRAM_NOT_CONFIGURED", BackendMessages.message("super.telegramNotConfigured"));
        }

        var attemptedAt = timeProvider.now();
        try {
            String boundary = "Boundary-" + UUID.randomUUID();
            HttpRequest req = buildTelegramRequest(settings, filename, file, boundary);

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                backupTelegramSettingsService.recordSuccess(attemptedAt);
                return OperationResult.success(null);
            } else {
                String msg = "Telegram API returned " + status + ": " + resp.body();
                backupTelegramSettingsService.recordFailure(attemptedAt, msg);
                log.error("Failed to send backup to Telegram: {}", msg);
                return OperationResult.failure(msg);
            }
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            backupTelegramSettingsService.recordFailure(attemptedAt, ex.getMessage());
            log.error("Exception while sending backup to Telegram", ex);
            return OperationResult.failure("TELEGRAM_SEND_FAILED",
                BackendMessages.message("backup.sendFailed", java.util.Map.of("reason", String.valueOf(ex.getMessage()))));
        }
    }

    private HttpRequest buildTelegramRequest(
        TelegramBackupSettingsSnapshot settings,
        String filename,
        Path file,
        String boundary
    ) throws IOException {
        byte[] body = buildMultipartBody(settings.chatId(), filename, file, boundary);
        return HttpRequest.newBuilder()
            .uri(buildTelegramUri(settings.botToken()))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    }

    private URI buildTelegramUri(String botToken) {
        return URI.create("https:" + '/' + '/' + "api.telegram.org/bot" + botToken + "/sendDocument");
    }

    private byte[] buildMultipartBody(String chatId, String filename, Path file, String boundary) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file);
        StringBuilder pre = new StringBuilder();
        pre.append("--").append(boundary).append("\r\n");
        pre.append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
        pre.append(chatId).append("\r\n");
        pre.append("--").append(boundary).append("\r\n");
        pre.append("Content-Disposition: form-data; name=\"document\"; filename=\"")
            .append(filename)
            .append("\"\r\n");
        pre.append("Content-Type: application/octet-stream\r\n\r\n");

        byte[] preBytes = pre.toString().getBytes(StandardCharsets.UTF_8);
        byte[] endBytes = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[preBytes.length + fileBytes.length + endBytes.length];
        System.arraycopy(preBytes, 0, body, 0, preBytes.length);
        System.arraycopy(fileBytes, 0, body, preBytes.length, fileBytes.length);
        System.arraycopy(endBytes, 0, body, preBytes.length + fileBytes.length, endBytes.length);
        return body;
    }
}
