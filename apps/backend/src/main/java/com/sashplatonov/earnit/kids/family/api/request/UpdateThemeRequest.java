package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.NotNull;

public record UpdateThemeRequest(
    @NotNull(message = "{validation.theme.required}")
    ChildTheme theme
) { }
