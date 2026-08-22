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
public class AdminParentBehaviorRepository {

    @PersistenceContext
    EntityManager entityManager;

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
}
