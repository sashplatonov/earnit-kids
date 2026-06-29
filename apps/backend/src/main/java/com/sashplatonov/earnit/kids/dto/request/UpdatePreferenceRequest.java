package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdatePreferenceRequest(
    @NotNull(message = "{validation.preference.key.required}")
    FamilyPreferenceKey key,

    Object value
) { }
