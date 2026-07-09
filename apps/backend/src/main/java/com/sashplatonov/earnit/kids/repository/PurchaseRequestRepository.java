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
        delete("familyId = ?1", familyDbId);
        entries.forEach(this::persist);
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
}
