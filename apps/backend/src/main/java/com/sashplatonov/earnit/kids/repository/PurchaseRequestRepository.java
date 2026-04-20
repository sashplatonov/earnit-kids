package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class PurchaseRequestRepository implements PanacheRepositoryBase<PurchaseRequestEntity, Long> {

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
}
