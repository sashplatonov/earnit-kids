package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminActivationAnalyticsRepository;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminChildBehaviorRepository;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminCoinEconomyRepository;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminOverviewAnalyticsRepository;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminParentBehaviorRepository;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminRewardAnalyticsRepository;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminRetentionRepository;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminTaskAnalyticsRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServicesTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Mock AdminRewardAnalyticsRepository rewardRepository;
    @Mock AdminCoinEconomyRepository coinRepository;
    @Mock AdminRetentionRepository retentionRepository;
    @Mock AdminTaskAnalyticsRepository taskRepository;
    @Mock AdminParentBehaviorRepository parentRepository;
    @Mock AdminChildBehaviorRepository childRepository;
    @Mock AdminActivationAnalyticsRepository activationRepository;
    @Mock AdminOverviewAnalyticsRepository overviewRepository;

    @Test
    void rewardsAnalytics_calculatesFailureRateAndBuildsRankings() {
        AdminRewardsService service = new AdminRewardsService();
        service.repository = rewardRepository;
        AdminRewardsResponse.RewardRanking ranking = AdminRewardsResponse.RewardRanking.builder()
            .category("games").count(2).rank(1).build();
        when(rewardRepository.countAllRewardRequests(any())).thenReturn(8);
        when(rewardRepository.countSuccessfulRewardPurchases(any())).thenReturn(6);
        when(rewardRepository.calcMedianRewardPrice(any())).thenReturn(12.5);
        when(rewardRepository.calcMedianPriceOfIssuedRewards(any())).thenReturn(10.0);
        when(rewardRepository.calcRewardRankings(any())).thenReturn(List.of(ranking));

        AdminRewardsResponse result = service.getRewardsAnalytics(AdminAnalyticsPeriod.parse("30d"));

        assertThat(result.getMetrics().getRequestCount()).isEqualTo(8);
        assertThat(result.getMetrics().getIssuedCount()).isEqualTo(6);
        assertThat(result.getMetrics().getFailedRate()).isEqualTo(25.0);
        assertThat(result.getRankings()).containsExactly(ranking);
        assertThat(result.getUpdatedAt()).isNotBlank();
    }

    @Test
    void coinEconomy_loadsAllMetricGroups() {
        AdminCoinEconomyService service = new AdminCoinEconomyService();
        service.repository = coinRepository;
        AdminCoinEconomyResponse.CoinMetrics coins = AdminCoinEconomyResponse.CoinMetrics.builder()
            .earned(100).spent(40).activeChildren(5).build();
        AdminCoinEconomyResponse.BalanceMetrics balances = AdminCoinEconomyResponse.BalanceMetrics.builder()
            .averageBalance(12.5).build();
        AdminCoinEconomyResponse.RewardMetrics rewards = AdminCoinEconomyResponse.RewardMetrics.builder()
            .familiesWithReward(3).build();
        when(coinRepository.countActiveChildren(any())).thenReturn(5);
        when(coinRepository.getCoinMetrics(any(), org.mockito.ArgumentMatchers.eq(5))).thenReturn(coins);
        when(coinRepository.getBalanceMetrics()).thenReturn(balances);
        when(coinRepository.getRewardMetrics(any())).thenReturn(rewards);

        AdminCoinEconomyResponse result = service.getCoinEconomy(AdminAnalyticsPeriod.parse("7d"));

        assertThat(result.getCoins()).isSameAs(coins);
        assertThat(result.getBalances()).isSameAs(balances);
        assertThat(result.getRewards()).isSameAs(rewards);
        verify(coinRepository).getBalanceMetrics();
    }

    @Test
    void retention_loadsPeriodAndRollingActivityMetrics() {
        AdminRetentionService service = new AdminRetentionService();
        service.repository = retentionRepository;
        when(retentionRepository.countNewFamilies(any())).thenReturn(4);
        when(retentionRepository.countReturningFamilies(any())).thenReturn(3);
        when(retentionRepository.countActiveFamilies(any())).thenReturn(9, 7, 6);

        AdminRetentionResponse result = service.getRetention(AdminAnalyticsPeriod.parse("90d"));

        assertThat(result.getRetentionMetrics().getNewFamilies()).isEqualTo(4);
        assertThat(result.getRetentionMetrics().getReturningFamilies()).isEqualTo(3);
        assertThat(result.getRetentionMetrics().getActiveFamilies()).isEqualTo(9);
        assertThat(result.getRetentionMetrics().getActive7d()).isEqualTo(7);
        assertThat(result.getRetentionMetrics().getActive30d()).isEqualTo(6);
    }

    @Test
    void taskAndParentServices_mapRepositoryData() {
        AdminTasksResponse.TaskMetrics taskMetrics = AdminTasksResponse.TaskMetrics.builder().tasksConfigured(11).build();
        AdminParentBehaviorResponse.ParentBehaviorMetrics parentMetrics =
            AdminParentBehaviorResponse.ParentBehaviorMetrics.builder().pendingRequestsCount(4).build();
        when(taskRepository.getTaskMetrics(any())).thenReturn(taskMetrics);
        when(taskRepository.calcTopTaskPatterns(any())).thenReturn(List.of());
        when(parentRepository.getParentBehaviorMetrics(any())).thenReturn(parentMetrics);

        AdminTasksResponse tasks = new AdminTaskEconomyService(taskRepository)
            .getTaskEconomy(AdminAnalyticsPeriod.parse(null, NOW));
        AdminParentBehaviorService parents = new AdminParentBehaviorService();
        parents.repository = parentRepository;
        AdminParentBehaviorResponse parentResult = parents.getParentBehavior(AdminAnalyticsPeriod.parse("", NOW));

        assertThat(tasks.getMetrics()).isSameAs(taskMetrics);
        assertThat(parentResult.getParentBehaviorMetrics()).isSameAs(parentMetrics);
    }

    @Test
    void childBehaviorAndActivationServices_mapRepositoryData() {
        AdminChildBehaviorResponse.ChildBehaviorMetrics childMetrics =
            AdminChildBehaviorResponse.ChildBehaviorMetrics.builder().childrenEarningNotSpending(5).build();
        when(childRepository.getChildBehaviorMetrics(any())).thenReturn(childMetrics);
        when(activationRepository.getActivationFunnel(any())).thenReturn(List.of());
        AdminChildBehaviorService children = new AdminChildBehaviorService();
        children.repository = childRepository;
        AdminActivationFunnelService activation = new AdminActivationFunnelService();
        activation.repository = activationRepository;

        assertThat(children.getChildBehavior(AdminAnalyticsPeriod.parse("7d")).getChildBehaviorMetrics())
            .isSameAs(childMetrics);
        assertThat(activation.getActivationFunnel(AdminAnalyticsPeriod.parse("7d")).getStages()).isEmpty();
    }

    @Test
    void analyticsService_usesInjectedClock() {
        TimeProvider timeProvider = () -> NOW;
        AdminAnalyticsResponse.Overview overview = AdminAnalyticsResponse.Overview.builder()
            .totalFamilies(2).build();
        when(overviewRepository.getOverview(NOW.minusSeconds(30L * 24 * 60 * 60))).thenReturn(overview);
        AdminAnalyticsService service = new AdminAnalyticsService(overviewRepository, timeProvider);

        AdminAnalyticsResponse result = service.getOverview(AdminAnalyticsPeriod.parse("30d", NOW));

        assertThat(result.getOverview()).isSameAs(overview);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW.toString());
    }
}
