package com.sashplatonov.earnit.kids.service.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class AdminParentBehaviorService {

    @Inject
    AdminAnalyticsRepository repository;

    public AdminParentBehaviorResponse getParentBehavior(String period) {
        Instant periodStart = parsePeriod(period);
        AdminParentBehaviorResponse.ParentBehaviorMetrics metrics = repository.getParentBehaviorMetrics(periodStart);
        return AdminParentBehaviorResponse.builder()
            .parentBehaviorMetrics(metrics)
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
