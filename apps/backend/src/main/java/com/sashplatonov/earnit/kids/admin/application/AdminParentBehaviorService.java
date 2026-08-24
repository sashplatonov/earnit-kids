package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminParentBehaviorResponse;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminParentBehaviorRepository;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class AdminParentBehaviorService {

    @Inject
    AdminParentBehaviorRepository repository;

    @CacheResult(cacheName = "admin-parent-behavior")
    public AdminParentBehaviorResponse getParentBehavior(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        AdminParentBehaviorResponse.ParentBehaviorMetrics metrics = repository.getParentBehaviorMetrics(periodStart);
        return AdminParentBehaviorResponse.builder()
            .parentBehaviorMetrics(metrics)
            .build();
    }

}
