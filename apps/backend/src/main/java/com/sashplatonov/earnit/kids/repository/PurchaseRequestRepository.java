package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.service.SlowOperationDiagnostics;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PurchaseRequestRepository implements PanacheRepositoryBase<PurchaseRequestEntity, Long> {
    private final SlowOperationDiagnostics slowOperationDiagnostics;

    public long countPendingTaskRequestsInWindow(
        int familyDbId,
        int childId,
        long taskId,
        Instant startInclusive,
        Instant endExclusive
    ) {
        return count(
            "familyId = ?1 AND childId = ?2 AND taskId = ?3"
                + " AND status = ?4 AND createdAt >= ?5 AND createdAt < ?6",
            familyDbId,
            childId,
            taskId,
            "pending",
            startInclusive,
            endExclusive
        );
    }

    public long countPendingItemRequestsInWindow(
        int familyDbId,
        int childId,
        long itemId,
        Instant startInclusive,
        Instant endExclusive
    ) {
        return count(
            "familyId = ?1 AND childId = ?2 AND itemId = ?3"
                + " AND status = ?4 AND createdAt >= ?5 AND createdAt < ?6",
            familyDbId,
            childId,
            itemId,
            "pending",
            startInclusive,
            endExclusive
        );
    }

    public List<PurchaseRequestEntity> getRequests(int familyDbId, int limit, int offset) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getRequests",
            () -> find("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId)
                .range(offset, offset + limit - 1)
                .list(),
            "familyDbId",
            String.valueOf(familyDbId),
            "limit",
            String.valueOf(limit),
            "offset",
            String.valueOf(offset)
        );
    }

    public int getRequestsCount(int familyDbId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getRequestsCount",
            () -> (int) count("familyId = ?1", familyDbId),
            "familyDbId",
            String.valueOf(familyDbId)
        );
    }

    @Transactional
    public boolean createRequest(int familyDbId, int childId, long externalId,
                                 Long taskId, String taskName, Long itemId,
                                 int coins, PurchaseRequestType requestType, int moneyAmount) {
        persist(PurchaseRequestEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(externalId)
            .taskId(taskId)
            .taskName(taskName)
            .itemId(itemId)
            .coins(coins)
            .requestType(requestType)
            .moneyAmount(moneyAmount)
            .build());
        return true;
    }

    @Transactional
    public void replaceRequests(int familyDbId, List<PurchaseRequestEntity> entries) {
        List<PurchaseRequestEntity> existingEntries = list("familyId = ?1", familyDbId);
        if (entries.isEmpty()) {
            delete("familyId = ?1", familyDbId);
            return;
        }

        Map<Long, PurchaseRequestEntity> existingByExternalId = existingEntries.stream()
            .filter(entry -> entry.getExternalId() != null)
            .collect(Collectors.toMap(
                PurchaseRequestEntity::getExternalId,
                entry -> entry,
                (left, right) -> left,
                HashMap::new
            ));
        Set<Long> incomingExternalIds = entries.stream()
            .map(PurchaseRequestEntity::getExternalId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        for (PurchaseRequestEntity existing : existingEntries) {
            Long externalId = existing.getExternalId();
            if (externalId == null || !incomingExternalIds.contains(externalId)) {
                deleteById(existing.getId());
            }
        }

        for (PurchaseRequestEntity entry : entries) {
            Long externalId = entry.getExternalId();
            if (externalId == null) {
                persist(entry);
                continue;
            }

            PurchaseRequestEntity current = existingByExternalId.get(externalId);
            if (current == null) {
                persist(entry);
                continue;
            }

            copyPurchaseRequest(entry, current);
        }
    }

    @Transactional
    public boolean updateRequestStatus(int requestId, PurchaseRequestStatus status) {
        return findByIdOptional((long) requestId)
            .map(request -> {
                request.setStatus(status);
                return true;
            })
            .orElse(false);
    }

    private void copyPurchaseRequest(PurchaseRequestEntity source, PurchaseRequestEntity target) {
        target.setFamilyId(source.getFamilyId());
        target.setChildId(source.getChildId());
        target.setExternalId(source.getExternalId());
        target.setTaskId(source.getTaskId());
        target.setTaskName(source.getTaskName());
        target.setItemId(source.getItemId());
        target.setCoins(source.getCoins());
        target.setStatus(source.getStatus());
        target.setRequestType(source.getRequestType());
        target.setMoneyAmount(source.getMoneyAmount());
        target.setNote(source.getNote());
    }
}
