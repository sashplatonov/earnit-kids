package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SelectFamilyRequest(
    @NotBlank String email,
    @NotBlank String familyId
) {
}
