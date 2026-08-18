package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminRetentionResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
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
    AdminAnalyticsRepository repository;

    public AdminRetentionResponse getRetention(String period) {
        Instant periodStart = calculatePeriodStart(period);
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

    private Instant calculatePeriodStart(String period) {
        return switch (period) {
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> Instant.now().minus(30, ChronoUnit.DAYS);
        };
    }
}
