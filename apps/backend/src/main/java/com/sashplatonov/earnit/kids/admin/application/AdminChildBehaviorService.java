package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminChildBehaviorResponse;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminChildBehaviorRepository;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class AdminChildBehaviorService {

    @Inject
    AdminChildBehaviorRepository repository;

    @CacheResult(cacheName = "admin-child-behavior")
    public AdminChildBehaviorResponse getChildBehavior(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        AdminChildBehaviorResponse.ChildBehaviorMetrics metrics = repository.getChildBehaviorMetrics(periodStart);
        return AdminChildBehaviorResponse.builder()
            .childBehaviorMetrics(metrics)
            .build();
    }

}
