package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
        List<AdminRewardsResponse.TopRewardPattern> topPatterns = calcTopRewardPatterns(periodStart);

        return AdminRewardsResponse.RewardShopMetrics.builder()
            .rewardsConfigured(rewardsConfigured)
            .familiesWithRewardPercent(familiesWithRewardPercent)
            .rewardRequests(rewardRequests)
            .approvedRewards(approvedRewards)
            .rejectionRate(rejectionRate)
            .medianPrice(medianPrice)
            .medianPurchasedPrice(medianPurchasedPrice)
            .priceDistribution(priceDistribution)
            .topPatterns(topPatterns)
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

    private List<AdminRewardsResponse.TopRewardPattern> calcTopRewardPatterns(Instant periodStart) {
        String sql = """
            SELECT s.groupName, s.icon, COUNT(p) as cnt
            FROM PurchaseRequestEntity p
            JOIN p.shopItem s
            WHERE p.status = :status AND p.createdAt >= :periodStart
            AND s.groupName IS NOT NULL AND s.groupName != ''
            GROUP BY s.groupName, s.icon
            ORDER BY cnt DESC
            """;
        var results = entityManager.createQuery(sql, Object[].class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .setMaxResults(5)
            .getResultList();
        
        long total = countApprovedRewards(periodStart);
        List<AdminRewardsResponse.TopRewardPattern> patterns = new ArrayList<>();
        
        for (Object[] row : results) {
            String groupName = (String) row[0];
            String icon = (String) row[1];
            long count = ((Number) row[2]).longValue();
            double percent = total > 0 ? Math.round(100.0 * count / total) : 0.0;
            
            patterns.add(AdminRewardsResponse.TopRewardPattern.builder()
                .groupName(groupName)
                .icon(icon)
                .count(count)
                .percent(percent)
                .build());
        }
        
        return patterns;
    }

    // EXPLAIN: Task economy metrics for ADM-09
    public AdminTasksResponse.TaskMetrics getTaskMetrics(Instant periodStart) {
        int tasksConfigured = countTasksConfigured();
        double familiesWithTasksPercent = percentFamiliesWithActiveTask();
        long taskCompletions = countTaskCompletionsInPeriod(periodStart);
        long approvedCompletions = countApprovedTaskCompletions(periodStart);
        long rejectedCompletions = countRejectedTaskCompletions(periodStart);
        double approvalRate = calcTaskApprovalRate(periodStart, approvedCompletions, rejectedCompletions);
        double medianCoinsPerTask = calcMedianCoinsPerApprovedTask();
        double medianCompletionsPerChild = calcMedianCompletionsPerActiveChild(periodStart);

        return AdminTasksResponse.TaskMetrics.builder()
            .tasksConfigured(tasksConfigured)
            .familiesWithTasksPercent(familiesWithTasksPercent)
            .taskCompletions(taskCompletions)
            .approvedCompletions(approvedCompletions)
            .rejectedCompletions(rejectedCompletions)
            .approvalRate(approvalRate)
            .medianCoinsPerTask(medianCoinsPerTask)
            .medianCompletionsPerChild(medianCompletionsPerChild)
            .build();
    }

    private int countTasksConfigured() {
        String sql = "SELECT COUNT(DISTINCT familyId) FROM TaskEntity";
        Long result = entityManager.createQuery(sql, Long.class).getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private double percentFamiliesWithActiveTask() {
        String sql = """
            SELECT COUNT(DISTINCT t.familyId) FROM TaskEntity t
            WHERE t.familyId IN (SELECT DISTINCT h.familyDbId FROM HistoryEntryEntity h)
            """;
        Long familiesWithTask = entityManager.createQuery(sql, Long.class).getSingleResult();
        int totalFamilies = countTotalFamilies();
        if (totalFamilies == 0) return 0.0;
        return Math.round(100.0 * familiesWithTask / totalFamilies);
    }

    private long countTaskCompletionsInPeriod(Instant periodStart) {
        String sql = """
            SELECT COUNT(h) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.TASK_COMPLETED)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private long countApprovedTaskCompletions(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.type = 'task' AND p.status = :status AND p.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private long countRejectedTaskCompletions(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.type = 'task' AND p.status IN (:rejected, :cancelled) AND p.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("rejected", PurchaseRequestStatus.rejected)
            .setParameter("cancelled", PurchaseRequestStatus.cancelled)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private double calcTaskApprovalRate(Instant periodStart, long approved, long rejected) {
        long total = approved + rejected;
        if (total == 0) return 0.0;
        return Math.round(100.0 * approved / total);
    }

    private double calcMedianCoinsPerApprovedTask() {
        String sql = """
            SELECT p.coins FROM PurchaseRequestEntity p
            WHERE p.type = 'task' AND p.status = :status AND p.coins > 0
            """;
        var coins = entityManager.createQuery(sql, Integer.class)
            .setParameter("status", PurchaseRequestStatus.approved)
            .getResultList();
        if (coins.isEmpty()) return 0.0;

        var sorted = coins.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 0
            ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
            : sorted.get(n / 2);
    }

    private double calcMedianCompletionsPerActiveChild(Instant periodStart) {
        String sql = """
            SELECT h.childId, COUNT(h) as cnt
            FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            GROUP BY h.childId
            """;
        var results = entityManager.createQuery(sql, Object[].class)
            .setParameter("type", HistoryEntryType.TASK_COMPLETED)
            .setParameter("periodStart", periodStart)
            .getResultList();
        
        if (results.isEmpty()) return 0.0;
        
        var counts = results.stream()
            .map(row -> ((Number) row[1]).longValue())
            .sorted()
            .toList();
        
        int n = counts.size();
        return n % 2 == 0
            ? (counts.get(n / 2 - 1) + counts.get(n / 2)) / 2.0
            : counts.get(n / 2);
    }

    public List<AdminTasksResponse.TopTaskPattern> calcTopTaskPatterns(Instant periodStart) {
        String sql = """
            SELECT h.groupName, h.icon, COUNT(h) as cnt
            FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            AND h.groupName IS NOT NULL AND h.groupName != ''
            GROUP BY h.groupName, h.icon
            ORDER BY cnt DESC
            """;
        var results = entityManager.createQuery(sql, Object[].class)
            .setParameter("type", HistoryEntryType.TASK_COMPLETED)
            .setParameter("periodStart", periodStart)
            .setMaxResults(5)
            .getResultList();
        
        long total = countApprovedTaskCompletions(periodStart);
        List<AdminTasksResponse.TopTaskPattern> patterns = new ArrayList<>();
        
        for (Object[] row : results) {
            String groupName = (String) row[0];
            String icon = (String) row[1];
            long count = ((Number) row[2]).longValue();
            double percent = total > 0 ? Math.round(100.0 * count / total) : 0.0;
            
            patterns.add(AdminTasksResponse.TopTaskPattern.builder()
                .groupName(groupName)
                .icon(icon)
                .count(count)
                .percent(percent)
                .build());
        }
        
        return patterns;
    }

    // EXPLAIN: Parent behavior metrics for ADM-10
    public AdminParentBehaviorResponse.ParentBehaviorMetrics getParentBehaviorMetrics(Instant periodStart) {
        double familiesUsingCatalogPercent = calcFamiliesUsingCatalogPercent(periodStart);
        double familiesUsingCustomContentPercent = calcFamiliesUsingCustomContentPercent(periodStart);
        double medianApprovalDelayHours = calcMedianApprovalDelayHours(periodStart);
        int pendingRequestsCount = countPendingRequests();
        int familiesWithPendingRequests = countFamiliesWithPendingRequests();
        double notificationsEnabledPercent = calcNotificationsEnabledPercent();

        return AdminParentBehaviorResponse.ParentBehaviorMetrics.builder()
            .familiesUsingCatalogPercent(Math.round(familiesUsingCatalogPercent * 100.0) / 100.0)
            .familiesUsingCustomContentPercent(Math.round(familiesUsingCustomContentPercent * 100.0) / 100.0)
            .medianApprovalDelayHours(Math.round(medianApprovalDelayHours * 100.0) / 100.0)
            .pendingRequestsCount(pendingRequestsCount)
            .familiesWithPendingRequests(familiesWithPendingRequests)
            .notificationsEnabledPercent(Math.round(notificationsEnabledPercent * 100.0) / 100.0)
            .build();
    }

    private double calcFamiliesUsingCatalogPercent(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyId = f.id
            JOIN HistoryEntryEntity h ON h.relatedId = c.id
            WHERE h.createdAt >= :periodStart
            AND h.groupName IS NOT NULL AND h.groupName != ''
            AND h.groupName != 'custom'
            """;
        Long familiesUsingCatalog = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();

        long totalActiveFamilies = countActiveFamilies(periodStart);
        return totalActiveFamilies > 0 ? (familiesUsingCatalog.doubleValue() / totalActiveFamilies) * 100.0 : 0.0;
    }

    private double calcFamiliesUsingCustomContentPercent(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyId = f.id
            JOIN HistoryEntryEntity h ON h.relatedId = c.id
            WHERE h.createdAt >= :periodStart
            AND (h.groupName IS NULL OR h.groupName = '' OR h.groupName = 'custom')
            """;
        Long familiesUsingCustom = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();

        long totalActiveFamilies = countActiveFamilies(periodStart);
        return totalActiveFamilies > 0 ? (familiesUsingCustom.doubleValue() / totalActiveFamilies) * 100.0 : 0.0;
    }

    private double calcMedianApprovalDelayHours(Instant periodStart) {
        String sql = """
            SELECT pr.updatedAt, pr.createdAt FROM PurchaseRequestEntity pr
            WHERE pr.status IN (:approved, :rejected)
            AND pr.updatedAt >= :periodStart
            AND pr.updatedAt IS NOT NULL
            """;
        var results = entityManager.createQuery(sql, Object[].class)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .setParameter("rejected", PurchaseRequestStatus.rejected)
            .setParameter("periodStart", periodStart)
            .getResultList();

        if (results.isEmpty()) return 0.0;

        List<Double> delays = new ArrayList<>();
        for (Object[] row : results) {
            Instant createdAt = (Instant) row[1];
            Instant updatedAt = (Instant) row[0];
            if (createdAt != null && updatedAt != null) {
                double hours = ChronoUnit.HOURS.between(createdAt, updatedAt);
                if (hours >= 0) {
                    delays.add(hours);
                }
            }
        }

        if (delays.isEmpty()) return 0.0;
        delays.sort(Double::compareTo);
        int n = delays.size();
        return (n % 2 == 0)
            ? (delays.get(n / 2 - 1) + delays.get(n / 2)) / 2.0
            : delays.get(n / 2);
    }

    private int countPendingRequests() {
        String sql = """
            SELECT COUNT(pr) FROM PurchaseRequestEntity pr
            WHERE pr.status = :pending
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("pending", PurchaseRequestStatus.pending)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countFamiliesWithPendingRequests() {
        String sql = """
            SELECT COUNT(DISTINCT c.familyId) FROM ChildEntity c
            JOIN PurchaseRequestEntity pr ON pr.childId = c.id
            WHERE pr.status = :pending
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("pending", PurchaseRequestStatus.pending)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private double calcNotificationsEnabledPercent() {
        // EXPLAIN: Notification settings not yet stored in FamilyEntity
        return 0.0;
    }
}
