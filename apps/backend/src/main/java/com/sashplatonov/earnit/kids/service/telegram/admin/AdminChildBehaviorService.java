package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class AdminChildBehaviorService {

    @Inject
    AdminAnalyticsRepository repository;

    public AdminChildBehaviorResponse getChildBehavior(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        AdminChildBehaviorResponse.ChildBehaviorMetrics metrics = repository.getChildBehaviorMetrics(periodStart);
        return AdminChildBehaviorResponse.builder()
            .childBehaviorMetrics(metrics)
            .build();
    }

}
