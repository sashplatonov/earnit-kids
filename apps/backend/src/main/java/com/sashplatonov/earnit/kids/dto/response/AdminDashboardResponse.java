package com.sashplatonov.earnit.kids.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// EXPLAIN: ADM-16: Aggregated dashboard response
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
    private String updatedAt;
}
