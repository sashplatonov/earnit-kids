package com.sashplatonov.earnit.kids.admin.infrastructure.persistence;

import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AdminTrendsRepository {

  @PersistenceContext EntityManager entityManager;

  public List<AdminTrendsResponse.TrendPoint> getTrendPoints(Instant periodStart, Instant now) {
    String historySql =
        """
            SELECT FUNCTION('DATE', h.createdAt) as day,
                   COALESCE(SUM(CASE WHEN h.type = :earn THEN h.amount ELSE 0 END), 0) as earned,
                   COALESCE(SUM(CASE WHEN h.type = :spend THEN ABS(h.amount) ELSE 0 END), 0) as spent,
                   COALESCE(SUM(CASE WHEN h.type = :taskType THEN 1 ELSE 0 END), 0) as tasks,
                   COUNT(DISTINCT h.familyId) as families
            FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            GROUP BY FUNCTION('DATE', h.createdAt)
            ORDER BY day
            """;
    var historyResults =
        entityManager
            .createQuery(historySql, Object[].class)
            .setParameter("earn", HistoryEntryType.earn)
            .setParameter("spend", HistoryEntryType.spend)
            .setParameter("taskType", HistoryEntryType.TASK_COMPLETED)
            .setParameter("periodStart", periodStart)
            .getResultList();

    String rewardsSql =
        """
            SELECT FUNCTION('DATE', p.createdAt) as day, COUNT(p) as rewards
            FROM PurchaseRequestEntity p
            WHERE p.status = :approved AND p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            GROUP BY FUNCTION('DATE', p.createdAt)
            ORDER BY day
            """;
    var rewardResults =
        entityManager
            .createQuery(rewardsSql, Object[].class)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getResultList();

    java.util.Map<java.sql.Date, AdminTrendsResponse.TrendPoint> byDay =
        new java.util.LinkedHashMap<>();

    mergeHistory(byDay, historyResults);
    mergeRewards(byDay, rewardResults);

    return new ArrayList<>(byDay.values());
  }

  private void mergeHistory(
      java.util.Map<java.sql.Date, AdminTrendsResponse.TrendPoint> byDay,
      List<Object[]> rows) {
    for (Object[] row : rows) {
      java.sql.Date day = (java.sql.Date) row[0];
      AdminTrendsResponse.TrendPoint point = pointFor(byDay, day);
      point.setCoinsEarned(point.getCoinsEarned() + ((Number) row[1]).longValue());
      point.setCoinsSpent(point.getCoinsSpent() + ((Number) row[2]).longValue());
      point.setTaskCompletions(point.getTaskCompletions() + ((Number) row[3]).longValue());
      point.setActiveFamilies(((Number) row[4]).longValue());
    }
  }

  private void mergeRewards(
      java.util.Map<java.sql.Date, AdminTrendsResponse.TrendPoint> byDay,
      List<Object[]> rows) {
    for (Object[] row : rows) {
      pointFor(byDay, (java.sql.Date) row[0])
          .setRewardRedemptions(((Number) row[1]).longValue());
    }
  }

  private AdminTrendsResponse.TrendPoint pointFor(
      java.util.Map<java.sql.Date, AdminTrendsResponse.TrendPoint> byDay, java.sql.Date day) {
    return byDay.computeIfAbsent(
        day,
        value ->
            AdminTrendsResponse.TrendPoint.builder()
                .date(value.toLocalDate().toString())
                .activeFamilies(0)
                .coinsEarned(0)
                .coinsSpent(0)
                .rewardRedemptions(0)
                .taskCompletions(0)
                .build());
  }
}
