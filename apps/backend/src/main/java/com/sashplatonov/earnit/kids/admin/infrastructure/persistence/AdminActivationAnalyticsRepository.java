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
public class AdminActivationAnalyticsRepository {

    @PersistenceContext
    EntityManager entityManager;

    private int countTotalFamilies(Instant periodStart) {
        String sql = "SELECT COUNT(f) FROM FamilyEntity f WHERE f.createdAt >= :periodStart";
        Long result = entityManager.createQuery(sql, Long.class)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
        return result != null ? Math.toIntExact(result) : 0;
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

}
