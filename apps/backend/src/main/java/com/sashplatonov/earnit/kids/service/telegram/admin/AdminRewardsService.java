package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminRewardsResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class AdminRewardsService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminAnalyticsRepository repository;

    public AdminRewardsResponse getRewardsAnalytics(String period) {
        Instant periodStart = calculatePeriodStart(period);
        Instant now = Instant.now();

        int requestCount = repository.countAllRewardRequests(periodStart);
        int issuedCount = repository.countSuccessfulRewardPurchases(periodStart);
        double medianPrice = repository.calcMedianRewardPrice(periodStart);
        double selectedPrice = repository.calcMedianPriceOfIssuedRewards(periodStart);
        double failedRate = calcFailedRate(requestCount, issuedCount);

        AdminRewardsResponse.RewardMetrics metrics = AdminRewardsResponse.RewardMetrics.builder()
            .requestCount(requestCount)
            .issuedCount(issuedCount)
            .medianPrice(medianPrice)
            .selectedPrice(selectedPrice)
            .failedRate(failedRate)
            .build();

        List<AdminRewardsResponse.RewardRanking> rankings = repository.calcRewardRankings(periodStart);

        return AdminRewardsResponse.builder()
            .metrics(metrics)
            .rankings(rankings)
            .updatedAt(ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)))
            .build();
    }

    private double calcFailedRate(int total, int successful) {
        if (total == 0) return 0.0;
        return Math.round(100.0 * (total - successful) / total);
    }

    private Instant calculatePeriodStart(String period) {
        return switch (period) {
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };
    }
}
