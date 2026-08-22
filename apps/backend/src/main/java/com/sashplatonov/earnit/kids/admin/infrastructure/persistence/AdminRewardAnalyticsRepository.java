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
public class AdminRewardAnalyticsRepository {

    @PersistenceContext
    EntityManager entityManager;

    public int countAllRewardRequests(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    public double calcMedianRewardPrice(Instant periodStart) {
        String sql = """
            SELECT p.coins FROM PurchaseRequestEntity p
            WHERE p.createdAt >= :periodStart
            AND p.coins > 0
            AND p.requestType IN (:shop, :shopPurchase)
            """;
        var prices = entityManager.createQuery(sql, Integer.class)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getResultList();
        if (prices.isEmpty()) return 0.0;
        var sorted = prices.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0 : sorted.get(n / 2);
    }

    public double calcMedianPriceOfIssuedRewards(Instant periodStart) {
        String sql = """
            SELECT p.coins FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart
            AND p.coins > 0
            AND p.requestType IN (:shop, :shopPurchase)
            """;
        var prices = entityManager.createQuery(sql, Integer.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getResultList();
        if (prices.isEmpty()) return 0.0;
        var sorted = prices.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0 : sorted.get(n / 2);
    }

    public List<AdminRewardsResponse.RewardRanking> calcRewardRankings(Instant periodStart) {
        String sql = """
            SELECT s.groupName, COUNT(p) as cnt
            FROM PurchaseRequestEntity p
            JOIN ShopItemEntity s ON s.childId = p.childId AND s.itemId = p.itemId
            WHERE p.status = :status AND p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            AND s.groupName IS NOT NULL AND s.groupName != ''
            GROUP BY s.groupName
            ORDER BY cnt DESC
            """;
        var results = entityManager.createQuery(sql, Object[].class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .setMaxResults(5)
            .getResultList();

        long total = countSuccessfulRewardPurchases(periodStart);
        List<AdminRewardsResponse.RewardRanking> rankings = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            Object[] row = results.get(i);
            String category = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double percent = total > 0 ? Math.round(100.0 * count / total) : 0.0;

            rankings.add(AdminRewardsResponse.RewardRanking.builder()
                .category(category)
                .count(Math.toIntExact(count))
                .percent(percent)
                .rank(i + 1)
                .build());
        }
        return rankings;
    }

    public int countSuccessfulRewardPurchases(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }
}
