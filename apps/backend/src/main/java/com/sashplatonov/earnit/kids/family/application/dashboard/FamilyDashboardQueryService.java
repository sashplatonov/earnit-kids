package com.sashplatonov.earnit.kids.family.application.dashboard;

import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardDetailResponse;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDashboardShellResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyDashboardQueryService {

    OperationResult<FamilyDashboardShellResponse> loadFamilyShellData(String familyId, Integer childId,
                                                                      boolean adminSession);

    OperationResult<FamilyDashboardDetailResponse> loadFamilyDetailData(String familyId, Integer childId,
                                                                        boolean adminSession);

    OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean adminSession);
}
