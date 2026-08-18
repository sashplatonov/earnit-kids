package com.sashplatonov.earnit.kids.service.family.action;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.ShopItemEntity;
import com.sashplatonov.earnit.kids.domain.model.TaskEntity;
import com.sashplatonov.earnit.kids.dto.response.FamilyDataResponse;
import com.sashplatonov.earnit.kids.dto.response.ImportValidationErrorItem;
import com.sashplatonov.earnit.kids.dto.response.ImportValidationErrorResponse;
import com.sashplatonov.earnit.kids.exception.ImportValidationException;
import com.sashplatonov.earnit.kids.i18n.BackendMessages;
import com.sashplatonov.earnit.kids.repository.ChildRepository;
import com.sashplatonov.earnit.kids.repository.FamilyRepository;
import com.sashplatonov.earnit.kids.repository.HistoryRepository;
import com.sashplatonov.earnit.kids.repository.PurchaseRequestRepository;
import com.sashplatonov.earnit.kids.repository.ShopItemRepository;
import com.sashplatonov.earnit.kids.repository.TaskRepository;
import com.sashplatonov.earnit.kids.util.OperationResult;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.List;
import java.util.Optional;

import com.sashplatonov.earnit.kids.service.family.FamilyService;
final class FamilyActionSupportService {

    private final FamilyRepository familyRepository;
    private final ChildRepository childRepository;
    private final TaskRepository taskRepository;
    private final ShopItemRepository shopItemRepository;
    private final HistoryRepository historyRepository;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final FamilyService familyService;

    FamilyActionSupportService(FamilyRepository familyRepository,
                               ChildRepository childRepository,
                               TaskRepository taskRepository,
                               ShopItemRepository shopItemRepository,
                               HistoryRepository historyRepository,
                               PurchaseRequestRepository purchaseRequestRepository,
                               FamilyService familyService) {
        this.familyRepository = familyRepository;
        this.childRepository = childRepository;
        this.taskRepository = taskRepository;
        this.shopItemRepository = shopItemRepository;
        this.historyRepository = historyRepository;
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.familyService = familyService;
    }

    Optional<Integer> getFamilyDbId(String familyId) {
        return familyRepository.getDbId(familyId);
    }

    Optional<ChildEntity> findFamilyChild(int familyDbId, int childId) {
        return childRepository.findByIdOptional(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }

    Optional<ChildEntity> findFamilyChildForUpdate(int familyDbId, int childId) {
        return childRepository.findByIdForUpdate(childId)
            .filter(child -> Objects.equals(child.getFamilyDbId(), familyDbId));
    }

    boolean isInactive(ChildEntity child) {
        return !com.sashplatonov.earnit.kids.domain.model.ChildStatus.ACTIVE.name()
            .equals(child.getStatus());
    }

    long dailyRewardSpend(int childId, java.time.Instant since) {
        return historyRepository.sumRewardSpendSince(childId, since);
    }

    // EXPLAIN: Resolve the start of the family's local day so daily reward limits
    // EXPLAIN: reset at midnight in the family timezone, not UTC.
    Instant startOfFamilyDay(int familyDbId, Instant now) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(familyRepository.getTimezone(familyDbId).orElse("UTC"));
        } catch (DateTimeException ignored) {
            zoneId = ZoneId.of("UTC");
        }
        return now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant();
    }

    Optional<TaskEntity> findActiveTask(int familyDbId, int childId, long taskId) {
        return taskRepository.find(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3 AND deleted = false AND active = true",
            familyDbId,
            childId,
            taskId
        ).firstResultOptional();
    }

    Optional<ShopItemEntity> findActiveItem(int familyDbId, int childId, long itemId) {
        return shopItemRepository.find(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3 AND deleted = false AND active = true",
            familyDbId,
            childId,
            itemId
        ).firstResultOptional();
    }

    Optional<PurchaseRequestEntity> findFamilyRequest(int familyDbId, long requestId) {
        return purchaseRequestRepository.findByIdOptional(requestId)
            .filter(request -> request.getFamilyId() == familyDbId);
    }

    Optional<PurchaseRequestEntity> findFamilyRequestForUpdate(int familyDbId, long requestId) {
        return purchaseRequestRepository.findByIdForUpdate(requestId)
            .filter(request -> request.getFamilyId() == familyDbId);
    }

    Optional<HistoryEntryEntity> findHistoryEntry(int familyDbId, int childId, long historyEntryId) {
        return historyRepository.find(
            "familyId = ?1 AND childId = ?2 AND externalId = ?3",
            familyDbId,
            childId,
            historyEntryId
        ).firstResultOptional();
    }

    List<TaskEntity> findTaskEntities(int familyDbId, int childId) {
        return taskRepository.find("familyId = ?1 AND childId = ?2", familyDbId, childId).list();
    }

    int resolveResponseChildId(int familyDbId, Integer currentChildId, int fallbackChildId) {
        if (currentChildId != null && findFamilyChild(familyDbId, currentChildId).isPresent()) {
            return currentChildId;
        }
        return fallbackChildId;
    }

    OperationResult<FamilyDataResponse> loadFamilyData(String familyId, Integer childId, boolean isAdmin) {
        return familyService.loadFamilyData(familyId, childId, isAdmin);
    }

    FamilyDataResponse loadRefreshedFamilyData(String familyId, Integer childId, boolean isAdmin) {
        OperationResult<FamilyDataResponse> result = familyService.loadFamilyData(familyId, childId, isAdmin);
        return switch (result) {
            case OperationResult.Success<FamilyDataResponse> success -> success.value();
            case OperationResult.Failure<FamilyDataResponse> failure ->
                throw new IllegalStateException("Failed to reload family data after import: " + failure.message());
        };
    }

    int requireImportFamilyDbId(String familyId) {
        return familyRepository.getDbId(familyId)
            .orElseThrow(() -> new ImportValidationException(ImportValidationErrorResponse.of(
                BackendMessages.message("family.familyNotFound"),
                List.of(new ImportValidationErrorItem(
                    0,
                    "familyId",
                    BackendMessages.message("family.familyNotFound")
                ))
            )));
    }

    void requireImportChild(int familyDbId, int childId) {
        if (findFamilyChild(familyDbId, childId).isEmpty()) {
            throw new ImportValidationException(ImportValidationErrorResponse.of(
                BackendMessages.message("family.childNotFound"),
                List.of(new ImportValidationErrorItem(
                    0,
                    "childId",
                    BackendMessages.message("family.childNotFound")
                ))
            ));
        }
    }

    long nextTaskBusinessId(int familyDbId, int childId) {
        return taskRepository.find("familyId = ?1 AND childId = ?2", familyDbId, childId)
            .list()
            .stream()
            .map(TaskEntity::getTaskId)
            .max(Long::compareTo)
            .orElse(0L) + 1;
    }

    long nextShopItemBusinessId(int familyDbId, int childId) {
        return shopItemRepository.find("familyId = ?1 AND childId = ?2", familyDbId, childId)
            .list()
            .stream()
            .map(ShopItemEntity::getItemId)
            .max(Long::compareTo)
            .orElse(0L) + 1;
    }
}
