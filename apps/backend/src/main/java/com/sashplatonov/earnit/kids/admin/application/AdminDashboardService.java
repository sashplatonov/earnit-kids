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
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AdminDashboardService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminAnalyticsService overviewService;

    @Inject
    AdminCoinEconomyService coinEconomyService;

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

    @Inject
    AdminRewardsService rewardsService;

    @CacheResult(cacheName = "admin-dashboard")
    public AdminDashboardResponse getDashboard(AdminAnalyticsPeriod period) {
        List<String> unavailableSections = new ArrayList<>();
        AdminAnalyticsResponse overview = null;
        AdminCoinEconomyResponse coinEconomy = null;
        AdminTasksResponse tasks = null;
        AdminParentBehaviorResponse parentSignals = null;
        AdminChildBehaviorResponse childSignals = null;
        AdminActivationFunnelResponse activation = null;
        AdminRetentionResponse activity = null;
        AdminRewardsResponse rewards = null;

        try {
            overview = overviewService.getOverview(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("overview");
        }
        try {
            coinEconomy = coinEconomyService.getCoinEconomy(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("coinEconomy");
        }
        try {
            tasks = taskEconomyService.getTaskEconomy(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("tasks");
        }
        try {
            parentSignals = parentBehaviorService.getParentBehavior(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("parentBehavior");
        }
        try {
            childSignals = childBehaviorService.getChildBehavior(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("childBehavior");
        }
        try {
            activation = activationFunnelService.getActivationFunnel(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("activation");
        }
        try {
            activity = retentionService.getRetention(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("retention");
        }
        try {
            rewards = rewardsService.getRewardsAnalytics(period);
        } catch (RuntimeException exception) {
            unavailableSections.add("rewards");
        }

        return AdminDashboardResponse.builder()
            .overview(overview == null ? null : overview.getOverview())
            .coinEconomy(coinEconomy)
            .tasks(tasks)
            .parentSignals(parentSignals)
            .childSignals(childSignals)
            .activation(activation)
            .activity(activity)
            .rewards(rewards)
            .updatedAt(ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)))
            .unavailableSections(List.copyOf(unavailableSections))
            .build();
    }
}
