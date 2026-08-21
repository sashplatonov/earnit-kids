package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTelegramParentInviteRequest(
    @NotBlank @Size(max = 255) String parentName
) { }
