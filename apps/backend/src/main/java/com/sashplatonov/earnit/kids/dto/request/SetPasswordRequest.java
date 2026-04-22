package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record SetPasswordRequest(
    @NotBlank(message = "{validation.password.required}")
    @Size(min = 6, message = "{validation.password.min}")
    @Schema(format = "password")
    String password
) { }