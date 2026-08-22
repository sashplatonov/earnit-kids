package com.sashplatonov.earnit.kids.family.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record AddParentMembershipRequest(
    @Schema(description = "Parent email address")
    @NotBlank @Email String email,
    @Schema(description = "Membership permission: viewer, editor, or family_admin")
    @NotBlank String permission
) {
}
