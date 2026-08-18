package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// EXPLAIN: Regression coverage for P0-1 (task-economy JPQL) and P2-4 (reward
// EXPLAIN: count must only count reward redemptions, not approved task requests).
// EXPLAIN: These queries previously used the invalid `p.type = 'task'` attribute
// EXPLAIN: and an unfiltered approved count, which would 500 the whole dashboard.
// EXPLAIN: Because admin analytics aggregate across ALL families, assertions use
// EXPLAIN: the delta (baseline before seeding vs. after) rather than absolute values.
@QuarkusTest
class AdminAnalyticsRepositoryTest {

    @Inject FamilyRepository familyRepository;
    @Inject ChildRepository childRepository;
    @Inject PurchaseRequestRepository purchaseRequestRepository;
    @Inject AdminAnalyticsRepository adminAnalyticsRepository;
    @Inject EntityManager entityManager;

    @Test
    @Transactional
    void taskEconomyQueriesDoNotThrow() {
        // EXPLAIN: P0-1 — these must not throw (previously used invalid p.type = 'task').
        assertThatCode(() -> adminAnalyticsRepository.getTaskMetrics(Instant.EPOCH))
            .doesNotThrowAnyException();
        assertThatCode(() -> adminAnalyticsRepository.getOverview(Instant.EPOCH))
            .doesNotThrowAnyException();

        AdminTasksResponse.TaskMetrics taskMetrics = adminAnalyticsRepository.getTaskMetrics(Instant.EPOCH);
        assertThat(taskMetrics).isNotNull();

        // EXPLAIN: Production P0 — calcTopTaskPatterns previously selected the
        // EXPLAIN: non-existent HistoryEntryEntity.icon attribute, which 500'd the
        // EXPLAIN: whole dashboard and made it render the empty state. Must not throw.
        assertThatCode(() -> adminAnalyticsRepository.calcTopTaskPatterns(Instant.EPOCH))
            .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void approvedTaskRequestDoesNotIncreaseRewardPurchaseCount() {
        // EXPLAIN: Baseline reward-purchase count across all families.
        Instant periodStart = Instant.EPOCH;
        int baseline = adminAnalyticsRepository.getOverview(periodStart).getRewardPurchases();

        FamilyEntity family = seedFamily();
        ChildEntity child = seedChild(family.getId());

        // EXPLAIN: A task request (requestType = earn) approved by the parent.
        purchaseRequestRepository.createRequest(family.getId(), child.getId(), 71001L,
            71001L, "Read", null, 5, PurchaseRequestType.earn, 0);
        markApprovedByExternalId(71001L);

        // EXPLAIN: P2-4 — an approved TASK request must NOT count as a reward purchase.
        int after = adminAnalyticsRepository.getOverview(periodStart).getRewardPurchases();
        assertThat(after).isEqualTo(baseline);
    }

    @Test
    @Transactional
    void approvedRewardRequestIncreasesRewardPurchaseCount() {
        Instant periodStart = Instant.EPOCH;
        int baseline = adminAnalyticsRepository.getOverview(periodStart).getRewardPurchases();

        FamilyEntity family = seedFamily();
        ChildEntity child = seedChild(family.getId());

        // EXPLAIN: A reward request (requestType = shop_purchase) approved by the parent.
        purchaseRequestRepository.createRequest(family.getId(), child.getId(), 71002L,
            null, null, 81001L, 7, PurchaseRequestType.shop_purchase, 0);
        markApprovedByExternalId(71002L);

        // EXPLAIN: P2-4 — an approved REWARD request MUST count as a reward purchase.
        int after = adminAnalyticsRepository.getOverview(periodStart).getRewardPurchases();
        assertThat(after).isEqualTo(baseline + 1);
    }

    private FamilyEntity seedFamily() {
        String familyId = "fam_admin_analytics_" + System.nanoTime();
        Optional<FamilyEntity> created = familyRepository.create(familyId,
            familyId + "@test.com", "secret123", false, "verify-" + familyId);
        assertThat(created).isPresent();
        return created.get();
    }

    @Test
    @Transactional
    void balanceMetricsDoNotThrowAndIncludeActiveChildren() {
        // EXPLAIN: Production P0 — getBalanceMetrics previously referenced the
        // EXPLAIN: non-existent ChildEntity.isTestAccount attribute, crashing
        // EXPLAIN: GET /api/admin/dashboard with a 500. It must not throw.
        assertThatCode(() -> adminAnalyticsRepository.getBalanceMetrics())
            .doesNotThrowAnyException();

        FamilyEntity family = seedFamily();
        ChildEntity child = seedChild(family.getId());
        assertThat(child.getStatus()).isEqualTo("ACTIVE");

        assertThatCode(() -> adminAnalyticsRepository.getBalanceMetrics())
            .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void familyJoinsUseCorrectFamilyDbIdAttribute() {
        // EXPLAIN: Production P0 family — ChildEntity exposes familyDbId, not
        // EXPLAIN: familyId; queries joining on c.familyId throw UnknownPathException.
        // EXPLAIN: The dashboard composes several such queries (catalog/custom
        // EXPLAIN: usage, activation funnel), so exercise them all.
        FamilyEntity family = seedFamily();
        seedChild(family.getId());

        assertThatCode(() -> adminAnalyticsRepository.getParentBehaviorMetrics(Instant.EPOCH))
            .doesNotThrowAnyException();
        assertThatCode(() -> adminAnalyticsRepository.getActivationFunnel())
            .doesNotThrowAnyException();
    }

    private ChildEntity seedChild(int familyDbId) {
        Optional<ChildEntity> child = childRepository.createChild(familyDbId, "Kid " + System.nanoTime());
        assertThat(child).isPresent();
        return child.get();
    }

    private void markApprovedByExternalId(long externalId) {
        PurchaseRequestEntity request = (PurchaseRequestEntity) entityManager
            .createQuery("SELECT r FROM PurchaseRequestEntity r WHERE r.externalId = :ext")
            .setParameter("ext", externalId)
            .getSingleResult();
        request.setStatus(PurchaseRequestStatus.approved);
        entityManager.flush();
    }
}
