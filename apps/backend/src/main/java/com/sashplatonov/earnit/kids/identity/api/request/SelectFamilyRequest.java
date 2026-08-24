package com.sashplatonov.earnit.kids.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record SelectFamilyRequest(
    String email,
    @NotBlank String familyId
) {
    public SelectFamilyRequest(String familyId) {
        this(null, familyId);
    }

    public SelectFamilyRequest(String email, String familyId) {
        this.email = email;
        this.familyId = familyId;
    }
}
