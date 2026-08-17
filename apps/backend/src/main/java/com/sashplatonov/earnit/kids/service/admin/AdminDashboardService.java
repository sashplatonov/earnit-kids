package com.sashplatonov.earnit.kids.service.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminActivationFunnelResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminCoinEconomyResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminDashboardResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// EXPLAIN: ADM-16: Aggregated dashboard endpoint composing all section services
@ApplicationScoped
public class AdminDashboardService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminAnalyticsService overviewService;

    @Inject
    AdminCoinEconomyService coinEconomyService;

    @Inject
    AdminRewardShopService rewardShopService;

    @Inject
    AdminTaskEconomyService taskEconomyService;

    @Inject
    AdminParentBehaviorService parentBehaviorService;

    @Inject
    AdminChildBehaviorService childBehaviorService;

    @Inject
    AdminActivationFunnelService activationFunnelService;

    @Inject
    AdminRetentionService retentionService;

    public AdminDashboardResponse getDashboard(String period) {
        AdminAnalyticsResponse overview = overviewService.getOverview(period);
        AdminCoinEconomyResponse coinEconomy = coinEconomyService.getCoinEconomy(period);
        AdminRewardsResponse rewardShop = rewardShopService.getRewardShop(period);
        AdminTasksResponse tasks = taskEconomyService.getTaskEconomy(period);
        AdminParentBehaviorResponse parentSignals = parentBehaviorService.getParentBehavior(period);
        AdminChildBehaviorResponse childSignals = childBehaviorService.getChildBehavior(period);
        AdminActivationFunnelResponse activation = activationFunnelService.getActivationFunnel();
        AdminRetentionResponse activity = retentionService.getRetention(period);

        return AdminDashboardResponse.builder()
            .overview(overview.getOverview())
            .coinEconomy(coinEconomy)
            .rewardShop(rewardShop)
            .tasks(tasks)
            .parentSignals(parentSignals)
            .childSignals(childSignals)
            .activation(activation)
            .activity(activity)
            .updatedAt(ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)))
            .build();
    }
}
