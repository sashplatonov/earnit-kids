package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ChangePasswordRequest(
    @NotBlank(message = "{validation.password.current.required}")
    @Schema(format = "password")
    String oldPassword,

    @NotBlank(message = "{validation.password.new.required}")
    @Size(min = 6, message = "{validation.password.new.min}")
    @Schema(format = "password")
    String newPassword
) { }
