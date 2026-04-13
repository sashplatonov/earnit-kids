package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Admin password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Schema(format = "password")
    String adminPin
) { }
