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
public class AdminChildBehaviorRepository {

    @PersistenceContext
    EntityManager entityManager;

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
}
