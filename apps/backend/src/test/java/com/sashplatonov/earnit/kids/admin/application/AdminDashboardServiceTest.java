package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminDashboardResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class AdminDashboardServiceTest {

    @Test
    void getDashboard_composesAllAnalyticsSections() {
        AdminAnalyticsService overviewService = mock(AdminAnalyticsService.class);
        AdminCoinEconomyService coinService = mock(AdminCoinEconomyService.class);
        AdminTaskEconomyService taskService = mock(AdminTaskEconomyService.class);
        AdminParentBehaviorService parentService = mock(AdminParentBehaviorService.class);
        AdminChildBehaviorService childService = mock(AdminChildBehaviorService.class);
        AdminActivationFunnelService activationService = mock(AdminActivationFunnelService.class);
        AdminRetentionService retentionService = mock(AdminRetentionService.class);
        AdminRewardsService rewardsService = mock(AdminRewardsService.class);
        AdminTrendsService trendsService = mock(AdminTrendsService.class);
        AdminAnalyticsResponse overview = mock(AdminAnalyticsResponse.class);
        AdminAnalyticsResponse.Overview overviewData = mock(AdminAnalyticsResponse.Overview.class);
        when(overview.getOverview()).thenReturn(overviewData);
        AdminAnalyticsPeriod period = AdminAnalyticsPeriod.parse("30d");
        when(overviewService.getOverview(period)).thenReturn(overview);
        AdminCoinEconomyResponse coins = mock(AdminCoinEconomyResponse.class);
        AdminTasksResponse tasks = mock(AdminTasksResponse.class);
        AdminParentBehaviorResponse parents = mock(AdminParentBehaviorResponse.class);
        AdminChildBehaviorResponse children = mock(AdminChildBehaviorResponse.class);
        AdminActivationFunnelResponse activation = mock(AdminActivationFunnelResponse.class);
        AdminRetentionResponse retention = mock(AdminRetentionResponse.class);
        AdminRewardsResponse rewards = mock(AdminRewardsResponse.class);
        AdminTrendsResponse trends = mock(AdminTrendsResponse.class);
        when(coinService.getCoinEconomy(period)).thenReturn(coins);
        when(taskService.getTaskEconomy(period)).thenReturn(tasks);
        when(parentService.getParentBehavior(period)).thenReturn(parents);
        when(childService.getChildBehavior(period)).thenReturn(children);
        when(activationService.getActivationFunnel(period)).thenReturn(activation);
        when(retentionService.getRetention(period)).thenReturn(retention);
        when(rewardsService.getRewardsAnalytics(period)).thenReturn(rewards);
        when(trendsService.getTrends(period)).thenReturn(trends);

        AdminDashboardService service = new AdminDashboardService();
        service.overviewService = overviewService;
        service.coinEconomyService = coinService;
        service.taskEconomyService = taskService;
        service.parentBehaviorService = parentService;
        service.childBehaviorService = childService;
        service.activationFunnelService = activationService;
        service.retentionService = retentionService;
        service.rewardsService = rewardsService;
        service.trendsService = trendsService;

        AdminDashboardResponse result = service.getDashboard(period);

        assertThat(result.getOverview()).isSameAs(overviewData);
        assertThat(result.getCoinEconomy()).isSameAs(coins);
        assertThat(result.getTasks()).isSameAs(tasks);
        assertThat(result.getParentSignals()).isSameAs(parents);
        assertThat(result.getChildSignals()).isSameAs(children);
        assertThat(result.getActivation()).isSameAs(activation);
        assertThat(result.getActivity()).isSameAs(retention);
        assertThat(result.getRewards()).isSameAs(rewards);
        assertThat(result.getTrends()).isSameAs(trends);
        assertThat(result.getUpdatedAt()).isNotBlank();
        assertThat(result.getUnavailableSections()).isEmpty();
    }

    @Test
    void getDashboard_keepsAvailableSectionsWhenOneAnalyticsServiceFails() {
        AdminAnalyticsService overviewService = mock(AdminAnalyticsService.class);
        AdminCoinEconomyService coinService = mock(AdminCoinEconomyService.class);
        AdminTaskEconomyService taskService = mock(AdminTaskEconomyService.class);
        AdminParentBehaviorService parentService = mock(AdminParentBehaviorService.class);
        AdminChildBehaviorService childService = mock(AdminChildBehaviorService.class);
        AdminActivationFunnelService activationService = mock(AdminActivationFunnelService.class);
        AdminRetentionService retentionService = mock(AdminRetentionService.class);
        AdminRewardsService rewardsService = mock(AdminRewardsService.class);
        AdminTrendsService trendsService = mock(AdminTrendsService.class);
        AdminAnalyticsPeriod period = AdminAnalyticsPeriod.parse("7d");
        AdminAnalyticsResponse overview = mock(AdminAnalyticsResponse.class);
        AdminAnalyticsResponse.Overview overviewData = mock(AdminAnalyticsResponse.Overview.class);
        AdminTrendsResponse trends = mock(AdminTrendsResponse.class);
        AdminCoinEconomyResponse coins = mock(AdminCoinEconomyResponse.class);
        when(overviewService.getOverview(period)).thenReturn(overview);
        when(overview.getOverview()).thenReturn(overviewData);
        doThrow(new IllegalStateException("database unavailable")).when(coinService).getCoinEconomy(period);
        when(trendsService.getTrends(period)).thenReturn(trends);

        AdminDashboardService service = new AdminDashboardService();
        service.overviewService = overviewService;
        service.coinEconomyService = coinService;
        service.taskEconomyService = taskService;
        service.parentBehaviorService = parentService;
        service.childBehaviorService = childService;
        service.activationFunnelService = activationService;
        service.retentionService = retentionService;
        service.rewardsService = rewardsService;
        service.trendsService = trendsService;

        AdminDashboardResponse result = service.getDashboard(period);

        assertThat(result.getOverview()).isSameAs(overviewData);
        assertThat(result.getCoinEconomy()).isNull();
        assertThat(result.getTrends()).isSameAs(trends);
        assertThat(result.getUnavailableSections()).containsExactly("coinEconomy");
    }
}
