package com.sashplatonov.earnit.kids.admin.infrastructure.persistence;

import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
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
public class AdminTaskAnalyticsRepository {

  @PersistenceContext EntityManager entityManager;

  private int countTotalFamilies() {
    return Math.toIntExact(
        entityManager
            .createQuery("SELECT COUNT(f) FROM FamilyEntity f", Long.class)
            .getSingleResult());
  }

  public AdminTasksResponse.TaskMetrics getTaskMetrics(Instant periodStart) {
    int tasksConfigured = countTasksConfigured();
    double familiesWithTasksPercent = percentFamiliesWithActiveTask();
    long taskCompletions = countTaskCompletionsInPeriod(periodStart);
    long approvedCompletions = countApprovedTaskCompletions(periodStart);
    long rejectedCompletions = countRejectedTaskCompletions(periodStart);
    double approvalRate =
        calcTaskApprovalRate(periodStart, approvedCompletions, rejectedCompletions);
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
    String sql =
        """
            SELECT COUNT(DISTINCT t.familyId) FROM TaskEntity t
            WHERE t.familyId IN (SELECT DISTINCT h.familyId FROM HistoryEntryEntity h)
            """;
    Long familiesWithTask = entityManager.createQuery(sql, Long.class).getSingleResult();
    int totalFamilies = countTotalFamilies();
    if (totalFamilies == 0) {
      return 0.0;
    }
    return Math.round(100.0 * familiesWithTask / totalFamilies);
  }

  private long countTaskCompletionsInPeriod(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(h) FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("type", HistoryEntryType.TASK_COMPLETED)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? result : 0L;
  }

  private long countApprovedTaskCompletions(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.requestType = :earnType AND p.status = :status AND p.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("earnType", PurchaseRequestType.earn)
            .setParameter("status", PurchaseRequestStatus.approved)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? result : 0L;
  }

  private long countRejectedTaskCompletions(Instant periodStart) {
    String sql =
        """
            SELECT COUNT(p) FROM PurchaseRequestEntity p
            WHERE p.requestType = :earnType AND p.status IN (:rejected, :cancelled) AND p.createdAt >= :periodStart
            """;
    Long result =
        entityManager
            .createQuery(sql, Long.class)
            .setParameter("earnType", PurchaseRequestType.earn)
            .setParameter("rejected", PurchaseRequestStatus.rejected)
            .setParameter("cancelled", PurchaseRequestStatus.cancelled)
            .setParameter("periodStart", periodStart)
            .getSingleResult();
    return result != null ? result : 0L;
  }

  private double calcTaskApprovalRate(Instant periodStart, long approved, long rejected) {
    long total = approved + rejected;
    if (total == 0) {
      return 0.0;
    }
    return Math.round(100.0 * approved / total);
  }

  private double calcMedianCoinsPerApprovedTask() {
    String sql =
        """
            SELECT p.coins FROM PurchaseRequestEntity p
            WHERE p.requestType = :earnType AND p.status = :status AND p.coins > 0
            """;
    var coins =
        entityManager
            .createQuery(sql, Integer.class)
            .setParameter("earnType", PurchaseRequestType.earn)
            .setParameter("status", PurchaseRequestStatus.approved)
            .getResultList();
    if (coins.isEmpty()) {
      return 0.0;
    }

    var sorted = coins.stream().sorted().toList();
    int n = sorted.size();
    return n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0 : sorted.get(n / 2);
  }

  private double calcMedianCompletionsPerActiveChild(Instant periodStart) {
    String sql =
        """
            SELECT h.childId, COUNT(h) as cnt
            FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            GROUP BY h.childId
            """;
    var results =
        entityManager
            .createQuery(sql, Object[].class)
            .setParameter("type", HistoryEntryType.TASK_COMPLETED)
            .setParameter("periodStart", periodStart)
            .getResultList();

    if (results.isEmpty()) {
      return 0.0;
    }

    var counts = results.stream().map(row -> ((Number) row[1]).longValue()).sorted().toList();

    int n = counts.size();
    return n % 2 == 0 ? (counts.get(n / 2 - 1) + counts.get(n / 2)) / 2.0 : counts.get(n / 2);
  }

  public List<AdminTasksResponse.TopTaskPattern> calcTopTaskPatterns(Instant periodStart) {
    String sql =
        """
            SELECT h.groupName, COUNT(h) as cnt
            FROM HistoryEntryEntity h
            WHERE h.type = :type AND h.createdAt >= :periodStart
            AND h.groupName IS NOT NULL AND h.groupName != ''
            GROUP BY h.groupName
            ORDER BY cnt DESC
            """;
    var results =
        entityManager
            .createQuery(sql, Object[].class)
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

      patterns.add(
          AdminTasksResponse.TopTaskPattern.builder()
              .groupName(groupName)
              .icon("")
              .count(count)
              .percent(percent)
              .build());
    }
    return patterns;
  }
}
