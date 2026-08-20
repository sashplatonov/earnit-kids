package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminAnalyticsResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import com.sashplatonov.earnit.kids.util.TimeProvider;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AdminAnalyticsService {

    private final AdminAnalyticsRepository adminAnalyticsRepository;
    private final TimeProvider timeProvider;

    @CacheResult(cacheName = "admin-analytics-overview")
    public AdminAnalyticsResponse getOverview(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        AdminAnalyticsResponse.Overview overview = adminAnalyticsRepository.getOverview(periodStart);

        return AdminAnalyticsResponse.builder()
            .overview(overview)
            .updatedAt(timeProvider.now().toString())
            .build();
    }

}
