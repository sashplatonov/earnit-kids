package com.sashplatonov.earnit.kids.service.family.action;

import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;

final class FamilyActionRewardGoalService {

    private final FamilyActionSupportService supportService;
    private final ChildRepository childRepository;

    FamilyActionRewardGoalService(FamilyActionSupportService supportService, ChildRepository childRepository) {
        this.supportService = supportService;
        this.childRepository = childRepository;
    }

    OperationResult<FamilyDataResponse> setRewardGoal(String familyId, int childId, Long itemId) {
        var familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }
        if (supportService.findFamilyChild(familyDbId.get(), childId).isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (itemId != null && supportService.findActiveItem(familyDbId.get(), childId, itemId).isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        childRepository.updateRewardGoal(childId, itemId);
        return supportService.loadFamilyData(familyId, childId, false);
    }
}
