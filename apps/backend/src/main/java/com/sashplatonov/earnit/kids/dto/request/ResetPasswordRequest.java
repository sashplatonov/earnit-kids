package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ResetPasswordRequest(
    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    String email,

    @NotBlank(message = "{validation.token.required}")
    String token,

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 6, message = "{validation.password.min}")
    @Schema(format = "password")
    String password
) { }
