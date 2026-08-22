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
public class AdminCoinEconomyRepository {

    @PersistenceContext
    EntityManager entityManager;

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

    public int countActiveChildren(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT h.childId) FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    public AdminCoinEconomyResponse.CoinMetrics getCoinMetrics(Instant periodStart, int activeChildrenInPeriod) {
        long earned = sumCoinsEarned(periodStart);
        long spent = sumCoinsSpent(periodStart);
        double spendRate = earned > 0 ? (double) spent / earned : 0.0;

        return AdminCoinEconomyResponse.CoinMetrics.builder()
            .earned(earned)
            .spent(spent)
            .spendRate(Math.round(spendRate * 100.0))
            .activeChildren(activeChildrenInPeriod)
            .build();
    }

    public AdminCoinEconomyResponse.BalanceMetrics getBalanceMetrics() {
        String sql = """
            SELECT c.balance FROM ChildEntity c WHERE c.status = :activeStatus
            """;
        var balances = entityManager.createQuery(sql, Integer.class)
            .setParameter("activeStatus", ChildStatus.ACTIVE.name())
            .getResultList();

        double timeToFirstReward = calcMedianTimeToFirstReward();

        if (balances.isEmpty()) {
            return AdminCoinEconomyResponse.BalanceMetrics.builder()
                .medianBalance(0)
                .averageBalance(0)
                .zeroBalanceCount(0)
                .zeroBalancePercent(0)
                .highBalanceCount(0)
                .highBalancePercent(0)
                .timeToFirstReward(timeToFirstReward)
                .build();
        }

        double sum = balances.stream().mapToInt(Integer::intValue).sum();
        double average = sum / balances.size();

        var sorted = balances.stream().sorted().toList();
        int n = sorted.size();
        double median = n % 2 == 0
            ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
            : sorted.get(n / 2);

        int zeroCount = (int) balances.stream().filter(b -> b == 0).count();
        int zeroPercent = Math.toIntExact(Math.round(100.0 * zeroCount / n));

        int highCount = (int) balances.stream().filter(b -> b >= 20).count();
        int highPercent = Math.toIntExact(Math.round(100.0 * highCount / n));

        return AdminCoinEconomyResponse.BalanceMetrics.builder()
            .medianBalance(median)
            .averageBalance(Math.round(average * 100.0) / 100.0)
            .zeroBalanceCount(zeroCount)
            .zeroBalancePercent(zeroPercent)
            .highBalanceCount(highCount)
            .highBalancePercent(highPercent)
            .timeToFirstReward(timeToFirstReward)
            .build();
    }

    private double calcMedianTimeToFirstReward() {
        String sql = """
            SELECT pr.createdAt, f.createdAt
            FROM PurchaseRequestEntity pr
            JOIN FamilyEntity f ON f.id = pr.familyId
            WHERE pr.status = :approved
            """;
        List<Object[]> rows = entityManager.createQuery(sql, Object[].class)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .getResultList();

        if (rows.isEmpty()) return 0.0;

        List<Double> days = new ArrayList<>();
        for (Object[] row : rows) {
            Instant rewardAt = (Instant) row[0];
            Instant familyCreatedAt = (Instant) row[1];
            if (rewardAt != null && familyCreatedAt != null && rewardAt.isAfter(familyCreatedAt)) {
                days.add(ChronoUnit.DAYS.between(familyCreatedAt, rewardAt) + (double) (ChronoUnit.HOURS.between(familyCreatedAt, rewardAt) % 24) / 24.0);
            }
        }

        if (days.isEmpty()) return 0.0;
        days.sort(Double::compareTo);
        int n = days.size();
        return n % 2 == 0
            ? (days.get(n / 2 - 1) + days.get(n / 2)) / 2.0
            : days.get(n / 2);
    }

    public AdminCoinEconomyResponse.RewardMetrics getRewardMetrics(Instant periodStart) {
        String familiesWithRewardSql = """
            SELECT COUNT(DISTINCT p.familyId) FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart
            AND p.requestType IN (:shop, :shopPurchase)
            """;
        Long familiesWithReward = entityManager.createQuery(familiesWithRewardSql, Long.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .getSingleResult();

        int totalFamilies = countTotalFamilies();
        int fwr = familiesWithReward != null ? Math.toIntExact(familiesWithReward) : 0;
        int percent = totalFamilies > 0 ? Math.toIntExact(Math.round(100.0 * fwr / totalFamilies)) : 0;

        return AdminCoinEconomyResponse.RewardMetrics.builder()
            .familiesWithReward(fwr)
            .percentFamiliesWithReward(percent)
            .build();
    }

    private int countTotalFamilies() {
        return Math.toIntExact(
            entityManager.createQuery(
                "SELECT COUNT(f) FROM FamilyEntity f", Long.class)
                .getSingleResult()
        );
    }
}
