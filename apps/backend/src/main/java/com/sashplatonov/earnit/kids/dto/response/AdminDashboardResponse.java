package com.sashplatonov.earnit.kids.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminDashboardResponse {

    private AdminAnalyticsResponse.Overview overview;
    private AdminCoinEconomyResponse coinEconomy;
    private AdminTasksResponse tasks;
    private AdminParentBehaviorResponse parentSignals;
    private AdminChildBehaviorResponse childSignals;
    private AdminActivationFunnelResponse activation;
    private AdminRetentionResponse activity;
    private AdminRewardsResponse rewards;
    private AdminTrendsResponse trends;
    private String updatedAt;
    private List<String> unavailableSections;
}
