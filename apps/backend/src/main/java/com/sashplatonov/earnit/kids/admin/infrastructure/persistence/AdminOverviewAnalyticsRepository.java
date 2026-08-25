package com.sashplatonov.earnit.kids.admin.infrastructure.persistence;

import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;

@ApplicationScoped
public class AdminOverviewAnalyticsRepository {

  @PersistenceContext EntityManager entityManager;

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
        entityManager
            .createQuery("SELECT COUNT(f) FROM FamilyEntity f", Long.class)
            .getSingleResult());
  }

  private int countTotalChildren() {
    String sql = "SELECT COUNT(c) FROM ChildEntity c";
    Long result = entityManager.createQuery(sql, Long.class).getSingleResult();
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

  public int countActiveChildren(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(DISTINCT h.childId) FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? Math.toIntExact(result) : 0;
  }

  private long sumCoinsEarned(Instant periodStart) {
    String sql =
        """
            SELECT COALESCE(SUM(h.amount), 0) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.amount > 0 AND h.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.earn)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? result : 0L;
  }

  private long sumCoinsSpent(Instant periodStart) {
    String sql =
        """
            SELECT COALESCE(SUM(ABS(h.amount)), 0) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.spend)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? result : 0L;
  }

  public int countSuccessfulRewardPurchases(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getSingleResult();
    return result != null ? Math.toIntExact(result) : 0;
  }

  private int countTaskCompletions(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(DISTINCT h.relatedId) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart AND h.relatedId IS NOT NULL
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.earn)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? Math.toIntExact(result) : 0;
  }

  public int countAllRewardRequests(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getSingleResult();
    return result != null ? Math.toIntExact(result) : 0;
  }
}
