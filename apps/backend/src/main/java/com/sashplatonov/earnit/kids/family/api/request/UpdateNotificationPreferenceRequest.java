package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
    @NotBlank String scope,
    Integer childId,
    @NotBlank String key,
    @NotNull Boolean enabled
) {
}
