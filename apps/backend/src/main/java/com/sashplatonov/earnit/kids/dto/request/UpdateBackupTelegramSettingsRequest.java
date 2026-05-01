package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateBackupTelegramSettingsRequest(
    boolean enabled,

    @Schema(format = "password")
    String botToken,

    String chatId,

    @Min(value = 1, message = "{validation.backup.interval.min}")
    @Max(value = 720, message = "{validation.backup.interval.max}")
    int intervalHours,

    @Min(value = 1, message = "{validation.backup.retention.min}")
    @Max(value = 500, message = "{validation.backup.retention.max}")
    int backupRetentionCount
) { }
