package com.sashplatonov.earnit.kids.family.application.analytics;

import com.sashplatonov.earnit.kids.family.api.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface AnalyticsService {

    OperationResult<AnalyticsResponse> getAnalyticsData(String familyId, Integer childId, String timeframe);

    void invalidateCache(String familyId);
}
