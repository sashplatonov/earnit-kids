package com.sashplatonov.earnit.kids.service.family.action;

import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.request.BulkTaskActionRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.dto.request.ImportTasksRequest;
import com.sashplatonov.earnit.kids.util.OperationResult;

public interface FamilyActionService {

    OperationResult<FamilyDataResponse> completeTask(String familyId, int childId, long taskId);

    OperationResult<FamilyDataResponse> requestTaskCompletion(String familyId, int childId, long taskId, String note);

    OperationResult<FamilyDataResponse> purchaseItem(String familyId, int childId, long itemId);

    OperationResult<FamilyDataResponse> requestItemPurchase(String familyId, int childId, long itemId, String note);

    OperationResult<FamilyDataResponse> approveRequest(String familyId, Integer currentChildId, long requestId);

    OperationResult<FamilyDataResponse> rejectRequest(String familyId, Integer currentChildId, long requestId);

    OperationResult<FamilyDataResponse> deleteRequest(String familyId, Integer currentChildId, long requestId);

    OperationResult<FamilyDataResponse> deleteHistoryEntry(String familyId, int childId, long historyEntryId);

    OperationResult<FamilyDataResponse> adjustBalance(String familyId, int childId, int amount, String description);

    OperationResult<FamilyDataResponse> bulkTaskAction(String familyId, BulkTaskActionRequest request);

    FamilyDataResponse importTasks(String familyId, ImportTasksRequest request);

    FamilyDataResponse importShopItems(String familyId, ImportShopItemsRequest request);
}
