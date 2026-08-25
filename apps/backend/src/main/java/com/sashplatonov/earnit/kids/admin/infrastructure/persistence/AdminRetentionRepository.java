package com.sashplatonov.earnit.kids.admin.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;

@ApplicationScoped
public class AdminRetentionRepository {

  @PersistenceContext EntityManager entityManager;

  public int countNewFamilies(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(f) FROM FamilyEntity f
            WHERE f.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? Math.toIntExact(result) : 0;
  }

  public int countReturningFamilies(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(DISTINCT h.familyId) FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            AND h.familyId IN (
                SELECT f.id FROM FamilyEntity f
                WHERE f.createdAt < :periodStart
            )
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? Math.toIntExact(result) : 0;
  }

  public int countActiveFamilies(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(DISTINCT h.familyId) FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? Math.toIntExact(result) : 0;
  }
}
