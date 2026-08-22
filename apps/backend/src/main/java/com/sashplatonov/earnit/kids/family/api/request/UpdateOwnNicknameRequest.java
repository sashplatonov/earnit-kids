package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOwnNicknameRequest(
    @NotBlank(message = "{validation.nickname.required}")
    @Size(max = 50, message = "{validation.nickname.max}")
    String nickname
) { }
