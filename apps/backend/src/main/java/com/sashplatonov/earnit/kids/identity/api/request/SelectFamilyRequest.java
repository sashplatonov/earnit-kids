package com.sashplatonov.earnit.kids.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record SelectFamilyRequest(
    @NotBlank String email,
    @NotBlank String familyId
) {
}
