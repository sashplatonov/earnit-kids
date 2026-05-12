package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.List;

public interface FamilyParentAccessService {

    OperationResult<List<ParentMembershipDto>> listMemberships(String familyId);

    OperationResult<ParentMembershipDto> addMembership(
        String familyId, String email, String permission, String invitedByEmail);

    OperationResult<ParentMembershipDto> updateMembership(
        Integer membershipId, String permission, String familyId);

    OperationResult<Void> removeMembership(Integer membershipId, String familyId);
}
