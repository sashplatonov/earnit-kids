package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ChildStatus;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.HistoryEntryType;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;

import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
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

    public int countActiveFamilies(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT h.familyId) FROM HistoryEntryEntity h
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
            WHERE t.familyId IN (SELECT DISTINCT h.familyId FROM HistoryEntryEntity h)
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
            WHERE p.requestType = :earnType AND p.status = :status AND p.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("earnType", PurchaseRequestType.earn)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    private long countRejectedTaskCompletions(Instant periodStart) {
        String sql = """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.requestType = :earnType AND p.status IN (:rejected, :cancelled) AND p.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("earnType", PurchaseRequestType.earn)
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
            WHERE p.requestType = :earnType AND p.status = :status AND p.coins > 0
            """;
        var coins = entityManager.createQuery(sql, Integer.class)
            .setParameter("earnType", PurchaseRequestType.earn)
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
            SELECT h.groupName, COUNT(h) as cnt
            FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            AND h.groupName IS NOT NULL AND h.groupName != ''
            GROUP BY h.groupName
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
            long count = ((Number) row[1]).longValue();
            double percent = total > 0 ? Math.round(100.0 * count / total) : 0.0;

            patterns.add(AdminTasksResponse.TopTaskPattern.builder()
                .groupName(groupName)
                .icon("")
                .count(count)
                .percent(percent)
                .build());
        }
        return patterns;
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
            JOIN ChildEntity c ON c.familyDbId = f.id
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
            JOIN ChildEntity c ON c.familyDbId = f.id
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
            SELECT COUNT(DISTINCT c.familyDbId) FROM ChildEntity c
            JOIN PurchaseRequestEntity pr ON pr.childId = c.id
            WHERE pr.status = :pending
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("pending", PurchaseRequestStatus.pending)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private double calcNotificationsEnabledPercent() {
        return 0.0;
    }

    public AdminChildBehaviorResponse.ChildBehaviorMetrics getChildBehaviorMetrics(Instant periodStart) {
        double medianActiveDaysPerChild = calcMedianActiveDaysPerChild(periodStart);
        double medianTasksBeforeReward = calcMedianTasksBeforeReward(periodStart);
        int childrenEarningNotSpending = countChildrenEarningNotSpending(periodStart);
        int totalActiveChildren = countActiveChildren(periodStart);
        double percentChildrenEarningNotSpending = totalActiveChildren > 0
            ? (childrenEarningNotSpending * 100.0) / totalActiveChildren : 0.0;
        
        int childrenRequestedNotReceived = countChildrenRequestedNotReceived(periodStart);
        double percentChildrenRequestedNotReceived = totalActiveChildren > 0
            ? (childrenRequestedNotReceived * 100.0) / totalActiveChildren : 0.0;

        return AdminChildBehaviorResponse.ChildBehaviorMetrics.builder()
            .medianActiveDaysPerChild(Math.round(medianActiveDaysPerChild * 100.0) / 100.0)
            .medianTasksBeforeReward(Math.round(medianTasksBeforeReward * 100.0) / 100.0)
            .childrenEarningNotSpending(childrenEarningNotSpending)
            .percentChildrenEarningNotSpending(Math.round(percentChildrenEarningNotSpending * 100.0) / 100.0)
            .childrenRequestedNotReceived(childrenRequestedNotReceived)
            .percentChildrenRequestedNotReceived(Math.round(percentChildrenRequestedNotReceived * 100.0) / 100.0)
            .build();
    }

    private double calcMedianActiveDaysPerChild(Instant periodStart) {
        String sql = """
            SELECT c.id, COUNT(DISTINCT DATE(h.createdAt)) as activeDays
            FROM ChildEntity c
            JOIN HistoryEntryEntity h ON h.relatedId = c.id
            WHERE h.createdAt >= :periodStart
            GROUP BY c.id
            """;
        var results = entityManager.createQuery(sql, Object[].class)
            .setParameter("periodStart", periodStart)
            .getResultList();
        
        if (results.isEmpty()) return 0.0;
        
        List<Double> activeDays = new ArrayList<>();
        for (Object[] row : results) {
            activeDays.add(((Number) row[1]).doubleValue());
        }
        
        activeDays.sort(Double::compareTo);
        int n = activeDays.size();
        return (n % 2 == 0)
            ? (activeDays.get(n / 2 - 1) + activeDays.get(n / 2)) / 2.0
            : activeDays.get(n / 2);
    }

    private double calcMedianTasksBeforeReward(Instant periodStart) {
        String sql = """
            SELECT pr.childId, COUNT(h.id) as taskCount
            FROM PurchaseRequestEntity pr
            JOIN ChildEntity c ON c.id = pr.childId
            LEFT JOIN HistoryEntryEntity h ON h.relatedId = c.id
                AND h.type = :taskType
                AND h.createdAt < pr.createdAt
            WHERE pr.status = :approved
            AND pr.createdAt >= :periodStart
            GROUP BY pr.childId, pr.createdAt
            ORDER BY pr.createdAt
            """;
        var results = entityManager.createQuery(sql, Object[].class)
            .setParameter("taskType", HistoryEntryType.TASK_COMPLETED)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getResultList();
        
        if (results.isEmpty()) return 0.0;
        
        List<Double> taskCounts = new ArrayList<>();
        for (Object[] row : results) {
            taskCounts.add(((Number) row[1]).doubleValue());
        }
        
        taskCounts.sort(Double::compareTo);
        int n = taskCounts.size();
        return (n % 2 == 0)
            ? (taskCounts.get(n / 2 - 1) + taskCounts.get(n / 2)) / 2.0
            : taskCounts.get(n / 2);
    }

    private int countChildrenEarningNotSpending(Instant periodStart) {
        String earnedSql = """
            SELECT DISTINCT c.id FROM ChildEntity c
            JOIN HistoryEntryEntity h ON h.relatedId = c.id
            WHERE h.type = :earnType AND h.createdAt >= :periodStart
            """;
        List<Integer> earnedChildIds = entityManager.createQuery(earnedSql, Integer.class)
            .setParameter("earnType", HistoryEntryType.earn)
            .setParameter("periodStart", periodStart)
            .getResultList();
        
        if (earnedChildIds.isEmpty()) return 0;
        
        String spentSql = """
            SELECT DISTINCT c.id FROM ChildEntity c
            JOIN PurchaseRequestEntity pr ON pr.childId = c.id
            WHERE pr.status = :approved AND pr.createdAt >= :periodStart
            """;
        List<Integer> spentChildIds = entityManager.createQuery(spentSql, Integer.class)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getResultList();
        
        int count = 0;
        for (Integer childId : earnedChildIds) {
            if (!spentChildIds.contains(childId)) {
                count++;
            }
        }
        return count;
    }

    private int countChildrenRequestedNotReceived(Instant periodStart) {
        String requestedSql = """
            SELECT DISTINCT pr.childId FROM PurchaseRequestEntity pr
            WHERE pr.createdAt >= :periodStart
            """;
        List<Integer> requestedChildIds = entityManager.createQuery(requestedSql, Integer.class)
            .setParameter("periodStart", periodStart)
            .getResultList();
        
        if (requestedChildIds.isEmpty()) return 0;
        
        String receivedSql = """
            SELECT DISTINCT pr.childId FROM PurchaseRequestEntity pr
            WHERE pr.status = :approved AND pr.createdAt >= :periodStart
            """;
        List<Integer> receivedChildIds = entityManager.createQuery(receivedSql, Integer.class)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getResultList();
        
        int count = 0;
        for (Integer childId : requestedChildIds) {
            if (!receivedChildIds.contains(childId)) {
                count++;
            }
        }
        return count;
    }

    public List<AdminActivationFunnelResponse.FunnelStage> getActivationFunnel(Instant periodStart) {
        int registered = countTotalFamilies(periodStart);
        int addedChild = countFamiliesWithChild(periodStart);
        int hasTask = countFamiliesWithTask(periodStart);
        int childCompletedTask = countFamiliesWithTaskCompletion(periodStart);
        int earnedCoins = countFamiliesWithCoinEarn(periodStart);
        int hasReward = countFamiliesWithRewardConfigured(periodStart);
        int receivedReward = countFamiliesWithRewardReceived(periodStart);

        List<AdminActivationFunnelResponse.FunnelStage> stages = new ArrayList<>();
        
        stages.add(createStage("registered", "Зарегистрировались", registered, registered));
        stages.add(createStage("added_child", "Добавили ребёнка", addedChild, registered, addedChild));
        stages.add(createStage("has_task", "Есть задание", hasTask, addedChild, hasTask));
        stages.add(createStage("completed_task", "Выполнили задание", childCompletedTask, hasTask, childCompletedTask));
        stages.add(createStage("earned_coins", "Заработали монеты", earnedCoins, childCompletedTask, earnedCoins));
        stages.add(createStage("has_reward", "Есть награда", hasReward, earnedCoins, hasReward));
        stages.add(createStage("received_reward", "Получили награду", receivedReward, hasReward, receivedReward));

        return stages;
    }

    private AdminActivationFunnelResponse.FunnelStage createStage(String key, String label, int count, int previousCount) {
        double percentFromPrevious = previousCount > 0 ? (count * 100.0) / previousCount : 0.0;
        return AdminActivationFunnelResponse.FunnelStage.builder()
            .key(key)
            .label(label)
            .count(count)
            .percentFromPrevious(Math.round(percentFromPrevious * 100.0) / 100.0)
            .build();
    }

    private AdminActivationFunnelResponse.FunnelStage createStage(String key, String label, int count, int previousCount, int initialCount) {
        double percentFromPrevious = previousCount > 0 ? (count * 100.0) / previousCount : 0.0;
        double percentFromInitial = initialCount > 0 ? (count * 100.0) / initialCount : 0.0;
        return AdminActivationFunnelResponse.FunnelStage.builder()
            .key(key)
            .label(label)
            .count(count)
            .percentFromPrevious(Math.round(percentFromPrevious * 100.0) / 100.0)
            .percentFromInitial(Math.round(percentFromInitial * 100.0) / 100.0)
            .build();
    }

    private int countTotalFamilies(Instant periodStart) {
        String sql = "SELECT COUNT(f) FROM FamilyEntity f WHERE f.createdAt >= :periodStart";
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countFamiliesWithChild(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyDbId = f.id
            WHERE f.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart).getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countFamiliesWithTask(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyDbId = f.id
            JOIN TaskEntity t ON t.childId = c.id
            WHERE f.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart).getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countFamiliesWithTaskCompletion(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyDbId = f.id
            JOIN HistoryEntryEntity h ON h.relatedId = c.id
            WHERE f.createdAt >= :periodStart AND h.type = :type
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.TASK_COMPLETED).setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countFamiliesWithCoinEarn(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyDbId = f.id
            JOIN HistoryEntryEntity h ON h.relatedId = c.id
            WHERE f.createdAt >= :periodStart AND h.type = :type
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.earn).setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countFamiliesWithRewardConfigured(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyDbId = f.id
            JOIN ShopItemEntity s ON s.childId = c.id
            WHERE f.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart).getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    private int countFamiliesWithRewardReceived(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT f.id) FROM FamilyEntity f
            JOIN ChildEntity c ON c.familyDbId = f.id
            JOIN PurchaseRequestEntity pr ON pr.childId = c.id
            WHERE f.createdAt >= :periodStart
            AND pr.status = :approved AND pr.requestType IN (:shop, :shopPurchase)
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("approved", PurchaseRequestStatus.approved)
            .setParameter("shop", PurchaseRequestType.shop)
            .setParameter("shopPurchase", PurchaseRequestType.shop_purchase)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    public int countNewFamilies(Instant periodStart) {
        String sql = """
            SELECT COUNT(f) FROM FamilyEntity f
            WHERE f.createdAt >= :periodStart
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    public int countReturningFamilies(Instant periodStart) {
        String sql = """
            SELECT COUNT(DISTINCT h.familyId) FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            AND h.familyId IN (
                SELECT f.id FROM FamilyEntity f
                WHERE f.createdAt < :periodStart
            )
            """;
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
    }

    public List<AdminTrendsResponse.TrendPoint> getTrendPoints(Instant periodStart, Instant now) {
        String historySql = """
            SELECT FUNCTION('DATE', h.createdAt) as day,
                   COALESCE(SUM(CASE WHEN h.type = :earn THEN h.amount ELSE 0 END), 0) as earned,
                   COALESCE(SUM(CASE WHEN h.type = :spend THEN ABS(h.amount) ELSE 0 END), 0) as spent,
                   COALESCE(SUM(CASE WHEN h.type = :taskType THEN 1 ELSE 0 END), 0) as tasks
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

        String familiesSql = """
            SELECT FUNCTION('DATE', h.createdAt) as day, COUNT(DISTINCT h.familyId) as families
            FROM HistoryEntryEntity h
            WHERE h.createdAt >= :periodStart
            GROUP BY FUNCTION('DATE', h.createdAt)
            ORDER BY day
            """;
        var familyResults = entityManager.createQuery(familiesSql, Object[].class)
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
        }

        for (Object[] row : familyResults) {
            java.sql.Date day = (java.sql.Date) row[0];
            long families = ((Number) row[1]).longValue();
            byDay.computeIfAbsent(day, d -> AdminTrendsResponse.TrendPoint.builder()
                    .date(d.toLocalDate().toString())
                    .activeFamilies(0)
                    .coinsEarned(0)
                    .coinsSpent(0)
                    .rewardRedemptions(0)
                    .taskCompletions(0)
                    .build());
            byDay.get(day).setActiveFamilies((int) families);
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
