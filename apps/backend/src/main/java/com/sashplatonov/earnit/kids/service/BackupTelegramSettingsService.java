package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.BackupTelegramSettingsEntity;
import com.sashplatonov.earnit.kids.dto.request.UpdateBackupTelegramSettingsRequest;
import com.sashplatonov.earnit.kids.dto.response.BackupTelegramSettingsResponse;
import com.sashplatonov.earnit.kids.repository.BackupTelegramSettingsRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;

@ApplicationScoped
public class BackupTelegramSettingsService {

    private static final int MIN_INTERVAL_HOURS = 1;
    private static final int MAX_INTERVAL_HOURS = 720;

    private final BackupTelegramSettingsRepository repository;
    private final boolean fallbackEnabled;
    private final String fallbackBotToken;
    private final String fallbackChatId;
    private final int fallbackIntervalHours;

    @Inject
    public BackupTelegramSettingsService(
        BackupTelegramSettingsRepository repository,
        @ConfigProperty(name = "ENABLE_TELEGRAM_ALERTS", defaultValue = "false") boolean fallbackEnabled,
        @ConfigProperty(name = "TELEGRAM_BOT_TOKEN", defaultValue = "") String fallbackBotToken,
        @ConfigProperty(name = "TELEGRAM_CHAT_ID", defaultValue = "") String fallbackChatId,
        @ConfigProperty(name = "BACKUP_INTERVAL_HOURS", defaultValue = "24") int fallbackIntervalHours
    ) {
        this.repository = repository;
        this.fallbackEnabled = fallbackEnabled;
        this.fallbackBotToken = normalize(fallbackBotToken);
        this.fallbackChatId = normalize(fallbackChatId);
        this.fallbackIntervalHours = sanitizeInterval(fallbackIntervalHours);
    }

    public BackupTelegramSettingsResponse getSettings() {
        return toResponse(currentSettings());
    }

    public TelegramBackupSettingsSnapshot currentSettings() {
        return repository.findSettings().map(this::toSnapshot).orElseGet(this::fallbackSnapshot);
    }

    @Transactional
    public OperationResult<BackupTelegramSettingsResponse> updateSettings(UpdateBackupTelegramSettingsRequest request) {
        if (request == null) {
            return OperationResult.failure("Настройки Telegram не переданы");
        }

        int intervalHours = request.intervalHours();
        if (intervalHours < MIN_INTERVAL_HOURS || intervalHours > MAX_INTERVAL_HOURS) {
            return OperationResult.failure("Интервал отправки должен быть от 1 до 720 часов");
        }

        BackupTelegramSettingsEntity entity = ensureEntity();
        entity.setEnabled(request.enabled());
        entity.setChatId(normalize(request.chatId()));
        entity.setIntervalHours(intervalHours);

        if (request.botToken() != null) {
            entity.setBotToken(normalize(request.botToken()));
        }

        String validationError = validate(entity);
        if (validationError != null) {
            return OperationResult.failure(validationError);
        }

        repository.flushChanges();
        return OperationResult.success(toResponse(toSnapshot(entity)));
    }

    @Transactional
    public void recordSuccess(Instant attemptedAt) {
        BackupTelegramSettingsEntity entity = ensureEntity();
        entity.setLastAttemptAt(attemptedAt);
        entity.setLastSentAt(attemptedAt);
        entity.setLastError(null);
        repository.flushChanges();
    }

    @Transactional
    public void recordFailure(Instant attemptedAt, String message) {
        BackupTelegramSettingsEntity entity = ensureEntity();
        entity.setLastAttemptAt(attemptedAt);
        entity.setLastError(truncate(message));
        repository.flushChanges();
    }

    private BackupTelegramSettingsEntity ensureEntity() {
        return repository.findSettings().orElseGet(this::createFromFallback);
    }

    private BackupTelegramSettingsEntity createFromFallback() {
        BackupTelegramSettingsEntity entity = BackupTelegramSettingsEntity.builder()
            .id(BackupTelegramSettingsEntity.DEFAULT_ID)
            .enabled(fallbackEnabled)
            .botToken(fallbackBotToken)
            .chatId(fallbackChatId)
            .intervalHours(fallbackIntervalHours)
            .build();
        repository.persistAndFlush(entity);
        return entity;
    }

    private TelegramBackupSettingsSnapshot fallbackSnapshot() {
        return new TelegramBackupSettingsSnapshot(
            fallbackEnabled,
            fallbackBotToken,
            fallbackChatId,
            fallbackIntervalHours,
            null,
            null,
            null
        );
    }

    private TelegramBackupSettingsSnapshot toSnapshot(BackupTelegramSettingsEntity entity) {
        return new TelegramBackupSettingsSnapshot(
            entity.isEnabled(),
            normalize(entity.getBotToken()),
            normalize(entity.getChatId()),
            sanitizeInterval(entity.getIntervalHours()),
            entity.getLastAttemptAt(),
            entity.getLastSentAt(),
            entity.getLastError()
        );
    }

    private BackupTelegramSettingsResponse toResponse(TelegramBackupSettingsSnapshot snapshot) {
        return new BackupTelegramSettingsResponse(
            snapshot.enabled(),
            snapshot.chatId(),
            snapshot.intervalHours(),
            snapshot.hasBotToken(),
            snapshot.configured(),
            snapshot.lastAttemptAt(),
            snapshot.lastSentAt(),
            snapshot.lastError()
        );
    }

    private int sanitizeInterval(int intervalHours) {
        return Math.max(MIN_INTERVAL_HOURS, Math.min(intervalHours, MAX_INTERVAL_HOURS));
    }

    private String validate(BackupTelegramSettingsEntity entity) {
        if (!entity.isEnabled()) {
            return null;
        }
        if (!hasText(entity.getChatId())) {
            return "Укажите Telegram chat id";
        }
        if (!hasText(entity.getBotToken())) {
            return "Сохраните Telegram bot token";
        }
        return null;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
