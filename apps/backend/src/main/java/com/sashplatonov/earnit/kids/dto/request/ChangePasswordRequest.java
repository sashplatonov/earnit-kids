package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required")
    @Schema(format = "password")
    String oldPassword,

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "New password must be at least 6 characters")
    @Schema(format = "password")
    String newPassword
) { }
