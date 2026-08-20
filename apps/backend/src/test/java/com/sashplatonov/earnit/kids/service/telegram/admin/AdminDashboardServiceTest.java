package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminDashboardResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        AdminAnalyticsResponse overview = mock(AdminAnalyticsResponse.class);
        AdminAnalyticsResponse.Overview overviewData = mock(AdminAnalyticsResponse.Overview.class);
        when(overview.getOverview()).thenReturn(overviewData);
        when(overviewService.getOverview("30d")).thenReturn(overview);
        AdminCoinEconomyResponse coins = mock(AdminCoinEconomyResponse.class);
        AdminTasksResponse tasks = mock(AdminTasksResponse.class);
        AdminParentBehaviorResponse parents = mock(AdminParentBehaviorResponse.class);
        AdminChildBehaviorResponse children = mock(AdminChildBehaviorResponse.class);
        AdminActivationFunnelResponse activation = mock(AdminActivationFunnelResponse.class);
        AdminRetentionResponse retention = mock(AdminRetentionResponse.class);
        AdminRewardsResponse rewards = mock(AdminRewardsResponse.class);
        when(coinService.getCoinEconomy("30d")).thenReturn(coins);
        when(taskService.getTaskEconomy("30d")).thenReturn(tasks);
        when(parentService.getParentBehavior("30d")).thenReturn(parents);
        when(childService.getChildBehavior("30d")).thenReturn(children);
        when(activationService.getActivationFunnel()).thenReturn(activation);
        when(retentionService.getRetention("30d")).thenReturn(retention);
        when(rewardsService.getRewardsAnalytics("30d")).thenReturn(rewards);

        AdminDashboardService service = new AdminDashboardService();
        service.overviewService = overviewService;
        service.coinEconomyService = coinService;
        service.taskEconomyService = taskService;
        service.parentBehaviorService = parentService;
        service.childBehaviorService = childService;
        service.activationFunnelService = activationService;
        service.retentionService = retentionService;
        service.rewardsService = rewardsService;

        AdminDashboardResponse result = service.getDashboard("30d");

        assertThat(result.getOverview()).isSameAs(overviewData);
        assertThat(result.getCoinEconomy()).isSameAs(coins);
        assertThat(result.getTasks()).isSameAs(tasks);
        assertThat(result.getParentSignals()).isSameAs(parents);
        assertThat(result.getChildSignals()).isSameAs(children);
        assertThat(result.getActivation()).isSameAs(activation);
        assertThat(result.getActivity()).isSameAs(retention);
        assertThat(result.getRewards()).isSameAs(rewards);
        assertThat(result.getUpdatedAt()).isNotBlank();
    }
}
