package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOwnNicknameRequest(
    @NotBlank(message = "Nickname is required")
    @Size(max = 50, message = "Nickname must be at most 50 characters")
    String nickname
) { }
