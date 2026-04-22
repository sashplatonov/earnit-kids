package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePreferenceRequest(
    @NotBlank(message = "{validation.preference.key.required}")
    String key,

    Object value
) { }
