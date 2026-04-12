package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePinRequest(
    @NotBlank(message = "Current PIN is required")
    String oldPin,

    @NotBlank(message = "New PIN is required")
    @Size(min = 6, message = "New PIN must be at least 6 characters")
    String newPin
) { }