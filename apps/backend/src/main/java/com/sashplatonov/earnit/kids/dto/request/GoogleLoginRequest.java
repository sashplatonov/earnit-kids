package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "{validation.google.credential.required}")
    String credential
) { }
