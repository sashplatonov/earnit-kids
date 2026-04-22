package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateThemeRequest(
    @NotBlank(message = "{validation.theme.required}")
    String theme
) { }
