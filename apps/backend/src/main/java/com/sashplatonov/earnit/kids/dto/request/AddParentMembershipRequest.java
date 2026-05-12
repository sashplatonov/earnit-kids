package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddParentMembershipRequest(
    @NotBlank @Email String email,
    @NotBlank String permission
) {}