package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyAdminTransferRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.identity.domain.model.ParentAccountEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FamilyParentMembershipPresentation {
  private FamilyParentMembershipPresentation() {}

  static void enrichWithPendingTransferRequest(
      List<ParentMembershipDto> dtos,
      FamilyAdminTransferRequestEntity request,
      Map<Integer, FamilyParentMembershipEntity> membershipsById,
      Map<Integer, ParentAccountEntity> parentsById) {
    String actorName = membershipName(request.getActorMembershipId(), membershipsById, parentsById);
    String targetName = membershipName(request.getTargetMembershipId(), membershipsById, parentsById);
    for (int i = 0; i < dtos.size(); i++) {
      ParentMembershipDto dto = dtos.get(i);
      if (dto.id() != null
          && (dto.id().equals(request.getActorMembershipId())
              || dto.id().equals(request.getTargetMembershipId()))) {
        String role = dto.id().equals(request.getActorMembershipId()) ? "actor" : "target";
        dtos.set(
            i,
            new ParentMembershipDto(
                dto.id(),
                dto.email(),
                dto.displayName(),
                dto.telegramUserId(),
                dto.telegramUsername(),
                dto.telegramDisplayName(),
                dto.permission(),
                dto.status(),
                dto.invitationStatus(),
                "pending",
                actorName,
                targetName,
                request.getId(),
                role));
      }
    }
  }

  private static String membershipName(
      Integer membershipId,
      Map<Integer, FamilyParentMembershipEntity> membershipsById,
      Map<Integer, ParentAccountEntity> parentsById) {
    FamilyParentMembershipEntity membership = membershipsById.get(membershipId);
    if (membership == null) {
      return null;
    }
    if (membership.getDisplayName() != null && !membership.getDisplayName().isBlank()) {
      return membership.getDisplayName();
    }
    return Optional.ofNullable(parentsById.get(membership.getParentAccountId()))
        .map(ParentAccountEntity::getEmail)
        .orElse(null);
  }
}
