package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record LoginChildRequest(
    @NotBlank(message = "Token is required")
    @Schema(format = "password")
    String token
) { }
