package com.sashplatonov.earnit.kids.family.application.action;

import com.sashplatonov.earnit.kids.family.api.request.BulkTaskActionRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportShopItemsRequest;
import com.sashplatonov.earnit.kids.family.api.request.ImportTasksRequest;
import com.sashplatonov.earnit.kids.family.api.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.child.ChildRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.family.FamilyRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.history.HistoryRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.request.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.ShopItemRepository;
import com.sashplatonov.earnit.kids.family.infrastructure.persistence.catalog.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.sashplatonov.earnit.kids.family.application.FamilyService;
import com.sashplatonov.earnit.kids.platform.application.observability.BackendKpiMetrics;
import com.sashplatonov.earnit.kids.service.event.ApplicationEventPublisher;
@ApplicationScoped
public class FamilyActionServiceImpl implements FamilyActionService {

    private final FamilyActionBalanceService balanceService;
    private final FamilyActionRequestService requestService;
    private final FamilyActionBulkService bulkService;
    private final FamilyActionImportService importService;
    private final BackendKpiMetrics backendKpiMetrics;

    public FamilyActionServiceImpl(FamilyRepository familyRepository,
                                   ChildRepository childRepository,
                                   TaskRepository taskRepository,
                                   ShopItemRepository shopItemRepository,
                                   HistoryRepository historyRepository,
                                   PurchaseRequestRepository purchaseRequestRepository,
                                   FamilyService familyService,
                                   TimeProvider timeProvider,
                                   FrequencyWindowService frequencyWindowService,
                                   BackendKpiMetrics backendKpiMetrics) {
        this(familyRepository, childRepository, taskRepository, shopItemRepository, historyRepository,
            purchaseRequestRepository, familyService, timeProvider, frequencyWindowService, backendKpiMetrics, null);
    }

    @Inject
    public FamilyActionServiceImpl(FamilyRepository familyRepository,
                                   ChildRepository childRepository,
                                   TaskRepository taskRepository,
                                   ShopItemRepository shopItemRepository,
                                   HistoryRepository historyRepository,
                                   PurchaseRequestRepository purchaseRequestRepository,
                                   FamilyService familyService,
                                   TimeProvider timeProvider,
                                   FrequencyWindowService frequencyWindowService,
                                   BackendKpiMetrics backendKpiMetrics,
                                   ApplicationEventPublisher eventPublisher) {
        FamilyActionSupportService supportService = new FamilyActionSupportService(
            familyRepository,
            childRepository,
            taskRepository,
            shopItemRepository,
            historyRepository,
            purchaseRequestRepository,
            familyService
        );
        FamilyActionHistoryFactory historyFactory = new FamilyActionHistoryFactory(timeProvider);
        FamilyActionFrequencyService frequencyService = new FamilyActionFrequencyService(
            purchaseRequestRepository,
            historyRepository,
            familyRepository,
            timeProvider,
            frequencyWindowService
        );
        this.balanceService = new FamilyActionBalanceService(supportService, historyFactory, historyRepository, eventPublisher);
        this.requestService = new FamilyActionRequestService(
            supportService,
            historyFactory,
            frequencyService,
            purchaseRequestRepository,
            historyRepository,
            eventPublisher
        );
        this.bulkService = new FamilyActionBulkService(supportService);
        this.importService = new FamilyActionImportService(supportService, frequencyService, taskRepository, shopItemRepository);
        this.backendKpiMetrics = backendKpiMetrics;
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> completeTask(String familyId, int childId, long taskId) {
        return backendKpiMetrics.recordResult("family_action", "complete_task",
            () -> balanceService.completeTask(familyId, childId, taskId));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> requestTaskCompletion(String familyId,
                                                                     int childId,
                                                                     long taskId,
                                                                     String note) {
        return backendKpiMetrics.recordResult("family_action", "request_task_completion",
            () -> requestService.requestTaskCompletion(familyId, childId, taskId, note));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> purchaseItem(String familyId, int childId, long itemId) {
        return backendKpiMetrics.recordResult("family_action", "purchase_item",
            () -> balanceService.purchaseItem(familyId, childId, itemId));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> requestItemPurchase(String familyId,
                                                                   int childId,
                                                                   long itemId,
                                                                   String note) {
        return backendKpiMetrics.recordResult("family_action", "request_item_purchase",
            () -> requestService.requestItemPurchase(familyId, childId, itemId, note));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> approveRequest(String familyId, Integer currentChildId, long requestId) {
        return backendKpiMetrics.recordResult("family_action", "approve_request",
            () -> requestService.approveRequest(familyId, currentChildId, requestId));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> rejectRequest(String familyId, Integer currentChildId, long requestId) {
        return backendKpiMetrics.recordResult("family_action", "reject_request",
            () -> requestService.rejectRequest(familyId, currentChildId, requestId));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> deleteRequest(String familyId, Integer currentChildId, long requestId) {
        return backendKpiMetrics.recordResult("family_action", "delete_request",
            () -> requestService.deleteRequest(familyId, currentChildId, requestId));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> deleteHistoryEntry(String familyId, int childId, long historyEntryId) {
        return backendKpiMetrics.recordResult("family_action", "delete_history_entry",
            () -> balanceService.deleteHistoryEntry(familyId, childId, historyEntryId));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> adjustBalance(String familyId,
                                                             int childId,
                                                             int amount,
                                                             String description) {
        return backendKpiMetrics.recordResult("family_action", "adjust_balance",
            () -> balanceService.adjustBalance(familyId, childId, amount, description));
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> bulkTaskAction(String familyId, BulkTaskActionRequest request) {
        return backendKpiMetrics.recordResult("family_action", "bulk_task_action",
            () -> bulkService.bulkTaskAction(familyId, request));
    }

    @Override
    @Transactional
    public FamilyDataResponse importTasks(String familyId, ImportTasksRequest request) {
        return backendKpiMetrics.recordValue("family_action", "import_tasks",
            () -> importService.importTasks(familyId, request));
    }

    @Override
    @Transactional
    public FamilyDataResponse importShopItems(String familyId, ImportShopItemsRequest request) {
        return backendKpiMetrics.recordValue("family_action", "import_shop_items",
            () -> importService.importShopItems(familyId, request));
    }
}
