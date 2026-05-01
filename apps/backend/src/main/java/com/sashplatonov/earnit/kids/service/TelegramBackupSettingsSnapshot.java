package com.sashplatonov.earnit.kids.service;

import java.time.Duration;
import java.time.Instant;

public record TelegramBackupSettingsSnapshot(
    boolean enabled,
    String botToken,
    String chatId,
    int intervalHours,
    int backupRetentionCount,
    Instant lastAttemptAt,
    Instant lastSentAt,
    String lastError
) {
    public boolean hasBotToken() {
        return botToken != null && !botToken.isBlank();
    }

    public boolean configured() {
        return hasBotToken() && chatId != null && !chatId.isBlank();
    }

    public boolean dueAt(Instant now) {
        if (!enabled || !configured()) {
            return false;
        }

        Instant anchor = lastAttemptAt != null ? lastAttemptAt : lastSentAt;
        if (anchor == null) {
            return true;
        }

        return !anchor.plus(Duration.ofHours(intervalHours)).isAfter(now);
    }
}
