package com.sashplatonov.earnit.kids.family.application.membership;

import com.sashplatonov.earnit.kids.family.api.response.ParentMembershipDto;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyAdminTransferRequestEntity;
import com.sashplatonov.earnit.kids.family.domain.model.membership.FamilyParentMembershipEntity;
import com.sashplatonov.earnit.kids.util.OperationResult;

record FamilyParentTransferRequestContext(
    Integer familyDbId,
    FamilyAdminTransferRequestEntity request,
    FamilyParentMembershipEntity actor,
    OperationResult<ParentMembershipDto> error) {
  static FamilyParentTransferRequestContext error(OperationResult<ParentMembershipDto> error) {
    return new FamilyParentTransferRequestContext(null, null, null, error);
  }
}
