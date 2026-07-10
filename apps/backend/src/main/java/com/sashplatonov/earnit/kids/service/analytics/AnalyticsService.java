package com.sashplatonov.earnit.kids.service.analytics;

import com.sashplatonov.earnit.kids.dto.response.AnalyticsResponse;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface AnalyticsService {

    OperationResult<AnalyticsResponse> getAnalyticsData(String familyId, Integer childId, String timeframe);

    void invalidateCache(String familyId);
}
