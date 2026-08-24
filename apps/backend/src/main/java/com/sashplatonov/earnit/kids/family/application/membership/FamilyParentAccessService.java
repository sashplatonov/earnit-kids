package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.List;

public interface FamilyParentAccessService {

    OperationResult<List<ParentMembershipDto>> listMemberships(String familyId);

    OperationResult<ParentMembershipDto> addMembership(
        String familyId, String email, String permission, String invitedByEmail);

    OperationResult<ParentMembershipDto> updateMembership(
        Integer membershipId, String permission, String familyId);

    OperationResult<Void> removeMembership(
        Integer membershipId, String familyId, Integer actorParentAccountId, String actorEmail);

    OperationResult<ParentMembershipDto> setMembershipActive(
        Integer membershipId, boolean active, String familyId, Integer actorParentAccountId, String actorEmail);

    OperationResult<ParentMembershipDto> transferAdmin(
        Integer membershipId, String familyId, Integer actorParentAccountId, String actorEmail);

    OperationResult<ParentMembershipDto> createTransferRequest(
        Integer targetMembershipId, String familyId, Integer actorParentAccountId, String actorEmail);

    OperationResult<ParentMembershipDto> acceptTransferRequest(
        Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail);

    OperationResult<ParentMembershipDto> declineTransferRequest(
        Integer requestId, String familyId, Integer actorParentAccountId, String actorEmail);
}
