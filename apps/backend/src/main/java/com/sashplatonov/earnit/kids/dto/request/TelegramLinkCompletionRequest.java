package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TelegramLinkCompletionRequest(
    @NotBlank String token,
    @NotBlank String initData
) { }
