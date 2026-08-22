package com.sashplatonov.earnit.kids.repository;

import com.sashplatonov.earnit.kids.domain.model.ChildEntity;
import com.sashplatonov.earnit.kids.domain.model.FamilyEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestEntity;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestStatus;
import com.sashplatonov.earnit.kids.domain.model.PurchaseRequestType;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
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
            familyId + "@test.com", "secret123");
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
        assertThatCode(() -> adminAnalyticsRepository.getActivationFunnel(Instant.EPOCH))
            .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void activationFunnelUsesRegisteredFamilyCohort() {
        int baselineRegistered = adminAnalyticsRepository.getActivationFunnel(Instant.EPOCH).get(0).getCount();
        int baselineWithChild = adminAnalyticsRepository.getActivationFunnel(Instant.EPOCH).get(1).getCount();
        FamilyEntity historicalFamily = seedFamily();
        seedChild(historicalFamily.getId());
        FamilyEntity recentFamily = seedFamily();
        seedChild(recentFamily.getId());

        Instant periodStart = Instant.now().plusSeconds(60);
        setCreatedAt(historicalFamily, periodStart.minusSeconds(60));
        setCreatedAt(recentFamily, periodStart.plusSeconds(60));

        var allStages = adminAnalyticsRepository.getActivationFunnel(Instant.EPOCH);
        var periodStages = adminAnalyticsRepository.getActivationFunnel(periodStart);

        assertThat(allStages.get(0).getCount()).isEqualTo(baselineRegistered + 2);
        assertThat(allStages.get(1).getCount()).isEqualTo(baselineWithChild + 2);
        assertThat(periodStages.get(0).getCount()).isEqualTo(1);
        assertThat(periodStages.get(1).getCount()).isEqualTo(1);
    }

    private ChildEntity seedChild(int familyDbId) {
        Optional<ChildEntity> child = childRepository.createChild(familyDbId, "Kid " + System.nanoTime());
        assertThat(child).isPresent();
        return child.get();
    }

    private void setCreatedAt(FamilyEntity family, Instant createdAt) {
        entityManager.createQuery("UPDATE FamilyEntity f SET f.createdAt = :createdAt WHERE f.id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", family.getId())
            .executeUpdate();
        entityManager.clear();
    }

    @Test
    @Transactional
    void rewardAnalyticsQueriesDoNotThrow() {
        // EXPLAIN: Production P0 — calcRewardRankings previously referenced the
        // EXPLAIN: non-existent PurchaseRequestEntity.category attribute, which
        // EXPLAIN: threw QuerySyntaxException and 500'd the whole dashboard. The
        // EXPLAIN: new reward queries must not throw and must return sane values.
        Instant periodStart = Instant.EPOCH;
        assertThatCode(() -> adminAnalyticsRepository.countAllRewardRequests(periodStart))
            .doesNotThrowAnyException();
        assertThatCode(() -> adminAnalyticsRepository.calcMedianRewardPrice(periodStart))
            .doesNotThrowAnyException();
        assertThatCode(() -> adminAnalyticsRepository.calcMedianPriceOfIssuedRewards(periodStart))
            .doesNotThrowAnyException();
        assertThatCode(() -> adminAnalyticsRepository.calcRewardRankings(periodStart))
            .doesNotThrowAnyException();
        assertThatCode(() -> adminAnalyticsRepository.countSuccessfulRewardPurchases(periodStart))
            .doesNotThrowAnyException();

        // EXPLAIN: Sanity — median prices are never negative.
        assertThat(adminAnalyticsRepository.calcMedianRewardPrice(periodStart)).isNotNegative();
        assertThat(adminAnalyticsRepository.calcMedianPriceOfIssuedRewards(periodStart)).isNotNegative();
    }

    @Test
    @Transactional
    void balanceMetricsExposeTimeToFirstReward() {
        // EXPLAIN: ADM-05 — the dashboard renders timeToFirstReward but the
        // EXPLAIN: DTO previously never populated it, so the metric always
        // EXPLAIN: displayed a dash. getBalanceMetrics must return the field
        // EXPLAIN: (0.0 when no approved reward exists yet) and must not throw.
        AdminCoinEconomyResponse.BalanceMetrics metrics =
            adminAnalyticsRepository.getBalanceMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTimeToFirstReward()).isNotNegative();
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
