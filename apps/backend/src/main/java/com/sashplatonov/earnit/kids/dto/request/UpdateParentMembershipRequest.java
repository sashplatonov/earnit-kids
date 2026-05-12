package com.sashplatonov.earnit.kids.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateParentMembershipRequest(
    @Schema(description = "Membership permission: viewer, editor, or family_admin")
    @NotBlank String permission
) {
}
