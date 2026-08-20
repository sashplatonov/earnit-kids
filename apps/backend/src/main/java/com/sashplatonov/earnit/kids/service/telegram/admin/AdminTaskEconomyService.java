package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminTasksResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class AdminTaskEconomyService {

    private final AdminAnalyticsRepository repository;

    @Inject
    public AdminTaskEconomyService(AdminAnalyticsRepository repository) {
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
