package com.sashplatonov.earnit.kids.service.telegram.admin;

import com.sashplatonov.earnit.kids.dto.response.AdminTrendsResponse;
import com.sashplatonov.earnit.kids.repository.AdminAnalyticsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@ApplicationScoped
public class AdminTrendsService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Inject
    AdminAnalyticsRepository repository;

    public AdminTrendsResponse getTrends(String period) {
        Instant periodStart = calculatePeriodStart(period);
        Instant now = Instant.now();

        List<AdminTrendsResponse.TrendPoint> points = repository.getTrendPoints(periodStart, now);

        return AdminTrendsResponse.builder()
            .points(points)
            .updatedAt(ISO_FORMATTER.format(now.atOffset(ZoneOffset.UTC)))
            .build();
    }

    private Instant calculatePeriodStart(String period) {
        return switch (period) {
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            case "90d" -> Instant.now().minus(90, ChronoUnit.DAYS);
            default -> Instant.EPOCH;
        };
    }
}
