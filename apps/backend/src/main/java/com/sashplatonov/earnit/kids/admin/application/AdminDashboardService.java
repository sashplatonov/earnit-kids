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
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@ApplicationScoped
public class AdminDashboardService {

  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

  @Inject AdminAnalyticsService overviewService;

  @Inject AdminCoinEconomyService coinEconomyService;

  @Inject AdminTaskEconomyService taskEconomyService;

  @Inject AdminParentBehaviorService parentBehaviorService;

  @Inject AdminChildBehaviorService childBehaviorService;

  @Inject AdminActivationFunnelService activationFunnelService;

  @Inject AdminRetentionService retentionService;

  @Inject AdminRewardsService rewardsService;

  @Inject AdminTrendsService trendsService;

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
    AdminTrendsResponse trends = null;

    overview = load(() -> overviewService.getOverview(period), "overview", unavailableSections);
    coinEconomy = load(() -> coinEconomyService.getCoinEconomy(period), "coinEconomy", unavailableSections);
    tasks = load(() -> taskEconomyService.getTaskEconomy(period), "tasks", unavailableSections);
    parentSignals = load(() -> parentBehaviorService.getParentBehavior(period), "parentBehavior", unavailableSections);
    childSignals = load(() -> childBehaviorService.getChildBehavior(period), "childBehavior", unavailableSections);
    activation = load(() -> activationFunnelService.getActivationFunnel(period), "activation", unavailableSections);
    activity = load(() -> retentionService.getRetention(period), "retention", unavailableSections);
    rewards = load(() -> rewardsService.getRewardsAnalytics(period), "rewards", unavailableSections);
    trends = load(() -> trendsService.getTrends(period), "trends", unavailableSections);

    return AdminDashboardResponse.builder()
        .overview(overview == null ? null : overview.getOverview())
        .coinEconomy(coinEconomy)
        .tasks(tasks)
        .parentSignals(parentSignals)
        .childSignals(childSignals)
        .activation(activation)
        .activity(activity)
        .rewards(rewards)
        .trends(trends)
        .updatedAt(ISO_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC)))
        .unavailableSections(List.copyOf(unavailableSections))
        .build();
  }

  private <T> T load(Supplier<T> loader, String section, List<String> unavailableSections) {
    try {
      return loader.get();
    } catch (RuntimeException exception) {
      unavailableSections.add(section);
      return null;
    }
  }
}
