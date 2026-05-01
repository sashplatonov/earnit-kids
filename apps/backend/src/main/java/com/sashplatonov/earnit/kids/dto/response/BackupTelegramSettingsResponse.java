package com.sashplatonov.earnit.kids.dto.response;

import java.time.Instant;

public record BackupTelegramSettingsResponse(
    boolean enabled,
    String chatId,
    int intervalHours,
    int backupRetentionCount,
    boolean hasBotToken,
    boolean configured,
    Instant lastAttemptAt,
    Instant lastSentAt,
    String lastError
) { }
