package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AdminAnalyticsService {

    private final AdminAnalyticsRepository adminAnalyticsRepository;
    private final TimeProvider timeProvider;

    @CacheResult(cacheName = "admin-analytics-overview")
    public AdminAnalyticsResponse getOverview(String period) {
        Instant periodStart = calculatePeriodStart(period);
        AdminAnalyticsResponse.Overview overview = adminAnalyticsRepository.getOverview(periodStart);

        return AdminAnalyticsResponse.builder()
            .overview(overview)
            .updatedAt(timeProvider.now().toString())
            .build();
    }

    private Instant calculatePeriodStart(String period) {
        Instant now = timeProvider.now();
        return switch (period) {
            case "7d" -> now.minus(7, ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            case "90d" -> now.minus(90, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };
    }
}
