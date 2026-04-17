package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePreferenceRequest(
    @NotBlank(message = "Preference key is required")
    String key,

    Object value
) { }
