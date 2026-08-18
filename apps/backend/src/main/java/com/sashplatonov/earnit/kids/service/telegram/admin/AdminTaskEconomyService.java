package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class AdminTaskEconomyService {

    private final AdminAnalyticsRepository repository;

    @Inject
    public AdminTaskEconomyService(AdminAnalyticsRepository repository) {
        this.repository = repository;
    }

    public AdminTasksResponse getTaskEconomy(String period) {
        Instant periodStart = parsePeriod(period);
        AdminTasksResponse.TaskMetrics metrics = repository.getTaskMetrics(periodStart);
        return AdminTasksResponse.builder()
            .metrics(metrics)
            .topPatterns(repository.calcTopTaskPatterns(periodStart))
            .updatedAt(java.time.Instant.now().toString())
            .build();
    }

    private Instant parsePeriod(String period) {
        if (period == null) period = "30d";
        return switch (period) {
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };
    }
}
