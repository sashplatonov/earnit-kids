package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(
    @NotBlank(message = "{validation.nickname.required}")
    @Size(max = 50, message = "{validation.nickname.max}")
    String name
) { }
