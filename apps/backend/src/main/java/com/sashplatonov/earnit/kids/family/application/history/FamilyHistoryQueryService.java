package com.sashplatonov.earnit.kids.family.application.history;

import com.sashplatonov.earnit.kids.family.api.response.PaginatedHistory;
import com.sashplatonov.earnit.kids.family.api.response.PaginatedRequests;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyHistoryQueryService {

    OperationResult<PaginatedHistory> getHistory(String familyId, int childId, int page, int limit);

    OperationResult<PaginatedRequests> getRequests(String familyId, int page, int limit);
}
