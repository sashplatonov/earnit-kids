package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class AdminChildBehaviorService {

    @Inject
    AdminAnalyticsRepository repository;

    public AdminChildBehaviorResponse getChildBehavior(String period) {
        Instant periodStart = parsePeriod(period);
        AdminChildBehaviorResponse.ChildBehaviorMetrics metrics = repository.getChildBehaviorMetrics(periodStart);
        return AdminChildBehaviorResponse.builder()
            .childBehaviorMetrics(metrics)
            .build();
    }

    private Instant parsePeriod(String period) {
        if (period == null || period.isEmpty()) {
            return Instant.now().minus(30, ChronoUnit.DAYS);
        }
        return switch (period) {
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };
    }
}
