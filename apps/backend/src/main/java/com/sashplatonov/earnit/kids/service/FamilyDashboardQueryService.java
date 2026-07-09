package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyDashboardQueryService {

    OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession);
}
