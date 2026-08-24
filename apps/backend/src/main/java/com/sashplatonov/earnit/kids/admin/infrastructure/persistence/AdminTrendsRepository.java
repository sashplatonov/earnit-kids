package com.sashplatonov.earnit.kids.admin.infrastructure.persistence;

import com.sashplatonov.earnit.kids.family.domain.model.child.ChildStatus;
import com.sashplatonov.earnit.kids.family.domain.model.history.HistoryEntryType;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.family.domain.model.request.PurchaseRequestType;
import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AdminTrendsRepository {

    @PersistenceContext
    EntityManager entityManager;

    public List<AdminTrendsResponse.TrendPoint> getTrendPoints(Instant periodStart, Instant now) {
        String historySql = """
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
        var historyResults = entityManager.createQuery(historySql, Object[].class)
            .setParameter("earn", HistoryEntryType.earn)
            .setParameter("spend", HistoryEntryType.spend)
            .setParameter("taskType", HistoryEntryType.TASK_COMPLETED)
            .setParameter("periodStart", periodStart)
            .getResultList();

        String rewardsSql = """
            SELECT FUNCTION('DATE', p.createdAt) as day, COUNT(p) as rewards
            FROM PurchaseRequestEntity p
            WHERE p.status = :approved AND p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            GROUP BY FUNCTION('DATE', p.createdAt)
            ORDER BY day
            """;
        var rewardResults = entityManager.createQuery(rewardsSql, Object[].class)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getResultList();

        java.util.Map<java.sql.Date, AdminTrendsResponse.TrendPoint> byDay = new java.util.LinkedHashMap<>();

        for (Object[] row : historyResults) {
            java.sql.Date day = (java.sql.Date) row[0];
            long earned = ((Number) row[1]).longValue();
            long spent = ((Number) row[2]).longValue();
            long tasks = ((Number) row[3]).longValue();
            long families = ((Number) row[4]).longValue();
            byDay.computeIfAbsent(day, d -> AdminTrendsResponse.TrendPoint.builder()
                    .date(d.toLocalDate().toString())
                    .activeFamilies(0)
                    .coinsEarned(0)
                    .coinsSpent(0)
                    .rewardRedemptions(0)
                    .taskCompletions(0)
                    .build());
            AdminTrendsResponse.TrendPoint point = byDay.get(day);
            point.setCoinsEarned(point.getCoinsEarned() + earned);
            point.setCoinsSpent(point.getCoinsSpent() + spent);
            point.setTaskCompletions(point.getTaskCompletions() + tasks);
            point.setActiveFamilies(families);
        }

        for (Object[] row : rewardResults) {
            java.sql.Date day = (java.sql.Date) row[0];
            long rewards = ((Number) row[1]).longValue();
            byDay.computeIfAbsent(day, d -> AdminTrendsResponse.TrendPoint.builder()
                    .date(d.toLocalDate().toString())
                    .activeFamilies(0)
                    .coinsEarned(0)
                    .coinsSpent(0)
                    .rewardRedemptions(0)
                    .taskCompletions(0)
                    .build());
            byDay.get(day).setRewardRedemptions(rewards);
        }

        return new ArrayList<>(byDay.values());
    }
}
