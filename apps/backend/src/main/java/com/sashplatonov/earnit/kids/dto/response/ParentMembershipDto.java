package com.sashplatonov.earnit.kids.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ParentMembershipDto(
    @Schema(description = "Membership identifier")
    Integer id,
    @Schema(description = "Parent email address")
    String email,
    @Schema(description = "Membership permission")
    String permission,
    @Schema(description = "Membership status")
    String status
) {
}
