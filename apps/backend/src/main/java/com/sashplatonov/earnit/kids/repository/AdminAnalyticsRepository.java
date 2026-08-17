package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class AdminAnalyticsRepository implements PanacheRepositoryBase<FamilyEntity, Integer> {

    @PersistenceContext
    EntityManager entityManager;

    public AdminAnalyticsResponse.Overview getOverview(Instant periodStart) {
        int totalFamilies = countTotalFamilies();
        int totalChildren = countTotalChildren();
        int activeFamilies = countActiveFamilies(periodStart);
        int activeChildren = countActiveChildren(periodStart);
        long coinsEarned = sumCoinsEarned(periodStart);
        long coinsSpent = sumCoinsSpent(periodStart);
        int rewardPurchases = countSuccessfulRewardPurchases(periodStart);
        int taskCompletions = countTaskCompletions(periodStart);

        return AdminAnalyticsResponse.Overview.builder()
            .totalFamilies(totalFamilies)
            .activeFamilies(activeFamilies)
            .totalChildren(totalChildren)
            .activeChildren(activeChildren)
            .coinsEarned(coinsEarned)
            .coinsSpent(coinsSpent)
            .rewardPurchases(rewardPurchases)
            .taskCompletions(taskCompletions)
            .build();
    }

    private int countTotalFamilies() {
        return Math.toIntExact(
            entityManager.createQuery(
                "SELECT COUNT(f) FROM FamilyEntity f", Long.class)
                .getSingleResult()
        );
    }

    private int countTotalChildren() {
        String sql = "SELECT COUNT(c) FROM ChildEntity c";
        Long result = entityManager.createQuery(sql, Long.class).getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countActiveFamilies(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT h.familyDbId) FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countActiveChildren(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT h.childId) FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private long sumCoinsEarned(Instant periodStart) {
        String sql = """
            SELECT COALESCE(SUM(h.amount), 0) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.amount > 0 AND h.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.earn)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private long sumCoinsSpent(Instant periodStart) {
        String sql = """
            SELECT COALESCE(SUM(ABS(h.amount)), 0) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.spend)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private int countSuccessfulRewardPurchases(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countTaskCompletions(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT h.relatedId) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart AND h.relatedId IS NOT NULL
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.earn)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }
}
