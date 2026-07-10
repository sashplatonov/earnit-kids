package com.sashplatonov.earnit.kids.dto.response;

import java.util.List;

public record AnalyticsResponse(
    AnalyticsSummary summary,
    List<AnalyticsStatItem> topTasks,
    List<AnalyticsStatItem> topItems,
    List<AnalyticsTrendPoint> trends,
    AnalyticsSummary comparison,
    List<AnalyticsRecommendation> recommendations
) {
    public AnalyticsResponse {
        topTasks = topTasks == null ? List.of() : List.copyOf(topTasks);
        topItems = topItems == null ? List.of() : List.copyOf(topItems);
        trends = trends == null ? List.of() : List.copyOf(trends);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }

    public record AnalyticsSummary(int totalEarned, int totalSpent, int netChange) { }

    public record AnalyticsStatItem(String name, int coins, int count) { }

    public record AnalyticsTrendPoint(String date, int earned, int spent) { }

    public record AnalyticsRecommendation(String name, int coins, String reason) { }
}
