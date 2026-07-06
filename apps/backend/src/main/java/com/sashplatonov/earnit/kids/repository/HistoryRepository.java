package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class HistoryRepository implements PanacheRepositoryBase<HistoryEntryEntity, Long> {

    @PersistenceContext
    EntityManager entityManager;

    // EXPLAIN: Latest createdAt per relatedId via SQL aggregation instead of loading rows into Java memory.
    public Map<Long, Instant> loadLatestTimestampsByRelatedId(int childId, HistoryEntryType type) {
        var query = entityManager.createQuery(
            "SELECT h.relatedId, MAX(h.createdAt) FROM HistoryEntryEntity h " +
            "WHERE h.childId = ?1 AND h.type = ?2 AND h.relatedId IS NOT NULL " +
            "GROUP BY h.relatedId", Object[].class);
        query.setParameter(1, childId);
        query.setParameter(2, type);
        List<Object[]> rows = query.getResultList();
        return rows.stream()
            .filter(row -> row[0] != null && row[1] != null)
            .collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(),
                row -> (Instant) row[1]
            ));
    }

    // EXPLAIN: Analytics aggregation — earned/spent in a time window via SQL SUM. Returns [earn, spend].
    public int[] summarizePeriod(int familyDbId, Integer childId, Instant from, Instant to) {
        var jpql = new StringBuilder(
            "SELECT h.type, COALESCE(SUM(h.amount), 0) FROM HistoryEntryEntity h " +
            "WHERE h.familyId = ?1 AND h.createdAt >= ?2 AND h.createdAt < ?3");
        if (childId != null) {
            jpql.append(" AND h.childId = ?4");
        }
        jpql.append(" GROUP BY h.type");

        var query = entityManager.createQuery(jpql.toString(), Object[].class);
        query.setParameter(1, familyDbId);
        query.setParameter(2, from);
        query.setParameter(3, to);
        if (childId != null) {
            query.setParameter(4, childId);
        }

        int earned = 0;
        int spent = 0;
        for (var row : query.getResultList()) {
            var type = (String) ((Object[]) row)[0];
            var sum = ((Number) ((Object[]) row)[1]).intValue();
            if ("earn".equals(type)) {
                earned = sum;
            } else if ("spend".equals(type)) {
                spent = sum;
            }
        }
        return new int[]{earned, spent};
    }

    // EXPLAIN: Top tasks by total coins earned in a time window — aggregated in SQL.
    public List<Object[]> topTasksInPeriod(int familyDbId, Integer childId, Instant from, Instant to) {
        var jpql = new StringBuilder(
            "SELECT h.relatedId, COALESCE(SUM(h.amount), 0), COUNT(h.id) FROM HistoryEntryEntity h " +
            "WHERE h.familyId = ?1 AND h.type = 'earn' AND h.relatedId IS NOT NULL " +
            "AND h.createdAt >= ?2 AND h.createdAt < ?3");
        if (childId != null) {
            jpql.append(" AND h.childId = ?4");
        }
        jpql.append(" GROUP BY h.relatedId ORDER BY SUM(h.amount) DESC");

        var query = entityManager.createQuery(jpql.toString(), Object[].class);
        query.setParameter(1, familyDbId);
        query.setParameter(2, from);
        query.setParameter(3, to);
        if (childId != null) {
            query.setParameter(4, childId);
        }
        return query.getResultList();
    }

    // EXPLAIN: Top shop items by total coins spent in a time window — aggregated in SQL.
    public List<Object[]> topItemsInPeriod(int familyDbId, Integer childId, Instant from, Instant to) {
        var jpql = new StringBuilder(
            "SELECT h.relatedId, COALESCE(SUM(h.amount), 0), COUNT(h.id) FROM HistoryEntryEntity h " +
            "WHERE h.familyId = ?1 AND h.type = 'spend' AND h.relatedId IS NOT NULL " +
            "AND h.createdAt >= ?2 AND h.createdAt < ?3");
        if (childId != null) {
            jpql.append(" AND h.childId = ?4");
        }
        jpql.append(" GROUP BY h.relatedId ORDER BY SUM(h.amount) DESC");

        var query = entityManager.createQuery(jpql.toString(), Object[].class);
        query.setParameter(1, familyDbId);
        query.setParameter(2, from);
        query.setParameter(3, to);
        if (childId != null) {
            query.setParameter(4, childId);
        }
        return query.getResultList();
    }

    // EXPLAIN: Daily aggregates (earned, spent) in a time window — one row per day via SQL.
    public List<Object[]> dailyTrendInPeriod(int familyDbId, Integer childId, Instant from, Instant to) {
        var jpql = new StringBuilder(
            "SELECT FUNCTION('DATE', h.createdAt), h.type, COALESCE(SUM(h.amount), 0) " +
            "FROM HistoryEntryEntity h " +
            "WHERE h.familyId = ?1 AND h.createdAt >= ?2 AND h.createdAt < ?3");
        if (childId != null) {
            jpql.append(" AND h.childId = ?4");
        }
        jpql.append(" GROUP BY FUNCTION('DATE', h.createdAt), h.type ORDER BY FUNCTION('DATE', h.createdAt) ASC");

        var query = entityManager.createQuery(jpql.toString(), Object[].class);
        query.setParameter(1, familyDbId);
        query.setParameter(2, from);
        query.setParameter(3, to);
        if (childId != null) {
            query.setParameter(4, childId);
        }
        return query.getResultList();
    }

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
