package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
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

    // EXPLAIN: Coin economy metrics for ADM-05
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
            SELECT c.balance FROM ChildEntity c WHERE c.isTestAccount = false
            """;
        var balances = entityManager.createQuery(sql, Integer.class).getResultList();

        if (balances.isEmpty()) {
            return AdminCoinEconomyResponse.BalanceMetrics.builder()
                .medianBalance(0)
                .averageBalance(0)
                .zeroBalanceCount(0)
                .zeroBalancePercent(0)
                .highBalanceCount(0)
                .highBalancePercent(0)
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
            .build();
    }

    public AdminCoinEconomyResponse.RewardMetrics getRewardMetrics(Instant periodStart) {
        String familiesWithRewardSql = """
            SELECT COUNT(DISTINCT p.familyDbId) FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart
            """;
        Long familiesWithReward = entityManager.createQuery(familiesWithRewardSql, Long.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getSingleResult();

        int totalFamilies = countTotalFamilies();
        int fwr = familiesWithReward != null ? Math.toIntExact(familiesWithReward) : 0;
        int percent = totalFamilies > 0 ? Math.toIntExact(Math.round(100.0 * fwr / totalFamilies)) : 0;

        return AdminCoinEconomyResponse.RewardMetrics.builder()
            .familiesWithReward(fwr)
            .percentFamiliesWithReward(percent)
            .build();
    }

    // EXPLAIN: Reward shop metrics for ADM-06
    public AdminRewardsResponse.RewardShopMetrics getRewardShopMetrics(Instant periodStart) {
        int rewardsConfigured = countRewardsConfigured();
        double familiesWithRewardPercent = percentFamiliesWithActiveReward();
        long rewardRequests = countRewardRequests(periodStart);
        long approvedRewards = countApprovedRewards(periodStart);
        double rejectionRate = calcRejectionRate(periodStart);
        double medianPrice = calcMedianRewardPrice();
        double medianPurchasedPrice = calcMedianPurchasedPrice(periodStart);
        AdminRewardsResponse.RewardPriceDistribution priceDistribution = calcRewardPriceDistribution();

        return AdminRewardsResponse.RewardShopMetrics.builder()
            .rewardsConfigured(rewardsConfigured)
            .familiesWithRewardPercent(familiesWithRewardPercent)
            .rewardRequests(rewardRequests)
            .approvedRewards(approvedRewards)
            .rejectionRate(rejectionRate)
            .medianPrice(medianPrice)
            .medianPurchasedPrice(medianPurchasedPrice)
            .priceDistribution(priceDistribution)
            .build();
    }

    private int countRewardsConfigured() {
        String sql = "SELECT COUNT(DISTINCT familyId) FROM ShopItemEntity";
        Long result = entityManager.createQuery(sql, Long.class).getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private double percentFamiliesWithActiveReward() {
        String sql = """
            SELECT COUNT(DISTINCT s.familyId) FROM ShopItemEntity s
            WHERE s.familyId IN (SELECT DISTINCT h.familyDbId FROM HistoryEntryEntity h)
            """;
        Long familiesWithReward = entityManager.createQuery(sql, Long.class).getSingleResult();
        int totalFamilies = countTotalFamilies();
        if (totalFamilies == 0) return 0.0;
        return Math.round(100.0 * familiesWithReward / totalFamilies);
    }

    private long countRewardRequests(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private long countApprovedRewards(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private double calcRejectionRate(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.status IN (:rejected, :cancelled) AND p.createdAt >= :periodStart
            """;
        Long rejected = entityManager.createQuery(sql, Long.class)
            .setParameter("rejected", PurchaseRequestStatus.rejected)
            .setParameter("cancelled", PurchaseRequestStatus.cancelled)
            .setParameter("periodStart", periodStart)
            .getSingleResult();

        long total = countRewardRequests(periodStart);
        if (total == 0) return 0.0;
        return Math.round(100.0 * rejected / total);
    }

    private double calcMedianRewardPrice() {
        String sql = "SELECT s.price FROM ShopItemEntity s WHERE s.price > 0";
        var prices = entityManager.createQuery(sql, Integer.class).getResultList();
        if (prices.isEmpty()) return 0.0;

        var sorted = prices.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 0
            ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
            : sorted.get(n / 2);
    }

    private double calcMedianPurchasedPrice(Instant periodStart) {
        String sql = """
            SELECT p.coins FROM PurchaseRequestEntity p
            WHERE p.status = :status AND p.createdAt >= :periodStart AND p.coins > 0
            """;
        var prices = entityManager.createQuery(sql, Integer.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getResultList();
        if (prices.isEmpty()) return 0.0;

        var sorted = prices.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 0
            ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
            : sorted.get(n / 2);
    }

    private AdminRewardsResponse.RewardPriceDistribution calcRewardPriceDistribution() {
        String sql = "SELECT s.price FROM ShopItemEntity s WHERE s.price > 0";
        var prices = entityManager.createQuery(sql, Integer.class).getResultList();
        
        int bucket1to5 = 0, bucket6to10 = 0, bucket11to20 = 0, bucket21to50 = 0, bucket51plus = 0;
        
        for (int price : prices) {
            if (price <= 5) bucket1to5++;
            else if (price <= 10) bucket6to10++;
            else if (price <= 20) bucket11to20++;
            else if (price <= 50) bucket21to50++;
            else bucket51plus++;
        }
        
        return AdminRewardsResponse.RewardPriceDistribution.builder()
            .bucket1to5(bucket1to5)
            .bucket6to10(bucket6to10)
            .bucket11to20(bucket11to20)
            .bucket21to50(bucket21to50)
            .bucket51plus(bucket51plus)
            .build();
    }
}
