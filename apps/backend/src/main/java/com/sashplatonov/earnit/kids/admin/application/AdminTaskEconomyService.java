package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminTaskAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class AdminTaskEconomyService {

    private final AdminTaskAnalyticsRepository repository;

    @Inject
    public AdminTaskEconomyService(AdminTaskAnalyticsRepository repository) {
        this.repository = repository;
    }

    public AdminTasksResponse getTaskEconomy(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        AdminTasksResponse.TaskMetrics metrics = repository.getTaskMetrics(periodStart);
        return AdminTasksResponse.builder()
            .metrics(metrics)
            .topPatterns(repository.calcTopTaskPatterns(periodStart))
            .updatedAt(java.time.Instant.now().toString())
            .build();
    }

}
