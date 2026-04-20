package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class HistoryRepository implements PanacheRepositoryBase<HistoryEntryEntity, Long> {

    public long countTaskEarnsInWindow(
        int familyDbId,
        int childId,
        long taskId,
        Instant startInclusive,
        Instant endExclusive
    ) {
        return count(
            "familyId = ?1 AND childId = ?2 AND relatedId = ?3"
                + " AND type = ?4 AND createdAt >= ?5 AND createdAt < ?6",
            familyDbId,
            childId,
            taskId,
            "earn",
            startInclusive,
            endExclusive
        );
    }

    public long countShopPurchasesInWindow(
        int familyDbId,
        int childId,
        long itemId,
        Instant startInclusive,
        Instant endExclusive
    ) {
        return count(
            "familyId = ?1 AND childId = ?2 AND relatedId = ?3"
                + " AND type = ?4 AND createdAt >= ?5 AND createdAt < ?6",
            familyDbId,
            childId,
            itemId,
            "spend",
            startInclusive,
            endExclusive
        );
    }
}
