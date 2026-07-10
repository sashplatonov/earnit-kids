package com.sashplatonov.earnit.kids.service.family.dashboard;

import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.dto.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyDashboardQueryService {

    OperationResult<FamilyDashboardShellResponse> loadFamilyShellData(String familyId, Integer childId,
                                                                      boolean adminSession);

    OperationResult<FamilyDashboardDetailResponse> loadFamilyDetailData(String familyId, Integer childId,
                                                                        boolean adminSession);

    OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession);
}
