package com.sashplatonov.earnit.kids.telegram.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTelegramParentInviteRequest(
    @NotBlank @Size(max = 255) String parentName
) { }
