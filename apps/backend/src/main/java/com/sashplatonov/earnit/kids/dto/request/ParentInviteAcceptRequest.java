package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ParentInviteAcceptRequest(
    @NotBlank String token,
    @NotBlank String email,
    @NotBlank String initData
) { }
