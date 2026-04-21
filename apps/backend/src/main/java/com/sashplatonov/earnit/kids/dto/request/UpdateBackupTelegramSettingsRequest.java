package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateBackupTelegramSettingsRequest(
    boolean enabled,

    @Schema(format = "password")
    String botToken,

    String chatId,

    @Min(value = 1, message = "Interval must be at least 1 hour")
    @Max(value = 720, message = "Interval must be at most 720 hours")
    int intervalHours
) { }
