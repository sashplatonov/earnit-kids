package com.sashplatonov.earnit.kids.service;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FamilyActionServiceImpl implements FamilyActionService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FamilyService familyService;
    private final TimeProvider timeProvider;

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> completeTask(String familyId, int childId, long taskId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure("Child not found");
        }

        Optional<TaskEntity> task = findActiveTask(familyDbId.get(), childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure("Task not found");
        }

        child.get().setBalance(child.get().getBalance() + task.get().getCoins());
        historyRepository.persist(buildTaskHistory(familyDbId.get(), childId, task.get()));
        return familyService.loadFamilyData(familyId, childId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> requestTaskCompletion(String familyId, int childId, long taskId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure("Child not found");
        }

        Optional<TaskEntity> task = findActiveTask(familyDbId.get(), childId, taskId);
        if (task.isEmpty()) {
            return OperationResult.failure("Task not found");
        }

        purchaseRequestRepository.persist(buildTaskRequest(familyDbId.get(), childId, task.get()));
        return familyService.loadFamilyData(familyId, childId, false);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> purchaseItem(String familyId, int childId, long itemId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure("Child not found");
        }

        Optional<ShopItemEntity> item = findActiveItem(familyDbId.get(), childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure("Shop item not found");
        }

        if (child.get().getBalance() < item.get().getPrice()) {
            return OperationResult.failure("Insufficient balance");
        }

        child.get().setBalance(child.get().getBalance() - item.get().getPrice());
        historyRepository.persist(buildShopHistory(familyDbId.get(), childId, item.get()));
        return familyService.loadFamilyData(familyId, childId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> requestItemPurchase(String familyId, int childId, long itemId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure("Child not found");
        }

        Optional<ShopItemEntity> item = findActiveItem(familyDbId.get(), childId, itemId);
        if (item.isEmpty()) {
            return OperationResult.failure("Shop item not found");
        }

        purchaseRequestRepository.persist(buildPurchaseRequest(familyDbId.get(), childId, item.get()));
        return familyService.loadFamilyData(familyId, childId, false);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> approveRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<PurchaseRequestEntity> request = findFamilyRequest(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure("Request not found");
        }
        if (!isPending(request.get())) {
            return OperationResult.failure("Request is already processed");
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), request.get().getChildId());
        if (child.isEmpty()) {
            return OperationResult.failure("Child not found");
        }

        if (isPurchaseRequest(request.get())) {
            if (child.get().getBalance() < request.get().getCoins()) {
                return OperationResult.failure("Insufficient balance");
            }
            child.get().setBalance(child.get().getBalance() - request.get().getCoins());
        } else {
            child.get().setBalance(child.get().getBalance() + request.get().getCoins());
        }

        historyRepository.persist(buildRequestHistory(familyDbId.get(), request.get()));
        request.get().setStatus("approved");
        int responseChildId = resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        return familyService.loadFamilyData(familyId, responseChildId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> rejectRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<PurchaseRequestEntity> request = findFamilyRequest(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure("Request not found");
        }
        if (!isPending(request.get())) {
            return OperationResult.failure("Request is already processed");
        }

        request.get().setStatus("rejected");
        int responseChildId = resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        return familyService.loadFamilyData(familyId, responseChildId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> deleteRequest(String familyId, Integer currentChildId, long requestId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<PurchaseRequestEntity> request = findFamilyRequest(familyDbId.get(), requestId);
        if (request.isEmpty()) {
            return OperationResult.failure("Request not found");
        }

        int responseChildId = resolveResponseChildId(familyDbId.get(), currentChildId, request.get().getChildId());
        purchaseRequestRepository.delete(request.get());
        return familyService.loadFamilyData(familyId, responseChildId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> deleteHistoryEntry(String familyId, int childId, long historyEntryId) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure("Child not found");
        }

        Optional<HistoryEntryEntity> historyEntry = findHistoryEntry(familyDbId.get(), childId, historyEntryId);
        if (historyEntry.isEmpty()) {
            return OperationResult.failure("History entry not found");
        }

        int delta = "earn".equals(historyEntry.get().getType())
            ? -historyEntry.get().getAmount()
            : historyEntry.get().getAmount();
        child.get().setBalance(child.get().getBalance() + delta);
        historyRepository.delete(historyEntry.get());
        return familyService.loadFamilyData(familyId, childId, true);
    }

    @Override
    @Transactional
    public OperationResult<FamilyDataResponse> adjustBalance(String familyId, int childId, int amount, String description) {
        Optional<Integer> familyDbId = familyRepository.getDbId(familyId);
        if (familyDbId.isEmpty()) {
            return OperationResult.failure("Family not found");
        }
        if (amount == 0) {
            return OperationResult.failure("Amount must not be zero");
        }

        Optional<ChildEntity> child = findFamilyChild(familyDbId.get(), childId);
        if (child.isEmpty()) {
            return OperationResult.failure("Child not found");
        }

        child.get().setBalance(child.get().getBalance() + amount);
        historyRepository.persist(buildAdjustmentHistory(familyDbId.get(), childId, amount, description));
        return familyService.loadFamilyData(familyId, childId, true);
    }

    private Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }

    private Optional<TaskEntity> findActiveTask(int familyDbId, int childId, long taskId) {
        return taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false",
            familyDbId,
            childId,
            taskId
        ).firstResultOptional();
    }

    private Optional<ShopItemEntity> findActiveItem(int familyDbId, int childId, long itemId) {
        return shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false",
            familyDbId,
            childId,
            itemId
        ).firstResultOptional();
    }

    private Optional<PurchaseRequestEntity> findFamilyRequest(int familyDbId, long requestId) {
        return purchaseRequestRepository.findByIdOptional(requestId)
            .filter(request -> request.getFamilyId() == familyDbId);
    }

    private Optional<HistoryEntryEntity> findHistoryEntry(int familyDbId, int childId, long historyEntryId) {
        return historyRepository.find(
            "familyId = ?1 AND childId = ?2 AND externalId = ?3",
            familyDbId,
            childId,
            historyEntryId
        ).firstResultOptional();
    }

    private int resolveResponseChildId(int familyDbId, Integer currentChildId, int fallbackChildId) {
        if (currentChildId != null && findFamilyChild(familyDbId, currentChildId).isPresent()) {
            return currentChildId;
        }
        return fallbackChildId;
    }

    private boolean isPending(PurchaseRequestEntity request) {
        return request.getStatus() == null || "pending".equals(request.getStatus());
    }

    private boolean isPurchaseRequest(PurchaseRequestEntity request) {
        return "shop_purchase".equals(request.getRequestType());
    }

    private PurchaseRequestEntity buildTaskRequest(int familyDbId, int childId, TaskEntity task) {
        return PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .taskId(task.getTaskId())
            .taskName(task.getName())
            .coins(task.getCoins())
            .status("pending")
            .requestType("earn")
            .moneyAmount(0)
            .createdAt(now())
            .build();
    }

    private PurchaseRequestEntity buildPurchaseRequest(int familyDbId, int childId, ShopItemEntity item) {
        return PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .taskId(item.getItemId())
            .taskName(item.getName())
            .itemId(item.getItemId())
            .coins(item.getPrice())
            .status("pending")
            .requestType("shop_purchase")
            .moneyAmount(item.getMoneyLimit() != null ? item.getMoneyLimit() : 0)
            .createdAt(now())
            .build();
    }

    private HistoryEntryEntity buildTaskHistory(int familyDbId, int childId, TaskEntity task) {
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type("earn")
            .amount(task.getCoins())
            .description(task.getName())
            .moneyAmount(0)
            .relatedId(task.getTaskId())
            .groupName(task.getGroupName())
            .comment(task.getComment())
            .createdAt(now())
            .build();
    }

    private HistoryEntryEntity buildShopHistory(int familyDbId, int childId, ShopItemEntity item) {
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type("spend")
            .amount(item.getPrice())
            .description(item.getName())
            .moneyAmount(item.getMoneyLimit() != null ? item.getMoneyLimit() : 0)
            .relatedId(item.getItemId())
            .groupName(item.getGroupName())
            .comment(item.getComment())
            .createdAt(now())
            .build();
    }

    private HistoryEntryEntity buildRequestHistory(int familyDbId, PurchaseRequestEntity request) {
        if (isPurchaseRequest(request)) {
            Optional<ShopItemEntity> item = request.getItemId() == null
                ? Optional.empty()
                : findActiveItem(familyDbId, request.getChildId(), request.getItemId());
            return HistoryEntryEntity.builder()
                .familyId(familyDbId)
                .childId(request.getChildId())
                .externalId(nextExternalId())
                .type("spend")
                .amount(request.getCoins())
                .description(item.map(ShopItemEntity::getName).orElse(request.getTaskName()))
                .moneyAmount(request.getMoneyAmount())
                .relatedId(request.getItemId() != null ? request.getItemId() : request.getTaskId())
                .groupName(item.map(ShopItemEntity::getGroupName).orElse(null))
                .comment(item.map(ShopItemEntity::getComment).orElse(null))
                .createdAt(now())
                .build();
        }

        Optional<TaskEntity> task = request.getTaskId() == null
            ? Optional.empty()
            : findActiveTask(familyDbId, request.getChildId(), request.getTaskId());
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(request.getChildId())
            .externalId(nextExternalId())
            .type("earn")
            .amount(request.getCoins())
            .description(task.map(TaskEntity::getName).orElse(request.getTaskName()))
            .moneyAmount(0)
            .relatedId(request.getTaskId())
            .groupName(task.map(TaskEntity::getGroupName).orElse(null))
            .comment(task.map(TaskEntity::getComment).orElse(null))
            .createdAt(now())
            .build();
    }

    private HistoryEntryEntity buildAdjustmentHistory(int familyDbId, int childId, int amount, String description) {
        String normalizedDescription = description != null && !description.isBlank()
            ? description.trim()
            : amount > 0 ? "Начисление" : "Списание";
        return HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(nextExternalId())
            .type(amount > 0 ? "earn" : "spend")
            .amount(Math.abs(amount))
            .description(normalizedDescription)
            .moneyAmount(0)
            .createdAt(now())
            .build();
    }

    private Instant now() {
        return timeProvider.now();
    }

    private long nextExternalId() {
        long value = timeProvider.now().toEpochMilli();
        return value > 0 ? value : 1L;
    }
}