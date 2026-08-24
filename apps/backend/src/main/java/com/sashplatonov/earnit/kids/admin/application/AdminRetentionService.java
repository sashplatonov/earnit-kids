package com.sashplatonov.earnit.kids.admin.application;

import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.admin.infrastructure.persistence.AdminRetentionRepository;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class AdminRetentionService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminRetentionRepository repository;

    @CacheResult(cacheName = "admin-retention")
    public AdminRetentionResponse getRetention(AdminAnalyticsPeriod period) {
        Instant periodStart = period.start();
        Instant now = Instant.now();

        AdminRetentionResponse.RetentionMetrics metrics = AdminRetentionResponse.RetentionMetrics.builder()
            .newFamilies(repository.countNewFamilies(periodStart))
            .returningFamilies(repository.countReturningFamilies(periodStart))
            .activeFamilies(repository.countActiveFamilies(periodStart))
            .active7d(repository.countActiveFamilies(now.minus(7, ChronoUnit.DAYS)))
            .active30d(repository.countActiveFamilies(now.minus(30, ChronoUnit.DAYS)))
            .build();

        return AdminRetentionResponse.builder()
            .retentionMetrics(metrics)
            .updatedAt(ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)))
            .build();
    }

}
