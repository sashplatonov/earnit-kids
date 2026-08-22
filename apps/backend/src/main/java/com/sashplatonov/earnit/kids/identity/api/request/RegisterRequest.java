package com.sashplatonov.earnit.kids.identity.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record RegisterRequest(
    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    String email,

    @NotBlank(message = "{validation.password.admin.required}")
    @Size(min = 6, message = "{validation.password.min}")
    @Schema(format = "password")
    String password
) { }
