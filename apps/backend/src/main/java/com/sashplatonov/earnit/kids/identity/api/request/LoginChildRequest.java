package com.sashplatonov.earnit.kids.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record LoginChildRequest(
    @NotBlank(message = "{validation.token.required}")
    @Schema(format = "password")
    String token
) { }
