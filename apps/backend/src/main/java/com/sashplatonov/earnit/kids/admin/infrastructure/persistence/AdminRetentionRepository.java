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
public class AdminRetentionRepository {

    @PersistenceContext
    EntityManager entityManager;

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
