package com.sashplatonov.earnit.kids.family.api.response;

import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.MembershipStatus;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record ParentMembershipDto(
    @Schema(description = "Membership identifier")
    Integer id,
    @Schema(description = "Parent email address")
    String email,
    @Schema(description = "Family-specific parent display name")
    String displayName,
    @Schema(description = "Verified Telegram user identifier")
    Long telegramUserId,
    @Schema(description = "Verified Telegram username")
    String telegramUsername,
    @Schema(description = "Verified Telegram display name")
    String telegramDisplayName,
    @Schema(description = "Membership permission")
    FamilyParentMembershipEntity.Permission permission,
    @Schema(description = "Membership status")
    MembershipStatus status
) {
    public ParentMembershipDto(
        Integer id,
        String email,
        FamilyParentMembershipEntity.Permission permission,
        MembershipStatus status) {
        this(id, email, null, null, null, null, permission, status);
    }
}
