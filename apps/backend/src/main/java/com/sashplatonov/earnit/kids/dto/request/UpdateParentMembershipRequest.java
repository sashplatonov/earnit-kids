package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateParentMembershipRequest(
    @NotBlank String permission
) {}