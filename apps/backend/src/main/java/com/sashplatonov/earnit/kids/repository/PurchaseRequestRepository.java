package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.service.observability.SlowOperationDiagnostics;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
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
    @PersistenceContext
    EntityManager entityManager;
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
            PurchaseRequestStatus.pending,
            startInclusive,
            endExclusive
        );
    }

    public Map<Long, Long> countPendingTaskRequestsInWindowByTask(
        int familyDbId,
        int childId,
        Instant startInclusive,
        Instant endExclusive
    ) {
        var query = entityManager.createQuery(
            "SELECT r.taskId, COUNT(r.id) FROM PurchaseRequestEntity r " +
                "WHERE r.familyId = ?1 AND r.childId = ?2 AND r.status = ?3 " +
                "AND r.taskId IS NOT NULL AND r.createdAt >= ?4 AND r.createdAt < ?5 " +
                "GROUP BY r.taskId",
            Object[].class
        );
        query.setParameter(1, familyDbId);
        query.setParameter(2, childId);
        query.setParameter(3, PurchaseRequestStatus.pending);
        query.setParameter(4, startInclusive);
        query.setParameter(5, endExclusive);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
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
            PurchaseRequestStatus.pending,
            startInclusive,
            endExclusive
        );
    }

    public Map<Long, Long> countPendingItemRequestsInWindowByItem(
        int familyDbId,
        int childId,
        Instant startInclusive,
        Instant endExclusive
    ) {
        var query = entityManager.createQuery(
            "SELECT r.itemId, COUNT(r.id) FROM PurchaseRequestEntity r " +
                "WHERE r.familyId = ?1 AND r.childId = ?2 AND r.status = ?3 " +
                "AND r.itemId IS NOT NULL AND r.createdAt >= ?4 AND r.createdAt < ?5 " +
                "GROUP BY r.itemId",
            Object[].class
        );
        query.setParameter(1, familyDbId);
        query.setParameter(2, childId);
        query.setParameter(3, PurchaseRequestStatus.pending);
        query.setParameter(4, startInclusive);
        query.setParameter(5, endExclusive);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    public List<PurchaseRequestEntity> getRequests(int familyDbId, int limit, int offset) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getRequests",
            () -> PanachePagination.page(find("familyId = ?1 ORDER BY createdAt DESC, id DESC", familyDbId), limit, offset),
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

    public java.util.Optional<PurchaseRequestEntity> findByIdForUpdate(long requestId) {
        return find("id = ?1", requestId)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResultOptional();
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
