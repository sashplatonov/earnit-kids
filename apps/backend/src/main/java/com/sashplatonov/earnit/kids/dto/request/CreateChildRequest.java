package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChildRequest(
    @NotBlank(message = "Child name is required")
    @Size(max = 50, message = "Child name must be at most 50 characters")
    String name
) { }
