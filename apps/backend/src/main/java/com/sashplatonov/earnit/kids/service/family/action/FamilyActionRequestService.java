package com.sashplatonov.earnit.kids.service.family.action;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.service.event.ApplicationEventPublisher;
import com.sashplatonov.earnit.kids.domain.model.ApplicationOutboxEventType;

import java.util.Objects;
import java.util.Optional;

final class FamilyActionRequestService {

    private final FamilyActionSupportService supportService;
    private final FamilyActionHistoryFactory historyFactory;
    private final FamilyActionFrequencyService frequencyService;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final HistoryRepository historyRepository;
    private final FamilyActionEventSupport eventSupport;

    FamilyActionRequestService(FamilyActionSupportService supportService,
                               FamilyActionHistoryFactory historyFactory,
                               FamilyActionFrequencyService frequencyService,
                               PurchaseRequestRepository purchaseRequestRepository,
                               HistoryRepository historyRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.supportService = supportService;
        this.historyFactory = historyFactory;
        this.frequencyService = frequencyService;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.historyRepository = historyRepository;
        this.eventSupport = new FamilyActionEventSupport(eventPublisher);
    }

    OperationResult<FamilyDataResponse> requestTaskCompletion(String familyId,
                                                              int childId,
                                                              long taskId,
                                                              String note) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        Optional<TaskEntity> task = supportService.findActiveTask(familyDbId.get(), childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("tasks.notFound"));
        }

        String taskLimitError = frequencyService.validateTaskRequestLimit(familyDbId.get(), childId, task.get());
        if (taskLimitError != null) {
            return OperationResult.failure("TASK_REQUEST_LIMIT_REACHED", taskLimitError);
        }

        OperationResult<String> normalizedNoteResult = FamilyActionRequestSupport.normalizeNote(note);
        if (normalizedNoteResult instanceof OperationResult.Failure<String> failure) {
            return OperationResult.failure(failure.errorCode(), failure.message());
        }

        String normalizedNote = normalizedNoteResult instanceof OperationResult.Success<String> success
            ? success.value()
            : null;

        PurchaseRequestEntity request = buildTaskRequest(familyDbId.get(), childId, task.get(), normalizedNote);
        purchaseRequestRepository.persist(request);
        purchaseRequestRepository.flush();
        publish(ApplicationOutboxEventType.TASK_REQUEST_CREATED, request, 0, null);
        return supportService.loadFamilyData(familyId, childId, false);
    }

    OperationResult<FamilyDataResponse> requestItemPurchase(String familyId,
                                                            int childId,
                                                            long itemId,
                                                            String note) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        Optional<ShopItemEntity> item = supportService.findActiveItem(familyDbId.get(), childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("shop.itemNotFound"));
        }

        String itemLimitError = frequencyService.validateItemRequestLimit(familyDbId.get(), childId, item.get());
        if (itemLimitError != null) {
            return OperationResult.failure("ITEM_REQUEST_LIMIT_REACHED", itemLimitError);
        }

        OperationResult<String> normalizedNoteResult = FamilyActionRequestSupport.normalizeNote(note);
        if (normalizedNoteResult instanceof OperationResult.Failure<String> failure) {
            return OperationResult.failure(failure.errorCode(), failure.message());
        }

        String normalizedNote = normalizedNoteResult instanceof OperationResult.Success<String> success
            ? success.value()
            : null;

        PurchaseRequestEntity request = buildPurchaseRequest(familyDbId.get(), childId, item.get(), normalizedNote);
        purchaseRequestRepository.persist(request);
        purchaseRequestRepository.flush();
        publish(ApplicationOutboxEventType.REWARD_REQUEST_CREATED, request, 0, null);
        return supportService.loadFamilyData(familyId, childId, false);
    }

    OperationResult<FamilyDataResponse> approveRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<PurchaseRequestEntity> request = supportService.findFamilyRequestForUpdate(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("requests.notFound"));
        }
        if (!FamilyActionRequestSupport.isPending(request.get())) {
            return OperationResult.failure(BackendMessages.message("requests.alreadyProcessed"));
        }

        Optional<ChildEntity> child = supportService.findFamilyChildForUpdate(familyDbId.get(), request.get().getChildId());
        if (child.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.childNotFound"));
        }
        if (supportService.isInactive(child.get())) {
            return OperationResult.failure(BackendMessages.message("family.childInactive"));
        }

        Optional<ShopItemEntity> item = request.get().getItemId() == null
            ? Optional.empty()
            : supportService.findActiveItem(familyDbId.get(), request.get().getChildId(), request.get().getItemId());
        Optional<TaskEntity> task = request.get().getTaskId() == null
            ? Optional.empty()
            : supportService.findActiveTask(familyDbId.get(), request.get().getChildId(), request.get().getTaskId());

        if (FamilyActionRequestSupport.isPurchase(request.get())) {
            long rewardLimit = child.get().getDailyRewardLimit();
            if (rewardLimit > 0
                && supportService.dailyRewardSpend(request.get().getChildId(),
                    supportService.startOfFamilyDay(familyDbId.get(), historyFactory.now())) + request.get().getCoins() > rewardLimit) {
                return OperationResult.failure(BackendMessages.message("balance.rewardLimitReached"));
            }
            if (child.get().getBalance() < request.get().getCoins()) {
                return OperationResult.failure(BackendMessages.message("balance.insufficient"));
            }
            child.get().setBalance(child.get().getBalance() - request.get().getCoins());
        } else {
            child.get().setBalance(child.get().getBalance() + request.get().getCoins());
        }

        historyRepository.persist(historyFactory.buildRequestHistory(familyDbId.get(), request.get(), item, task));
        request.get().setStatus(PurchaseRequestStatus.approved);
        publish(FamilyActionRequestSupport.isPurchase(request.get())
                ? ApplicationOutboxEventType.REWARD_APPROVED : ApplicationOutboxEventType.TASK_APPROVED,
            request.get(), FamilyActionRequestSupport.isPurchase(request.get())
                ? -request.get().getCoins() : request.get().getCoins(),
            child.get().getBalance());
        int responseChildId = supportService.resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        return supportService.loadFamilyData(familyId, responseChildId, true);
    }

    OperationResult<FamilyDataResponse> rejectRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<PurchaseRequestEntity> request = supportService.findFamilyRequestForUpdate(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("requests.notFound"));
        }
        if (!FamilyActionRequestSupport.isPending(request.get())) {
            return OperationResult.failure(BackendMessages.message("requests.alreadyProcessed"));
        }

        request.get().setStatus(PurchaseRequestStatus.rejected);
        publish(FamilyActionRequestSupport.isPurchase(request.get())
                ? ApplicationOutboxEventType.REWARD_REJECTED : ApplicationOutboxEventType.TASK_REJECTED,
            request.get(), 0, null);
        int responseChildId = supportService.resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        return supportService.loadFamilyData(familyId, responseChildId, true);
    }

    OperationResult<FamilyDataResponse> deleteRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = supportService.getFamilyDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("family.familyNotFound"));
        }

        Optional<PurchaseRequestEntity> request = supportService.findFamilyRequest(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure(BackendMessages.message("requests.notFound"));
        }

        boolean isChildDeletingOwnRequest = currentChildId != null
            && Objects.equals(request.get().getChildId(), currentChildId);
        if (isChildDeletingOwnRequest && request.get().getStatus() == PurchaseRequestStatus.approved) {
            return OperationResult.failure(BackendMessages.message("requests.alreadyProcessed"));
        }

        int responseChildId = supportService.resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        if (isChildDeletingOwnRequest && FamilyActionRequestSupport.isPending(request.get())) {
            // EXPLAIN: A child cancelling their own pending request soft-cancels it (status = cancelled) so it stays visible in history instead of being physically deleted; rejected requests and parent deletes keep the physical-delete behavior.
            request.get().setStatus(PurchaseRequestStatus.cancelled);
        } else {
            purchaseRequestRepository.delete(request.get());
        }
        return supportService.loadFamilyData(familyId, responseChildId, true);
    }

    private PurchaseRequestEntity buildTaskRequest(int familyDbId, int childId, TaskEntity task, String note) {
        return PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(historyFactory.nextExternalId())
            .taskId(task.getTaskId())
            .taskName(task.getName())
            .coins(task.getCoins())
            .status(PurchaseRequestStatus.pending)
            .requestType(PurchaseRequestType.earn)
            .moneyAmount(0)
            .note(note)
            .createdAt(historyFactory.now())
            .build();
    }

    private PurchaseRequestEntity buildPurchaseRequest(int familyDbId, int childId, ShopItemEntity item, String note) {
        return PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(historyFactory.nextExternalId())
            .taskId(item.getItemId())
            .taskName(item.getName())
            .itemId(item.getItemId())
            .coins(item.getPrice())
            .status(PurchaseRequestStatus.pending)
            .requestType(PurchaseRequestType.shop_purchase)
            .moneyAmount(item.getMoneyLimit() != null ? item.getMoneyLimit() : 0)
            .note(note)
            .createdAt(historyFactory.now())
            .build();
    }

    private void publish(ApplicationOutboxEventType type, PurchaseRequestEntity request, int delta, Integer balance) {
        eventSupport.publish(type, request.getFamilyId(), request.getChildId(), request.getId(), delta, balance,
            historyFactory.now());
    }
}
