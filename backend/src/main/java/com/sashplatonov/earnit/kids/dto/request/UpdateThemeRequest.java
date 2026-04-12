package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateThemeRequest(
    @NotBlank(message = "Theme is required")
    String theme
) { }