package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class AdminParentBehaviorService {

    @Inject
    AdminAnalyticsRepository repository;

    public AdminParentBehaviorResponse getParentBehavior(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        AdminParentBehaviorResponse.ParentBehaviorMetrics metrics = repository.getParentBehaviorMetrics(periodStart);
        return AdminParentBehaviorResponse.builder()
            .parentBehaviorMetrics(metrics)
            .build();
    }

}
