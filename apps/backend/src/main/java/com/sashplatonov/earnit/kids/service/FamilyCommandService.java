package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.util.Map;

public interface FamilyCommandService {

    OperationResult<FamilyDataResponse> saveFamilyData(String familyId, Integer childId,
                                                       Map<String, Object> payload,
                                                       boolean adminSession);
}
