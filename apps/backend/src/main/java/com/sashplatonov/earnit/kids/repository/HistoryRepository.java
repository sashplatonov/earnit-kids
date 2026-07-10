package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.HistoryEntryEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.service.SlowOperationDiagnostics;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HistoryRepository implements PanacheRepositoryBase<HistoryEntryEntity, Long> {

    @PersistenceContext
    EntityManager entityManager;
    private final SlowOperationDiagnostics slowOperationDiagnostics;

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

    public List<HistoryEntryEntity> getHistory(int childId, int limit, int offset) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getHistory",
            () -> find("childId = ?1 ORDER BY createdAt DESC, id DESC", childId)
                .range(offset, offset + limit - 1)
                .list(),
            "childId",
            String.valueOf(childId),
            "limit",
            String.valueOf(limit),
            "offset",
            String.valueOf(offset)
        );
    }

    public List<HistoryEntryEntity> getHistoryForFamily(int familyDbId, int limit, int offset) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getHistoryForFamily",
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

    public int getHistoryCount(int childId) {
        return slowOperationDiagnostics.recordQuery(
            "family-data.getHistoryCount",
            () -> (int) count("childId = ?1", childId),
            "childId",
            String.valueOf(childId)
        );
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

    @Transactional
    public boolean addHistory(int familyDbId, int childId, long externalId, HistoryEntryType type,
                              int amount, String description, int moneyAmount,
                              Long relatedId, String groupName, String comment) {
        persist(HistoryEntryEntity.builder()
            .familyId(familyDbId)
            .childId(childId)
            .externalId(externalId)
            .type(type)
            .amount(amount)
            .description(description)
            .moneyAmount(moneyAmount)
            .relatedId(relatedId)
            .groupName(groupName)
            .comment(comment)
            .build());
        return true;
    }

    @Transactional
    public void replaceHistory(int familyDbId, int childId, List<HistoryEntryEntity> entries) {
        List<HistoryEntryEntity> existingEntries = list("familyId = ?1 AND childId = ?2", familyDbId, childId);
        if (entries.isEmpty()) {
            delete("familyId = ?1 AND childId = ?2", familyDbId, childId);
            return;
        }

        Map<Long, HistoryEntryEntity> existingByExternalId = existingEntries.stream()
            .filter(entry -> entry.getExternalId() != null)
            .collect(Collectors.toMap(
                HistoryEntryEntity::getExternalId,
                entry -> entry,
                (left, right) -> left,
                HashMap::new
            ));
        Set<Long> incomingExternalIds = entries.stream()
            .map(HistoryEntryEntity::getExternalId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        for (HistoryEntryEntity existing : existingEntries) {
            Long externalId = existing.getExternalId();
            if (externalId == null || !incomingExternalIds.contains(externalId)) {
                deleteById(existing.getId());
            }
        }

        for (HistoryEntryEntity entry : entries) {
            Long externalId = entry.getExternalId();
            if (externalId == null) {
                persist(entry);
                continue;
            }

            HistoryEntryEntity current = existingByExternalId.get(externalId);
            if (current == null) {
                persist(entry);
                continue;
            }

            copyHistoryEntry(entry, current);
        }
    }

    @Transactional
    public void upsertHistoryEntry(HistoryEntryEntity entry) {
        if (entry.getExternalId() == null) {
            persist(entry);
            return;
        }

        List<HistoryEntryEntity> existingEntries = list(
            "familyId = ?1 AND childId = ?2 AND externalId = ?3",
            entry.getFamilyId(),
            entry.getChildId(),
            entry.getExternalId()
        );
        if (existingEntries.isEmpty()) {
            persist(entry);
            return;
        }

        copyHistoryEntry(entry, existingEntries.getFirst());
    }

    private void copyHistoryEntry(HistoryEntryEntity source, HistoryEntryEntity target) {
        target.setFamilyId(source.getFamilyId());
        target.setChildId(source.getChildId());
        target.setExternalId(source.getExternalId());
        target.setType(source.getType());
        target.setAmount(source.getAmount());
        target.setDescription(source.getDescription());
        target.setMoneyAmount(source.getMoneyAmount());
        target.setRelatedId(source.getRelatedId());
        target.setGroupName(source.getGroupName());
        target.setComment(source.getComment());
    }
}
